package ai.freeapp.bricktown

import ai.freeapp.bricktown.importing.ImportActions
import ai.freeapp.bricktown.importing.ImportContent
import ai.freeapp.bricktown.importing.ImportRowUi
import ai.freeapp.bricktown.importing.ImportStage
import ai.freeapp.bricktown.importing.ImportUiState
import ai.freeapp.bricktown.importing.RowStatus
import ai.freeapp.bricktown.importing.TowerChoice
import org.junit.Test

class ImportGalleryTest : Gallery1029() {
    private val towers = listOf(
        TowerChoice("g", "Groceries", 0),
        TowerChoice("e", "Eating out", 1),
        TowerChoice("h", "Home", 2),
        TowerChoice("s", "Shopping", 3),
        TowerChoice("t", "Transport", 4),
        TowerChoice("b", "Subscriptions", 5),
    )

    private val review = ImportUiState(
        stage = ImportStage.Review,
        fileName = "statement_nov.csv",
        towers = towers,
        summary = "Found 7 transactions · 5 ready · 2 need a category",
        addCount = 5,
        addLabel = "Add 5 bricks",
        rows = listOf(
            ImportRowUi(0, "Lidl", "20 Nov", "€45.30", RowStatus.Ready, "g", false, false),
            ImportRowUi(1, "Netflix", "21 Nov · Looks recurring · will repeat monthly", "€12.99", RowStatus.Ready, "b", true, false),
            ImportRowUi(2, "Mystery Shop", "22 Nov", "€9.00", RowStatus.NeedsTower, null, false, false),
            ImportRowUi(3, "Dup Cafe", "23 Nov · Already a brick · skipped", "€5.00", RowStatus.Duplicate, null, false, false),
            ImportRowUi(4, "Uber", "24 Nov", "€14.20", RowStatus.Ready, "t", false, false),
        ),
    )

    @Test
    fun pick() = shot("import-5b-pick") { ImportContent(ImportUiState(), ImportActions()) }

    @Test
    fun scanning() = shot("import-5b-scan") { ImportContent(ImportUiState(stage = ImportStage.Scanning, fileName = "statement_nov.csv"), ImportActions()) }

    @Test
    fun reviewRows() = shot("import-5b-review") { ImportContent(review, ImportActions()) }

    @Test
    fun failed() = shot("import-6d") {
        ImportContent(
            ImportUiState(
                stage = ImportStage.Failed,
                fileName = "statement_nov.pdf",
                failure = ai.freeapp.bricktown.importing.failureText(ai.freeapp.bricktown.domain.ImportFailure.SCANNED_PDF),
            ),
            ImportActions(),
        )
    }

    @Test
    fun failedPdfFlagOff() = shot("import-6d-pdf-off") {
        ImportContent(
            ImportUiState(
                stage = ImportStage.Failed,
                fileName = "statement_nov.pdf",
                failure = ai.freeapp.bricktown.importing.failureText(ai.freeapp.bricktown.domain.ImportFailure.PDF_NOT_SUPPORTED),
            ),
            ImportActions(),
        )
    }
}
