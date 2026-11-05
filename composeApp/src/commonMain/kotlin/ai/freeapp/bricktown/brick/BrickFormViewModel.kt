package ai.freeapp.bricktown.brick

import ai.freeapp.bricktown.core.time.TownClock
import ai.freeapp.bricktown.core.ui.BaseViewModel
import ai.freeapp.bricktown.core.ui.toMessage
import ai.freeapp.bricktown.domain.Category
import ai.freeapp.bricktown.domain.Currency
import ai.freeapp.bricktown.domain.Expense
import ai.freeapp.bricktown.domain.NUMPAD_DELETE
import ai.freeapp.bricktown.domain.TowerProblem
import ai.freeapp.bricktown.domain.TownSnapshot
import ai.freeapp.bricktown.domain.bricksFor
import ai.freeapp.bricktown.domain.checkTowerName
import ai.freeapp.bricktown.domain.draftToCents
import ai.freeapp.bricktown.domain.nextColorIndex
import ai.freeapp.bricktown.domain.shortNameFor
import ai.freeapp.bricktown.domain.summarize
import ai.freeapp.bricktown.home.HomeFeedback
import ai.freeapp.bricktown.home.Undo
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** Which brick the form is for: [expenseId] set means Edit, else Add (with [categoryId] pre-selected when given). */
data class BrickFormArgs(val expenseId: String? = null, val categoryId: String? = null)

@Immutable
data class TowerChoice(val id: String, val name: String, val colorIndex: Int)

@Immutable
data class BrickFormState(
    val isEdit: Boolean = false,
    /** False until the town has loaded (and, for Edit, the brick was found). */
    val ready: Boolean = false,
    /** Edit only: the town loaded and has no brick with this id. */
    val missing: Boolean = false,
    val currency: Currency = Currency.EUR,
    val towers: List<TowerChoice> = emptyList(),
    val selectedTowerId: String? = null,
    val draft: String = "",
    val note: String = "",
    val monthly: Boolean = false,
    val refund: Boolean = false,
    val shared: Boolean = false,
    val newTowerOpen: Boolean = false,
    val newTowerName: String = "",
    val newTowerProblem: TowerProblem? = null,
    /** Bricks the selected tower has now, for the refund picture. */
    val towerBricks: Int = 0,
    val saveFailed: String? = null,
) {
    val amountCents: Long get() = draftToCents(draft)

    /** Bricks a refund removes: round(amount / 25), from the domain. */
    val refundBricks: Int get() = bricksFor(amountCents)

    val selectedTowerName: String get() = towers.firstOrNull { it.id == selectedTowerId }?.name.orEmpty()

    /** The Submit button stays off at 0 and while there is no tower to put the brick in. */
    val canSubmit: Boolean get() = ready && amountCents > 0 && selectedTowerId != null

    val title: String get() = when {
        refund -> "Refund"
        isEdit -> "Edit brick"
        else -> "New brick"
    }

    /** The hero reads "−€89" for a refund, "€12.5" while typing. */
    val heroText: String
        get() {
            val body = heroDigits
            val sign = heroSign
            return if (currency.symbolAfter) "$sign$body ${currency.symbol}" else "$sign${currency.symbol}$body"
        }

    /** The hero's parts, drawn at two sizes: "−" for a refund, then the currency symbol, then these digits. */
    val heroDigits: String get() = draft.ifEmpty { "0" }
    val heroSign: String get() = if (refund) "−" else ""

    /** "Add €12 to Eating out" for a new brick, "Save €89" (or "Save −€89" for a refund) when editing. */
    val submitLabel: String
        get() = when {
            amountCents <= 0L -> if (isEdit) "Save" else "Add a brick"
            isEdit -> "Save " + (if (refund) "\u2212" else "") + amountText(amountCents, currency)
            else -> "Add ${amountText(amountCents, currency)} to $selectedTowerName"
        }
}

sealed interface BrickFormEffect {
    /** Add finished: back to the Month tab, where the toast and the celebration wait. */
    data object ShowMonth : BrickFormEffect

    /** Edit finished or the brick is gone: back to where Edit was opened. */
    data object Close : BrickFormEffect
}

/**
 * Add brick and Edit brick share one form, so they share this ViewModel. Edit loads the brick once
 * and then the form owns the values: a sync that changes the town does not overwrite what the user typed.
 */
class BrickFormViewModel(
    private val args: BrickFormArgs,
    private val ledger: BrickLedger,
    private val clock: TownClock,
    private val feedback: HomeFeedback,
    private val newId: () -> String,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : BaseViewModel<BrickFormState, BrickFormEffect>(BrickFormState(isEdit = args.expenseId != null), dispatcher) {

    private var snapshot: TownSnapshot? = null
    private var original: Expense? = null
    private var seeded = false

    init {
        tryToCollect(
            flow = { ledger.town },
            onEach = ::onTown,
            onError = { error -> updateState { it.copy(saveFailed = error.toMessage()) } },
        )
    }

    private fun onTown(town: TownSnapshot?) {
        snapshot = town
        if (town == null) return
        val categories = town.categories.sortedBy { it.position }
        val choices = categories.map { TowerChoice(it.id, it.name, it.colorIndex) }
        if (!seeded) {
            seeded = true
            val id = args.expenseId
            if (id != null) {
                val expense = town.expenses.firstOrNull { it.id == id }
                if (expense == null) {
                    updateState { it.copy(missing = true, currency = town.town.settings.currency) }
                    return
                }
                original = expense
                updateState {
                    it.copy(
                        ready = true,
                        currency = town.town.settings.currency,
                        towers = choices,
                        selectedTowerId = expense.categoryId,
                        draft = draftFromCents(expense.amountCents),
                        note = expense.note,
                        monthly = expense.recurring,
                        refund = expense.refund,
                        shared = expense.shared,
                        towerBricks = towerBricks(town, expense.categoryId),
                    )
                }
            } else {
                val first = categories.firstOrNull { it.id == args.categoryId } ?: categories.firstOrNull()
                updateState {
                    it.copy(
                        ready = true,
                        currency = town.town.settings.currency,
                        towers = choices,
                        selectedTowerId = first?.id,
                        towerBricks = first?.let { c -> towerBricks(town, c.id) } ?: 0,
                    )
                }
            }
        } else {
            updateState { it.copy(towers = choices, towerBricks = it.selectedTowerId?.let { id -> towerBricks(town, id) } ?: 0) }
        }
    }

    private fun towerBricks(town: TownSnapshot, categoryId: String): Int =
        summarize(town, clock.today(town.town.settings)).towers.firstOrNull { it.category.id == categoryId }?.bricks ?: 0

    private fun draftFromCents(cents: Long): String {
        val whole = (cents / 100).toString()
        val rest = (cents % 100).toInt()
        return if (rest == 0) whole else "$whole.${rest.toString().padStart(2, '0')}"
    }

    /** Numpad keys: the design-system Numpad emits '\b' for delete, the domain rules call it [NUMPAD_DELETE]. */
    fun onKey(key: Char) {
        val domainKey = if (key == '\b') NUMPAD_DELETE else key
        updateState { it.copy(draft = ai.freeapp.bricktown.domain.numpadPress(it.draft, domainKey), saveFailed = null) }
    }

    fun onSelectTower(id: String) {
        val town = snapshot
        updateState { it.copy(selectedTowerId = id, towerBricks = town?.let { t -> towerBricks(t, id) } ?: 0) }
    }

    fun onNoteChange(note: String) = updateState { it.copy(note = note.take(MAX_NOTE)) }

    fun onMonthlyChange(on: Boolean) = updateState { it.copy(monthly = on) }

    fun onRefundChange(on: Boolean) = updateState { it.copy(refund = on) }

    fun onSharedChange(on: Boolean) = updateState { it.copy(shared = on) }

    fun onOpenNewTower() = updateState { it.copy(newTowerOpen = true, newTowerName = "", newTowerProblem = null) }

    fun onCancelNewTower() = updateState { it.copy(newTowerOpen = false, newTowerName = "", newTowerProblem = null) }

    fun onNewTowerNameChange(name: String) = updateState { it.copy(newTowerName = name, newTowerProblem = null) }

    fun onCreateTower() {
        val town = snapshot ?: return
        val name = state.value.newTowerName
        val problem = checkTowerName(name, town.categories, editingId = null)
        if (problem != null) {
            updateState { it.copy(newTowerProblem = problem) }
            return
        }
        val category = Category(
            id = newId(),
            name = name.trim(),
            short = shortNameFor(name),
            colorIndex = nextColorIndex(town.categories),
            capCents = null,
            position = (town.categories.maxOfOrNull { it.position } ?: -1) + 1,
        )
        updateState { it.copy(newTowerOpen = false, newTowerName = "", selectedTowerId = category.id, towerBricks = 0) }
        tryToExecute(
            call = { ledger.putCategory(category) },
            onSuccess = ::towerSaved,
            onError = { error -> updateState { it.copy(saveFailed = error.toMessage()) } },
        )
    }

    /** The town flow re-emits the new tower, so a successful save has nothing more to do. */
    private fun towerSaved(done: Unit) = done

    fun onSubmit() {
        val form = state.value
        val town = snapshot ?: return
        val categoryId = form.selectedTowerId ?: return
        if (!form.canSubmit) return
        val existing = original
        val expense = if (existing != null) {
            existing.copy(
                categoryId = categoryId,
                amountCents = form.amountCents,
                note = form.note.trim(),
                recurring = form.monthly,
                shared = form.shared,
                refund = form.refund,
            )
        } else {
            Expense(
                id = newId(),
                categoryId = categoryId,
                amountCents = form.amountCents,
                date = clock.today(town.town.settings),
                note = form.note.trim(),
                recurring = form.monthly,
                paidBy = town.viewerId,
                createdAt = town.serverTime,
            )
        }
        tryToExecute(
            call = { ledger.putExpense(expense) },
            onSuccess = {
                if (existing == null) {
                    val name = form.towers.firstOrNull { it.id == categoryId }?.name.orEmpty()
                    feedback.brickAdded(expense.id, "Added ${amountText(expense.amountCents, form.currency)} to $name")
                    sendEffect(BrickFormEffect.ShowMonth)
                } else {
                    feedback.showToast("Brick saved")
                    sendEffect(BrickFormEffect.Close)
                }
            },
            onError = { error -> updateState { it.copy(saveFailed = error.toMessage()) } },
        )
    }

    /** Edit only: hides the brick and offers Undo, which puts the same row back. */
    fun onDelete() {
        val existing = original ?: return
        tryToExecute(
            call = { ledger.deleteExpense(existing.id) },
            onSuccess = {
                feedback.showToast("Brick deleted", Undo.RestoreBrick(existing))
                sendEffect(BrickFormEffect.Close)
            },
            onError = { error -> updateState { it.copy(saveFailed = error.toMessage()) } },
        )
    }

    private companion object {
        const val MAX_NOTE = 80
    }
}
