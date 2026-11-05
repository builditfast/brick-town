package ai.freeapp.bricktown.importing

import ai.freeapp.bricktown.ViewModelTest
import ai.freeapp.bricktown.domain.Expense
import ai.freeapp.bricktown.core.flags.AppFlags
import ai.freeapp.bricktown.core.flags.Flag
import ai.freeapp.bricktown.domain.ImportFailure
import ai.freeapp.bricktown.domain.StatementParse
import ai.freeapp.bricktown.domain.StatementRow
import ai.freeapp.bricktown.settings.FakeTownGateway
import ai.freeapp.bricktown.settings.flagsWith
import ai.freeapp.bricktown.settings.fixtureNow
import ai.freeapp.bricktown.settings.tower
import ai.freeapp.bricktown.settings.townSnapshot
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImportViewModelTest : ViewModelTest() {
    private val csv = """
        Date,Description,Amount
        2026-11-20,Lidl,-45.30
        2026-11-21,Netflix,-12.99
        2026-11-22,Mystery Shop,-9.00
        2026-11-23,Dup Cafe,-5.00
    """.trimIndent()

    private val existing = Expense(
        id = "old", categoryId = "eat", amountCents = 500, date = LocalDate(2026, 11, 23), note = "Dup Cafe",
        paidBy = "you", createdAt = fixtureNow,
    )

    private fun gateway() = FakeTownGateway(
        townSnapshot(
            categories = listOf(tower("groc", "Groceries", 0), tower("subs", "Subscriptions", 1), tower("eat", "Eating out", 2)),
            expenses = listOf(existing),
        ),
    )

    /** What the fake server answers. */
    private var pdfAnswer: PdfReadResult = PdfReadResult.NoConnection
    private var pdfCalls = 0

    private fun viewModel(gateway: FakeTownGateway, flags: AppFlags = flagsWith()): ImportViewModel {
        var next = 0
        val importer = PdfStatementImporter { pdfCalls++; pdfAnswer }
        return ImportViewModel(gateway, importer, flags, dispatcher, newId = { "new${next++}" })
    }

    private val pdfFile = PickedFile("statement_nov.pdf", "%PDF-1.4 fake".encodeToByteArray())

    private fun file(text: String, name: String = "statement_nov.csv") = PickedFile(name, text.encodeToByteArray())

    @Test
    fun addCountsOnlyRowsWithATowerAndNoDuplicate() = runTest(dispatcher) {
        val viewModel = viewModel(gateway())
        viewModel.onFilePicked(file(csv))
        advanceUntilIdle()
        val state = viewModel.state.value

        assertEquals(ImportStage.Review, state.stage)
        assertEquals(listOf(RowStatus.Ready, RowStatus.Ready, RowStatus.NeedsTower, RowStatus.Duplicate), state.rows.map { it.status })
        assertEquals(2, state.addCount)
        assertEquals("Add 2 bricks", state.addLabel)
        assertEquals("Found 4 transactions · 2 ready · 1 need a category · 1 already a brick", state.summary)
        assertTrue(state.rows[1].meta.endsWith("Looks recurring · will repeat monthly"))
        assertTrue(state.rows[3].meta.endsWith("Already a brick · skipped"))
    }

    @Test
    fun choosingATowerForTheUnknownRowAddsItAndADuplicateCannotBeChosen() = runTest(dispatcher) {
        val viewModel = viewModel(gateway())
        viewModel.onFilePicked(file(csv))
        advanceUntilIdle()

        viewModel.onTowerClick(3, "groc")
        assertEquals(2, viewModel.state.value.addCount)
        viewModel.onTowerClick(2, "eat")

        assertEquals(3, viewModel.state.value.addCount)
        assertEquals("Add 3 bricks", viewModel.state.value.addLabel)
        assertEquals(RowStatus.Duplicate, viewModel.state.value.rows[3].status)
    }

    @Test
    fun addWritesTheBricksAndUndoRemovesTheWholeImport() = runTest(dispatcher) {
        val gateway = gateway()
        val viewModel = viewModel(gateway)
        viewModel.onFilePicked(file(csv))
        advanceUntilIdle()

        viewModel.onAddClick()
        advanceUntilIdle()

        assertEquals(ImportStage.Added, viewModel.state.value.stage)
        assertEquals(2, viewModel.state.value.undoable)
        val written = gateway.snapshot.expenses.filter { it.id != "old" }
        assertEquals(listOf("groc", "subs"), written.map { it.categoryId })
        assertEquals(listOf(false, true), written.map { it.recurring })
        assertEquals(listOf("Lidl", "Netflix"), written.map { it.note })
        assertEquals(listOf(4530L, 1299L), written.map { it.amountCents })

        viewModel.onUndoClick()
        advanceUntilIdle()

        assertEquals(listOf("old"), gateway.snapshot.expenses.map { it.id })
        assertEquals(ImportStage.Review, viewModel.state.value.stage)
        assertEquals(2, viewModel.state.value.addCount)
    }

    @Test
    fun theScanCardShowsForOneAndFourTenthsSeconds() = runTest(dispatcher) {
        val viewModel = viewModel(gateway())
        viewModel.onFilePicked(file(csv))
        runCurrent()
        assertEquals(ImportStage.Scanning, viewModel.state.value.stage)
        assertEquals("statement_nov.csv", viewModel.state.value.fileName)

        advanceTimeBy(SCAN_MILLIS - 100)
        runCurrent()
        assertEquals(ImportStage.Scanning, viewModel.state.value.stage)
        advanceUntilIdle()
        assertEquals(ImportStage.Review, viewModel.state.value.stage)
    }

    @Test
    fun aPdfAnEmptyFileAndASpendlessFileEachGiveTheirOwnReasonAndAddNothing() = runTest(dispatcher) {
        val gateway = gateway()
        val viewModel = viewModel(gateway)
        val cases = listOf(
            PickedFile("photo.jpg", ByteArray(40) { 0 }) to "doesn't look like a bank statement.",
            file("Date,Description\nhello,world") to "has no dates and amounts I could find.",
            file("Date,Description,Amount\n2026-11-02,Salary,2500.00\n2026-11-03,Refund,12.00") to "has no spending in it, only money coming in.",
        )
        for ((picked, reason) in cases) {
            viewModel.onFilePicked(picked)
            advanceUntilIdle()
            assertEquals(ImportStage.Failed, viewModel.state.value.stage)
            assertEquals(reason, viewModel.state.value.failure)
            viewModel.onTryAgainClick()
            assertEquals(ImportStage.Pick, viewModel.state.value.stage)
        }
        assertTrue(gateway.calls.none { it.startsWith("putExpense") })
    }

    private fun failed(reason: ImportFailure) = PdfReadResult.Read(StatementParse.Failed(reason))

    private suspend fun kotlinx.coroutines.test.TestScope.failureOf(viewModel: ImportViewModel, picked: PickedFile): String {
        viewModel.onFilePicked(picked)
        advanceUntilIdle()
        assertEquals(ImportStage.Failed, viewModel.state.value.stage)
        return viewModel.state.value.failure
    }

    @Test
    fun thePickCardOffersAPdfOnlyWhileTheFlagIsOn() = runTest(dispatcher) {
        val on = viewModel(gateway())
        advanceUntilIdle()
        assertTrue(on.state.value.pdfOn)
        val off = viewModel(gateway(), flagsWith(Flag.IMPORT_PDF to false))
        advanceUntilIdle()
        assertEquals(false, off.state.value.pdfOn)
    }

    @Test
    fun aPdfWithTheFlagOffKeepsTheNotSupportedCopyAndNeverCallsTheServer() = runTest(dispatcher) {
        val viewModel = viewModel(gateway(), flagsWith(Flag.IMPORT_PDF to false))
        assertEquals("is a PDF. I can read CSV files for now; PDF statements are coming soon.", failureOf(viewModel, pdfFile))
        assertEquals(0, pdfCalls)
    }

    @Test
    fun aServerThatRefusesOrLacksTheRouteKeepsTheNotSupportedCopy() = runTest(dispatcher) {
        pdfAnswer = PdfReadResult.NotAvailable
        val viewModel = viewModel(gateway())
        assertEquals("is a PDF. I can read CSV files for now; PDF statements are coming soon.", failureOf(viewModel, pdfFile))
        assertEquals(1, pdfCalls)
    }

    @Test
    fun aPdfWithoutAConnectionSaysSoAndCsvStillWorks() = runTest(dispatcher) {
        pdfAnswer = PdfReadResult.NoConnection
        val viewModel = viewModel(gateway())
        assertEquals("is a PDF, and I need a connection to read one. CSV files work offline.", failureOf(viewModel, pdfFile))
        viewModel.onTryAgainClick()
        viewModel.onFilePicked(file(csv))
        advanceUntilIdle()
        assertEquals(ImportStage.Review, viewModel.state.value.stage)
        assertEquals(1, pdfCalls)
    }

    @Test
    fun rowsFromTheServerGoIntoTheSameReviewAsCsvRows() = runTest(dispatcher) {
        pdfAnswer = PdfReadResult.Read(
            StatementParse.Rows(
                listOf(
                    StatementRow(LocalDate(2026, 11, 20), "Lidl", 4_530),
                    StatementRow(LocalDate(2026, 11, 22), "Mystery Shop", 900),
                    StatementRow(LocalDate(2026, 11, 23), "Dup Cafe", 500),
                ),
            ),
        )
        val viewModel = viewModel(gateway())
        viewModel.onFilePicked(pdfFile)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(ImportStage.Review, state.stage)
        assertEquals(listOf(RowStatus.Ready, RowStatus.NeedsTower, RowStatus.Duplicate), state.rows.map { it.status })
        assertEquals("Found 3 transactions · 1 ready · 1 need a category · 1 already a brick", state.summary)
    }

    @Test
    fun eachNewPdfFailureHasItsSentenceAndScannedOrLockedUseTheFramesWords() = runTest(dispatcher) {
        val expected = mapOf(
            ImportFailure.SCANNED_PDF to "looks like a scanned image or is password-protected. I can read text PDFs and CSV exports.",
            ImportFailure.NO_TABLE to "has no table of payments I could find. Try the CSV export from your bank.",
            ImportFailure.PDF_LOCKED to "looks like a scanned image or is password-protected. I can read text PDFs and CSV exports.",
        )
        val gateway = gateway()
        val viewModel = viewModel(gateway)
        for ((reason, sentence) in expected) {
            pdfAnswer = failed(reason)
            assertEquals(sentence, failureOf(viewModel, pdfFile))
            viewModel.onTryAgainClick()
        }
        assertTrue(gateway.calls.none { it.startsWith("putExpense") })
    }

    @Test
    fun cancellingThePickerChangesNothingAndTheNextFileStillWorks() = runTest(dispatcher) {
        val viewModel = viewModel(gateway())
        viewModel.onFilePicked(null)
        advanceUntilIdle()
        assertEquals(ImportStage.Pick, viewModel.state.value.stage)
        assertNull(viewModel.state.value.message)

        viewModel.onFilePicked(file(csv))
        advanceUntilIdle()
        assertEquals(ImportStage.Review, viewModel.state.value.stage)
    }

    @Test
    fun addIsDeadWhenNothingIsReady() = runTest(dispatcher) {
        val gateway = gateway()
        val viewModel = viewModel(gateway)
        viewModel.onFilePicked(file("Date,Description,Amount\n2026-11-22,Mystery Shop,-9.00"))
        advanceUntilIdle()

        assertEquals(0, viewModel.state.value.addCount)
        viewModel.onAddClick()
        advanceUntilIdle()

        assertFalse(gateway.calls.any { it.startsWith("putExpense") })
        assertEquals(ImportStage.Review, viewModel.state.value.stage)
    }

    @Test
    fun photoImportSaysComingSoon() = runTest(dispatcher) {
        val viewModel = viewModel(gateway())
        viewModel.onPhotoClick()
        assertEquals("Photo import is coming soon", viewModel.state.value.message)
    }
}
