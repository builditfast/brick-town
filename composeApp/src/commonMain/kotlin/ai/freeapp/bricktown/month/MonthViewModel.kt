package ai.freeapp.bricktown.month

import ai.freeapp.bricktown.brick.BrickLedger
import ai.freeapp.bricktown.core.time.TownClock
import ai.freeapp.bricktown.core.ui.BaseViewModel
import ai.freeapp.bricktown.core.ui.toMessage
import ai.freeapp.bricktown.domain.BillSkip
import ai.freeapp.bricktown.domain.Expense
import ai.freeapp.bricktown.domain.TownSnapshot
import ai.freeapp.bricktown.domain.payMonthOf
import ai.freeapp.bricktown.home.HomeFeedback
import ai.freeapp.bricktown.home.Undo
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.datetime.LocalDate

/** Nothing leaves Month as an effect: navigation goes through the screen's callbacks. */
sealed interface MonthEffect

class MonthViewModel(
    private val joinedTown: String?,
    private val ledger: BrickLedger,
    private val clock: TownClock,
    private val feedback: HomeFeedback,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : BaseViewModel<MonthUiState, MonthEffect>(MonthUiState(), dispatcher) {

    private var snapshot: TownSnapshot? = null
    private var today: LocalDate? = null

    /** "Welcome to {town}." shows once, then the Penny line comes back. */
    private var welcome: String? = joinedTown?.let { "Welcome to $it." }

    init {
        tryToCollect(
            flow = { ledger.town },
            onEach = ::onTown,
            onError = { error -> feedback.showToast(error.toMessage()) },
        )
        if (welcome != null) {
            tryToExecute(
                call = { delay(WELCOME_MILLIS) },
                onSuccess = {
                    welcome = null
                    snapshot?.let(::onTown)
                },
                onError = { welcome = null },
            )
        }
    }

    private fun onTown(town: TownSnapshot?) {
        snapshot = town
        if (town == null) return
        val day = clock.today(town.town.settings)
        today = day
        val mapped = monthUiState(town, day)
        updateState { old ->
            // The sheet that is open stays open while a sync lands; it closes only if its tower is gone.
            val keepTower = old.openTowerId?.takeIf { id -> mapped.towers.any { it.id == id } }
            val keepGhost = old.openGhostKey?.takeIf { key -> keepTower != null && mapped.towers.first { it.id == keepTower }.ghosts.any { it.seriesKey == key } }
            mapped.copy(
                pennyLine = welcome ?: mapped.pennyLine,
                openTowerId = keepTower,
                openGhostKey = keepGhost,
                towersPlayed = old.towersPlayed,
            )
        }
    }

    fun onOpenTower(index: Int) {
        val tower = state.value.towers.getOrNull(index) ?: return
        updateState { it.copy(openTowerId = tower.id, openGhostKey = null) }
    }

    fun onTowersPlayed() = updateState { it.copy(towersPlayed = true) }

    fun onCloseTower() = updateState { it.copy(openTowerId = null, openGhostKey = null) }

    fun onOpenGhost(seriesKey: String) = updateState { it.copy(openGhostKey = seriesKey) }

    fun onCloseGhost() = updateState { it.copy(openGhostKey = null) }

    /** A cap chip applies at once: the tower's own row is re-put with the new cap (null clears it). */
    fun onCapChosen(categoryId: String, capCents: Long?) {
        val category = snapshot?.categories?.firstOrNull { it.id == categoryId } ?: return
        if (category.capCents == capCents) return
        write { ledger.putCategory(category.copy(capCents = capCents)) }
    }

    /** Swipe-delete hides the row and offers Undo, which puts the saved row back. */
    fun onDeleteBrick(expenseId: String) {
        val expense = snapshot?.expenses?.firstOrNull { it.id == expenseId } ?: return
        write {
            ledger.deleteExpense(expenseId)
            feedback.showToast("Brick deleted", Undo.RestoreBrick(expense))
        }
    }

    /** Confirm turns the pencilled bill into a brick dated today, or its due date when that is earlier. */
    fun onConfirmGhost() {
        val town = snapshot ?: return
        val now = today ?: return
        val tower = state.value.openTower ?: return
        val ghost = tower.ghosts.firstOrNull { it.seriesKey == state.value.openGhostKey } ?: return
        val source = town.expenses.firstOrNull { e -> e.categoryId == ghost.categoryId && ai.freeapp.bricktown.domain.seriesKeyOf(e) == ghost.seriesKey }
        val brick = Expense(
            id = newId(),
            categoryId = ghost.categoryId,
            amountCents = ghost.amountCents,
            date = if (ghost.dueDate < now) ghost.dueDate else now,
            note = source?.note.orEmpty(),
            recurring = true,
            shared = source?.shared ?: false,
            paidBy = town.viewerId,
            createdAt = town.serverTime,
        )
        updateState { it.copy(openGhostKey = null) }
        write { ledger.putExpense(brick) }
    }

    fun onSkipGhost() {
        val town = snapshot ?: return
        val now = today ?: return
        val key = state.value.openGhostKey ?: return
        updateState { it.copy(openGhostKey = null) }
        write { ledger.skipBill(BillSkip(payMonthOf(now, town.town.settings.payday).start, key)) }
    }

    private fun write(call: suspend () -> Unit) {
        tryToExecute(call = call, onSuccess = ::writeDone, onError = { error -> feedback.showToast(error.toMessage()) })
    }

    /** The town flow re-emits the written row, so a successful write has nothing more to do. */
    private fun writeDone(done: Unit) = done

    private fun newId(): String = ai.freeapp.bricktown.brick.newBrickId()

    private companion object {
        const val WELCOME_MILLIS = 4_000L
    }
}
