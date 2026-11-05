package ai.freeapp.bricktown.towers

import ai.freeapp.bricktown.core.error.ErrorState
import ai.freeapp.bricktown.core.time.TownClock
import ai.freeapp.bricktown.core.ui.BaseViewModel
import ai.freeapp.bricktown.core.ui.toMessage
import ai.freeapp.bricktown.domain.CAP_CHOICES_CENTS
import ai.freeapp.bricktown.domain.Category
import ai.freeapp.bricktown.domain.Currency
import ai.freeapp.bricktown.domain.MAX_TOWER_NAME
import ai.freeapp.bricktown.domain.MemberRole
import ai.freeapp.bricktown.domain.TowerProblem
import ai.freeapp.bricktown.domain.TownSnapshot
import ai.freeapp.bricktown.domain.bricksFor
import ai.freeapp.bricktown.domain.checkTowerDelete
import ai.freeapp.bricktown.domain.checkTowerName
import ai.freeapp.bricktown.domain.formatWhole
import ai.freeapp.bricktown.domain.nextColorIndex
import ai.freeapp.bricktown.domain.payMonthOf
import ai.freeapp.bricktown.domain.shortNameFor
import ai.freeapp.bricktown.domain.signedCents
import ai.freeapp.bricktown.settings.TownGateway
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** The words a tower rule shows the user. */
fun TowerProblem.toCopy(): String = when (this) {
    TowerProblem.NameRequired -> "Give the tower a name"
    TowerProblem.NameTooLong -> "Keep it to $MAX_TOWER_NAME characters"
    TowerProblem.NameTaken -> "You already have a tower with that name"
    TowerProblem.LastTower -> "You need at least one tower"
    TowerProblem.MergeIntoItself -> "Pick a different tower to merge into"
}

@Immutable
data class TowerRow(
    val id: String,
    val name: String,
    val colorIndex: Int,
    val bricks: Int,
    val capCents: Long?,
    val bills: Int,
    val meta: String,
)

/** The inline editor under a row. [id] is null for "+ New tower". */
@Immutable
data class TowerEditor(
    val id: String?,
    val name: String,
    val colorIndex: Int,
    val capCents: Long?,
    /** The Custom chip is on: [customDraft] is typed in whole currency units. */
    val customCap: Boolean = false,
    val customDraft: String = "",
    val error: String? = null,
)

/** Merge or delete of a tower with bricks: where do they go. */
@Immutable
data class TowerMovePrompt(
    val fromId: String,
    val fromName: String,
    val bricks: Int,
    val deleting: Boolean,
    val targets: List<TowerRow>,
)

@Immutable
data class TowersUiState(
    val loaded: Boolean = false,
    val canManage: Boolean = false,
    val rows: List<TowerRow> = emptyList(),
    val editor: TowerEditor? = null,
    val prompt: TowerMovePrompt? = null,
    val message: String? = null,
)

@OptIn(ExperimentalUuidApi::class)
class TowersViewModel(
    private val gateway: TownGateway,
    private val clock: TownClock,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val newId: () -> String = { Uuid.random().toString() },
) : BaseViewModel<TowersUiState, Nothing>(TowersUiState(), dispatcher) {

    private var snapshot: TownSnapshot? = null

    /** The order shown while a drag is in flight, before it is written. */
    private var dragOrder: List<TowerRow>? = null

    init {
        tryToCollect(flow = { gateway.town }, onEach = ::render, onError = ::fail)
    }

    private fun render(town: TownSnapshot?) {
        snapshot = town
        dragOrder = null
        if (town == null) return
        val viewer = town.members.firstOrNull { it.userId == town.viewerId }
        val month = payMonthOf(clock.today(town.town.settings), town.town.settings.payday)
        val currency = town.town.settings.currency
        val inMonth = town.expenses.filter { it.date in month }
        val rows = town.categories.sortedBy { it.position }.map { category ->
            val own = inMonth.filter { it.categoryId == category.id }
            rowOf(category, bricksFor(own.sumOf { it.signedCents }), own.count { it.recurring }, currency)
        }
        updateState {
            it.copy(loaded = true, canManage = viewer?.role == MemberRole.OWNER || viewer?.canManage == true, rows = rows)
        }
    }

    private fun rowOf(category: Category, bricks: Int, bills: Int, currency: Currency): TowerRow {
        val parts = mutableListOf(if (bricks == 1) "1 brick" else "$bricks bricks")
        val cap = category.capCents
        if (cap != null) parts += "cap ${formatWhole(cap, currency)}"
        if (bills > 0) parts += if (bills == 1) "1 monthly bill" else "$bills monthly bills"
        if (cap == null && bills == 0) parts += "no cap"
        return TowerRow(category.id, category.name, category.colorIndex, bricks, cap, bills, parts.joinToString(" · "))
    }

    fun onTowerClick(id: String) {
        if (!state.value.canManage) return
        val category = snapshot?.categories?.firstOrNull { it.id == id } ?: return
        val cap = category.capCents
        val custom = cap != null && cap !in CAP_CHOICES_CENTS
        updateState {
            it.copy(editor = TowerEditor(id, category.name, category.colorIndex, cap, custom, if (custom) draftOf(cap!!) else ""))
        }
    }

    fun onNewTowerClick() {
        val town = snapshot ?: return
        if (!state.value.canManage) return
        updateState { it.copy(editor = TowerEditor(null, "", nextColorIndex(town.categories), null)) }
    }

    fun onNameChange(name: String) = editEditor { it.copy(name = name.take(MAX_TOWER_NAME + 1), error = null) }

    fun onColorPick(index: Int) = editEditor { it.copy(colorIndex = index) }

    fun onCapPick(capCents: Long?) = editEditor { it.copy(capCents = capCents, customCap = false, customDraft = "", error = null) }

    fun onCustomCapClick() = editEditor { it.copy(customCap = true, capCents = it.customDraft.toWholeCents()) }

    fun onCustomCapChange(text: String) {
        val clean = text.filter { it.isDigit() }.take(6)
        editEditor { it.copy(customCap = true, customDraft = clean, capCents = clean.toWholeCents()) }
    }

    fun onEditorCancel() = updateState { it.copy(editor = null) }

    fun onEditorSave() {
        val town = snapshot ?: return
        val editor = state.value.editor ?: return
        if (!state.value.canManage) return
        val problem = checkTowerName(editor.name, town.categories, editor.id)
        if (problem != null) {
            editEditor { it.copy(error = problem.toCopy()) }
            return
        }
        val name = editor.name.trim()
        val existing = town.categories.firstOrNull { it.id == editor.id }
        val category = existing?.copy(
            name = name,
            short = if (existing.name == name) existing.short else shortNameFor(name),
            colorIndex = editor.colorIndex,
            capCents = editor.capCents,
        ) ?: Category(
            id = newId(),
            name = name,
            short = shortNameFor(name),
            colorIndex = editor.colorIndex,
            capCents = editor.capCents,
            position = (town.categories.maxOfOrNull { it.position } ?: -1) + 1,
        )
        updateState { it.copy(editor = null) }
        launchWrite { gateway.putCategory(category) }
    }

    /** Moves a row while a handle is dragged. Nothing is written until [onMoveEnd]. */
    fun onMove(from: Int, to: Int) {
        if (!state.value.canManage) return
        val rows = (dragOrder ?: state.value.rows).toMutableList()
        if (from !in rows.indices || to !in rows.indices || from == to) return
        rows.add(to, rows.removeAt(from))
        dragOrder = rows
        updateState { it.copy(rows = rows) }
    }

    fun onMoveEnd() {
        val order = dragOrder ?: return
        dragOrder = null
        launchWrite { gateway.reorderCategories(order.map { it.id }) }
    }

    fun onMergeClick(id: String) = openPrompt(id, deleting = false)

    fun onDeleteClick(id: String) {
        val row = state.value.rows.firstOrNull { it.id == id } ?: return
        val town = snapshot ?: return
        if (!state.value.canManage) return
        checkTowerDelete(id, null, town.categories)?.let { return showMessage(it.toCopy()) }
        // A tower with no bricks has nothing to move, so it goes at once.
        if (row.bricks == 0 && town.expenses.none { it.categoryId == id }) {
            launchWrite { gateway.deleteCategory(id, null) }
        } else {
            openPrompt(id, deleting = true)
        }
    }

    fun onMoveTargetPick(targetId: String) {
        val prompt = state.value.prompt ?: return
        val town = snapshot ?: return
        checkTowerDelete(prompt.fromId, targetId, town.categories)?.let { return showMessage(it.toCopy()) }
        updateState { it.copy(prompt = null, editor = null) }
        launchWrite { gateway.deleteCategory(prompt.fromId, targetId) }
    }

    fun onPromptDismiss() = updateState { it.copy(prompt = null) }

    fun onMessageShown() = updateState { it.copy(message = null) }

    private fun openPrompt(id: String, deleting: Boolean) {
        val town = snapshot ?: return
        val rows = state.value.rows
        val row = rows.firstOrNull { it.id == id } ?: return
        if (!state.value.canManage) return
        if (town.categories.size <= 1) return showMessage(TowerProblem.LastTower.toCopy())
        updateState { it.copy(prompt = TowerMovePrompt(id, row.name, row.bricks, deleting, rows.filter { r -> r.id != id })) }
    }

    private fun editEditor(change: (TowerEditor) -> TowerEditor) = updateState { s -> s.copy(editor = s.editor?.let(change)) }

    private fun showMessage(text: String) = updateState { it.copy(message = text) }

    private fun launchWrite(write: suspend () -> Unit) = tryToExecute(write, onSuccess = ::written, onError = ::fail)

    private fun written(ignored: Unit) = Unit

    private fun fail(error: ErrorState) = showMessage(error.toMessage())

    private fun String.toWholeCents(): Long? = toLongOrNull()?.takeIf { it > 0 }?.times(100)

    private fun draftOf(cents: Long): String = (cents / 100).toString()
}
