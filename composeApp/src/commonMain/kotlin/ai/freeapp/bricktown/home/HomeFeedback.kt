package ai.freeapp.bricktown.home

import ai.freeapp.bricktown.brick.BrickLedger
import ai.freeapp.bricktown.domain.Expense
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** What the toast's Undo does. */
sealed interface Undo {
    /** Takes back a brick that was just added. */
    data class RemoveBrick(val id: String) : Undo

    /** Puts back a brick that was just deleted (a re-put restores the hidden row). */
    data class RestoreBrick(val expense: Expense) : Undo
}

@Immutable
data class ToastUi(val message: String, val undo: Undo?, val sequence: Int)

@Immutable
data class FeedbackState(
    val toast: ToastUi? = null,
    /** Raised on each celebration so [Confetti] replays; 0 means none yet. */
    val burst: Int = 0,
    /** Home reads this once to play [burst]: Home re-entered after Settings must not replay an old one. */
    val celebrate: Boolean = false,
    /** Month reads this once to make Penny jump after a brick was added. */
    val pennyJump: Boolean = false,
)

/** Whether a new brick throws confetti: the Celebrations switch in Settings. */
fun interface CelebrationSetting {
    fun enabled(): Boolean
}

/**
 * The toast, the confetti and Penny's jump outlive the screen that raised them:
 * Add pops back to a new Month entry, which has a ViewModel store of its own. So
 * they live here, one instance for the app (a Koin singleton).
 */
class HomeFeedback(
    private val ledger: BrickLedger,
    private val celebrations: CelebrationSetting,
) {
    private val _state = MutableStateFlow(FeedbackState())
    val state: StateFlow<FeedbackState> = _state.asStateFlow()
    private var sequence = 0

    fun showToast(message: String, undo: Undo? = null) {
        _state.update { it.copy(toast = ToastUi(message, undo, ++sequence)) }
    }

    /** A brick was added: toast with Undo, plus confetti and a jump when celebrations are on. */
    fun brickAdded(expenseId: String, message: String) {
        val on = celebrations.enabled()
        _state.update {
            it.copy(
                toast = ToastUi(message, Undo.RemoveBrick(expenseId), ++sequence),
                burst = if (on) it.burst + 1 else it.burst,
                celebrate = on || it.celebrate,
                pennyJump = on || it.pennyJump,
            )
        }
    }

    fun dismissToast() {
        _state.update { it.copy(toast = null) }
    }

    fun consumeJump() {
        _state.update { it.copy(pennyJump = false) }
    }

    fun consumeCelebration() {
        _state.update { it.copy(celebrate = false) }
    }

    /** Runs the toast's Undo, then closes the toast. */
    suspend fun undo() {
        val toast = _state.value.toast ?: return
        _state.update { it.copy(toast = null) }
        when (val action = toast.undo) {
            is Undo.RemoveBrick -> ledger.deleteExpense(action.id)
            is Undo.RestoreBrick -> ledger.putExpense(action.expense)
            null -> Unit
        }
    }
}
