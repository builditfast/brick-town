package ai.freeapp.bricktown.auth

import ai.freeapp.bricktown.core.error.ErrorState
import ai.freeapp.bricktown.core.error.toErrorState
import ai.freeapp.bricktown.core.ui.BaseViewModel
import ai.freeapp.bricktown.rollover.RolloverGate
import ai.freeapp.bricktown.town.JoinCodeStore
import ai.freeapp.bricktown.town.SyncStatus
import ai.freeapp.bricktown.town.TownLoader
import androidx.compose.runtime.Immutable
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.LocalDate

/** The first screen after a launch with a stored session. */
sealed interface StartEffect {
    /** A join link was opened while signed out (spec 5.5): it goes first, with or without a town. */
    data class ShowJoin(val code: String) : StartEffect
    data object ShowOnboarding : StartEffect
    data class ShowRollover(val monthStart: LocalDate) : StartEffect
    data object ShowMonth : StartEffect
}

/** [error] is set only when there is nothing to show: no town on the phone and the server unreachable. */
@Immutable
data class StartUiState(val error: ErrorState? = null)

/**
 * Checks the stored session and loads the town, then names the first screen.
 * A session the server refuses ends here (App then shows Landing); an offline
 * launch keeps the session and opens the cached town.
 */
class StartViewModel(
    private val auth: AuthRepository,
    private val town: TownLoader,
    private val joinCodes: JoinCodeStore,
    private val rollover: RolloverGate,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : BaseViewModel<StartUiState, StartEffect>(StartUiState(), dispatcher) {

    init {
        check()
    }

    fun onRetry() {
        updateState { it.copy(error = null) }
        check()
    }

    private fun check() {
        tryToExecute(
            call = ::firstScreen,
            onSuccess = { effect -> effect?.let(::sendEffect) },
            onError = { error -> updateState { it.copy(error = error) } },
        )
    }

    /** Null when the session ended: App moves the stack to Landing. */
    private suspend fun firstScreen(): StartEffect? {
        if (!sessionStands()) return null
        loadTown()
        joinCodes.read()?.let { code ->
            joinCodes.write(null)
            return StartEffect.ShowJoin(code)
        }
        if (town.status.value != SyncStatus.Ready) return StartEffect.ShowOnboarding
        rollover.due()?.let { return StartEffect.ShowRollover(it) }
        return StartEffect.ShowMonth
    }

    /** Only a 401 ends the session; no network keeps it. */
    private suspend fun sessionStands(): Boolean {
        try {
            auth.refreshUser()
            return true
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            if (error.toErrorState() != ErrorState.Unauthorized) return true
        }
        // The refused refresh usually cleared the session already; a 401 that
        // left it stored would otherwise keep this screen spinning.
        if (auth.isSignedIn.value) auth.signOut()
        return false
    }

    /** A failed refresh is fine with a town on the phone: it opens, and TownSync catches up. */
    private suspend fun loadTown() {
        try {
            town.refresh()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            if (town.status.value != SyncStatus.Ready) throw error
        }
    }
}
