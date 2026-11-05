package ai.freeapp.bricktown.importing

import ai.freeapp.bricktown.core.flags.AppFlags
import ai.freeapp.bricktown.core.flags.Flag
import ai.freeapp.bricktown.core.ui.BaseViewModel
import ai.freeapp.bricktown.core.ui.toMessage
import ai.freeapp.bricktown.domain.Expense
import ai.freeapp.bricktown.domain.ImportCandidate
import ai.freeapp.bricktown.domain.ImportFailure
import ai.freeapp.bricktown.domain.StatementParse
import ai.freeapp.bricktown.domain.TownSnapshot
import ai.freeapp.bricktown.domain.formatExact
import ai.freeapp.bricktown.domain.looksLikePdf
import ai.freeapp.bricktown.domain.parseCsvStatement
import ai.freeapp.bricktown.domain.reviewImport
import ai.freeapp.bricktown.settings.TownGateway
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/** How long the scanning card shows, so a fast parse does not flash. */
const val SCAN_MILLIS = 1400L

enum class ImportStage { Pick, Scanning, Review, Added, Failed }

enum class RowStatus { Ready, NeedsTower, Duplicate }

@Immutable
data class TowerChoice(val id: String, val name: String, val colorIndex: Int)

@Immutable
data class ImportRowUi(
    val key: Int,
    val merchant: String,
    val meta: String,
    val amount: String,
    val status: RowStatus,
    val towerId: String?,
    val recurring: Boolean,
    val open: Boolean,
)

@Immutable
data class ImportUiState(
    val stage: ImportStage = ImportStage.Pick,
    /** `import.pdf`: the pick card only offers a PDF, and only says nothing leaves the phone, when that is true. */
    val pdfOn: Boolean = true,
    val fileName: String = "",
    val towers: List<TowerChoice> = emptyList(),
    val rows: List<ImportRowUi> = emptyList(),
    val summary: String = "",
    val addCount: Int = 0,
    val addLabel: String = "Add bricks",
    val failure: String = "",
    val undoable: Int = 0,
    val message: String? = null,
)

/**
 * The reason on the 6d failure card. The card prints the file's name in bold and this right after it,
 * as the frame does ("statement_nov.pdf looks like a scanned image or is password-protected...").
 * Scanned and locked PDFs use the frame's words; every other reason is (ours) in the same shape.
 */
fun failureText(reason: ImportFailure): String = when (reason) {
    ImportFailure.PDF_NOT_SUPPORTED -> "is a PDF. I can read CSV files for now; PDF statements are coming soon."
    ImportFailure.NOT_A_STATEMENT -> "doesn't look like a bank statement."
    ImportFailure.NO_DATES_OR_AMOUNTS -> "has no dates and amounts I could find."
    ImportFailure.NO_SPENDING -> "has no spending in it, only money coming in."
    ImportFailure.SCANNED_PDF, ImportFailure.PDF_LOCKED ->
        "looks like a scanned image or is password-protected. I can read text PDFs and CSV exports."
    ImportFailure.NO_TABLE -> "has no table of payments I could find. Try the CSV export from your bank."
}

/** (ours) A PDF is read on the server, so it cannot be read offline. Follows the file's name like [failureText]. */
const val PDF_NEEDS_CONNECTION = "is a PDF, and I need a connection to read one. CSV files work offline."

/**
 * Import a statement (5b, 6d): read a CSV on the device, or a PDF on the server when
 * `import.pdf` is on, review the rows, add the ones that have a tower.
 */
@OptIn(ExperimentalUuidApi::class)
class ImportViewModel(
    private val gateway: TownGateway,
    private val pdf: PdfStatementImporter,
    private val flags: AppFlags,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val newId: () -> String = { Uuid.random().toString() },
) : BaseViewModel<ImportUiState, Nothing>(ImportUiState(), dispatcher) {

    private var snapshot: TownSnapshot? = null
    private var candidates: List<ImportCandidate> = emptyList()
    private var ids: List<String> = emptyList()
    private var chosen: Map<Int, String> = emptyMap()
    private var openKey: Int? = null
    private var added: List<String> = emptyList()

    init {
        tryToCollect(
            flow = { flags.on(Flag.IMPORT_PDF) },
            onEach = { on -> updateState { it.copy(pdfOn = on) } },
            // The server still gates PDF reading; the PDF copy ("CSVs stay on your phone") is true either way.
            onError = { updateState { it.copy(pdfOn = true) } },
        )
    }

    /** Null means the person cancelled the picker: nothing changes. */
    fun onFilePicked(file: PickedFile?) {
        if (file == null || state.value.stage == ImportStage.Scanning) return
        updateState { it.copy(stage = ImportStage.Scanning, fileName = file.name, message = null) }
        tryToExecute(
            call = {
                val town = gateway.town.first() ?: error("The town is not loaded yet")
                delay(SCAN_MILLIS)
                town to parse(file)
            },
            onSuccess = { (town, parsed) ->
                when (parsed) {
                    is Parsed.Offline -> updateState { it.copy(stage = ImportStage.Failed, failure = PDF_NEEDS_CONNECTION) }
                    is Parsed.Statement -> when (val result = parsed.parse) {
                        is StatementParse.Failed -> updateState { it.copy(stage = ImportStage.Failed, failure = failureText(result.reason)) }
                        is StatementParse.Rows -> startReview(town, result)
                    }
                }
            },
            onError = { error -> updateState { it.copy(stage = ImportStage.Pick, message = error.toMessage()) } },
        )
    }

    private sealed interface Parsed {
        data class Statement(val parse: StatementParse) : Parsed
        data object Offline : Parsed
    }

    private suspend fun parse(file: PickedFile): Parsed = when {
        file.bytes.size > MAX_PICKED_BYTES -> Parsed.Statement(StatementParse.Failed(ImportFailure.NOT_A_STATEMENT))
        looksLikePdf(file.bytes) -> readPdf(file.bytes)
        else -> Parsed.Statement(parseCsvStatement(file.bytes.decodeToString()))
    }

    /** Flag off, or a server that refuses or lacks the route, keeps the 6d "PDF not supported" copy. */
    private suspend fun readPdf(bytes: ByteArray): Parsed {
        val notSupported = Parsed.Statement(StatementParse.Failed(ImportFailure.PDF_NOT_SUPPORTED))
        if (!flags.isOn(Flag.IMPORT_PDF)) return notSupported
        return when (val result = pdf.read(bytes)) {
            is PdfReadResult.Read -> Parsed.Statement(result.parse)
            PdfReadResult.NoConnection -> Parsed.Offline
            PdfReadResult.NotAvailable -> notSupported
        }
    }

    private fun startReview(town: TownSnapshot, parsed: StatementParse.Rows) {
        snapshot = town
        candidates = reviewImport(parsed.rows, town)
        ids = candidates.map { newId() }
        chosen = emptyMap()
        openKey = null
        added = emptyList()
        render(ImportStage.Review)
    }

    private fun towerOf(key: Int): String? {
        val candidate = candidates[key]
        if (candidate.duplicateOf != null) return null
        return chosen[key] ?: candidate.guessedCategoryId
    }

    private fun render(stage: ImportStage) {
        val town = snapshot ?: return
        val currency = town.town.settings.currency
        val rows = candidates.mapIndexed { key, candidate ->
            val towerId = towerOf(key)
            val status = when {
                candidate.duplicateOf != null -> RowStatus.Duplicate
                towerId == null -> RowStatus.NeedsTower
                else -> RowStatus.Ready
            }
            ImportRowUi(
                key = key,
                merchant = candidate.row.merchant.ifBlank { "Unnamed" },
                meta = dayLabel(candidate.row.date) + when {
                    status == RowStatus.Duplicate -> " · Already a brick · skipped"
                    candidate.looksRecurring -> " · Looks recurring · will repeat monthly"
                    else -> ""
                },
                amount = formatExact(candidate.row.amountCents, currency),
                status = status,
                towerId = towerId,
                recurring = candidate.looksRecurring,
                open = openKey == key,
            )
        }
        val ready = rows.count { it.status == RowStatus.Ready }
        val needs = rows.count { it.status == RowStatus.NeedsTower }
        val duplicates = rows.count { it.status == RowStatus.Duplicate }
        val summary = buildString {
            append("Found ${rows.size} ${if (rows.size == 1) "transaction" else "transactions"} · $ready ready")
            if (needs > 0) append(" · $needs need a category")
            if (duplicates > 0) append(" · $duplicates already ${if (duplicates == 1) "a brick" else "bricks"}")
        }
        updateState {
            it.copy(
                stage = stage,
                towers = town.categories.sortedBy { c -> c.position }.map { c -> TowerChoice(c.id, c.name, c.colorIndex) },
                rows = rows,
                summary = summary,
                addCount = ready,
                addLabel = if (ready == 0) "Add bricks" else if (ready == 1) "Add 1 brick" else "Add $ready bricks",
                undoable = if (stage == ImportStage.Added) added.size else 0,
            )
        }
    }

    private fun dayLabel(day: LocalDate) = "${day.dayOfMonth} ${MONTHS[day.month.number - 1]}"

    /** Opens or closes the tower chips of a row. A skipped duplicate stays shut. */
    fun onRowClick(key: Int) {
        if (state.value.stage != ImportStage.Review || candidates.getOrNull(key)?.duplicateOf != null) return
        openKey = if (openKey == key) null else key
        render(ImportStage.Review)
    }

    fun onTowerClick(key: Int, towerId: String) {
        if (state.value.stage != ImportStage.Review || candidates.getOrNull(key)?.duplicateOf != null) return
        chosen = chosen + (key to towerId)
        openKey = null
        render(ImportStage.Review)
    }

    fun onAddClick() {
        val town = snapshot ?: return
        if (state.value.stage != ImportStage.Review || state.value.addCount == 0) return
        val bricks = candidates.mapIndexedNotNull { key, candidate ->
            val towerId = towerOf(key) ?: return@mapIndexedNotNull null
            Expense(
                id = ids[key],
                categoryId = towerId,
                amountCents = candidate.row.amountCents,
                date = candidate.row.date,
                note = candidate.row.merchant,
                recurring = candidate.looksRecurring,
                paidBy = town.viewerId,
                createdAt = town.serverTime,
            )
        }
        tryToExecute(
            call = { for (brick in bricks) if (brick.id !in added) { gateway.putExpense(brick); added = added + brick.id } },
            onSuccess = { render(ImportStage.Added) },
            onError = { error -> updateState { it.copy(message = error.toMessage()) } },
        )
    }

    /** Removes the whole import, then shows the review again with the choices made. */
    fun onUndoClick() {
        if (state.value.stage != ImportStage.Added) return
        val toRemove = added
        tryToExecute(
            call = { for (id in toRemove.reversed()) { gateway.deleteExpense(id); added = added - id } },
            onSuccess = {
                ids = candidates.map { newId() }
                render(ImportStage.Review)
            },
            onError = { error -> updateState { it.copy(message = error.toMessage()) } },
        )
    }

    /** Back to the first card, for another file. */
    fun onTryAgainClick() {
        candidates = emptyList()
        updateState { ImportUiState() }
    }

    /** The undo toast timed out: the import stays. */
    fun onUndoExpired() = updateState { it.copy(undoable = 0) }

    fun onPhotoClick() = updateState { it.copy(message = "Photo import is coming soon") }

    fun onMessageShown() = updateState { it.copy(message = null) }
}
