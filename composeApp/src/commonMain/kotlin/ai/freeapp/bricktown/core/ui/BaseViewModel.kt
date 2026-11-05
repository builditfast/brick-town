package ai.freeapp.bricktown.core.ui

import ai.freeapp.bricktown.core.error.ErrorState
import ai.freeapp.bricktown.core.error.toErrorState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * Base for every screen ViewModel: one immutable [state] the UI renders, and a
 * stream of one-shot [effect]s it reacts to (navigation, snackbars).
 *
 * The point of [tryToExecute] and [tryToCollect] is that no screen and no
 * repository ever writes a `try`/`catch` again. Failures are converted to
 * [ErrorState] here, once, and every call site is forced to say what happens
 * when its call fails.
 *
 * EXTENSION POINT: subclass per screen with your own state and effect types.
 * Pass the dispatcher through, so a test can run the ViewModel on its own
 * test dispatcher instead of waiting on real threads:
 *
 *     class ProfileViewModel(
 *         private val repo: ProfileRepository,
 *         dispatcher: CoroutineDispatcher = Dispatchers.Default,
 *     ) : BaseViewModel<ProfileUiState, ProfileEffect>(ProfileUiState(), dispatcher)
 */
abstract class BaseViewModel<S, E>(
    initialState: S,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<S> = _state.asStateFlow()

    // A Channel, not a SharedFlow: an effect raised while the screen is away
    // must still arrive when it comes back, and must be delivered exactly once.
    private val _effect = Channel<E>(capacity = Channel.BUFFERED)
    val effect: Flow<E> = _effect.receiveAsFlow()

    protected fun updateState(reduce: (S) -> S) {
        _state.update(reduce)
    }

    protected fun sendEffect(effect: E) {
        _effect.trySend(effect)
    }

    /**
     * Runs a suspending call and routes its outcome. Cancellation is rethrown:
     * a cancelled screen is not a failed one, and swallowing it here would both
     * break structured concurrency and flash an error on the way out.
     */
    protected fun <T> tryToExecute(
        call: suspend () -> T,
        onSuccess: (T) -> Unit,
        onError: (ErrorState) -> Unit,
        dispatcher: CoroutineDispatcher = defaultDispatcher,
    ): Job = viewModelScope.launch(dispatcher) {
        try {
            onSuccess(call())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            onError(throwable.toErrorState())
        }
    }

    /**
     * Collects a flow for as long as the screen lives. [flow] is a factory, not
     * a value, so a retry after an error builds a fresh collection instead of
     * resubscribing to a stream that already terminated.
     */
    protected fun <T> tryToCollect(
        flow: () -> Flow<T>,
        onEach: (T) -> Unit,
        onError: (ErrorState) -> Unit,
        dispatcher: CoroutineDispatcher = defaultDispatcher,
    ): Job = viewModelScope.launch(dispatcher) {
        try {
            flow().collect { value -> onEach(value) }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            onError(throwable.toErrorState())
        }
    }
}
