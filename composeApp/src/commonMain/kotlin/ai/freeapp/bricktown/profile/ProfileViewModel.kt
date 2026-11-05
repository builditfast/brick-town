package ai.freeapp.bricktown.profile

import ai.freeapp.bricktown.auth.AuthRepository
import ai.freeapp.bricktown.auth.User
import ai.freeapp.bricktown.core.error.ErrorState
import ai.freeapp.bricktown.core.ui.BaseViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

data class ProfileUiState(
    val user: User? = null,
    val isLoadingUser: Boolean = false,
    val showDeleteConfirm: Boolean = false,
    val isDeleting: Boolean = false,
    val isSigningOut: Boolean = false,
    val error: ErrorState? = null,
)

/**
 * The ACCOUNT group of Settings. Signing out and deleting end the session in the
 * repository, and App moves the whole stack to Landing: there is no
 * navigation effect here, and this ViewModel is cleared with the stack.
 *
 * Deleting takes two steps on purpose: [onDeleteAccount] only opens the
 * confirm dialog, and only [onConfirmDelete] calls the server.
 *
 * The reminder lives in the Settings Evening nudge, which owns the one
 * [ai.freeapp.bricktown.reminders.DailyReminder] alarm.
 */
class ProfileViewModel(
    private val auth: AuthRepository,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : BaseViewModel<ProfileUiState, Nothing>(ProfileUiState(), dispatcher) {

    init {
        tryToCollect(
            flow = { auth.currentUser },
            onEach = { user -> updateState { it.copy(user = user) } },
            onError = { error -> updateState { it.copy(error = error) } },
        )
        // Launched offline, the start check could not load the account.
        if (auth.currentUser.value == null) loadUser()
    }

    fun loadUser() {
        updateState { it.copy(isLoadingUser = true, error = null) }
        tryToExecute(
            call = auth::refreshUser,
            onSuccess = { updateState { it.copy(isLoadingUser = false) } },
            onError = { error -> updateState { it.copy(isLoadingUser = false, error = error) } },
        )
    }

    fun onSignOut() {
        if (state.value.isSigningOut || state.value.isDeleting) return
        updateState { it.copy(isSigningOut = true, error = null) }
        tryToExecute(
            call = auth::signOut,
            onSuccess = { updateState { it.copy(isSigningOut = false) } },
            onError = { error -> updateState { it.copy(isSigningOut = false, error = error) } },
        )
    }

    /**
     * Refused while a sign-out is in flight: once it clears the tokens, the
     * delete would go out without them, get a 401, and leave the account
     * standing while the user lands on Landing believing it is gone.
     */
    fun onDeleteAccount() {
        if (state.value.isSigningOut) return
        updateState { it.copy(showDeleteConfirm = true, error = null) }
    }

    fun onDismissDelete() {
        if (state.value.isDeleting) return
        updateState { it.copy(showDeleteConfirm = false) }
    }

    fun onConfirmDelete() {
        if (state.value.isDeleting || state.value.isSigningOut) return
        updateState { it.copy(isDeleting = true, error = null) }
        tryToExecute(
            call = auth::deleteAccount,
            onSuccess = { updateState { it.copy(isDeleting = false, showDeleteConfirm = false) } },
            // Usually nothing was deleted. If only the answer was lost, the next
            // request finds the session gone and the repository wipes the phone.
            onError = { error -> updateState { it.copy(isDeleting = false, showDeleteConfirm = false, error = error) } },
        )
    }
}
