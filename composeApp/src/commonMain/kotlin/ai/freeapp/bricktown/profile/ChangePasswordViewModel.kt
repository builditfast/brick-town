package ai.freeapp.bricktown.profile

import ai.freeapp.bricktown.auth.AccountRules
import ai.freeapp.bricktown.auth.AuthRepository
import ai.freeapp.bricktown.core.error.ErrorState
import ai.freeapp.bricktown.core.ui.BaseViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

data class ChangePasswordUiState(
    val currentPassword: String = "",
    val newPassword: String = "",
    val isSaving: Boolean = false,
    val isChanged: Boolean = false,
    val error: ErrorState? = null,
)

/**
 * The server keeps this session and signs out every other one. A wrong
 * current password comes back as a 400 with the server's sentence, never a
 * 401, so it shows on the form instead of ending the session.
 */
class ChangePasswordViewModel(
    private val auth: AuthRepository,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : BaseViewModel<ChangePasswordUiState, Nothing>(ChangePasswordUiState(), dispatcher) {

    fun onCurrentPasswordChange(value: String) = updateState { it.copy(currentPassword = value, error = null) }

    fun onNewPasswordChange(value: String) = updateState { it.copy(newPassword = value, error = null) }

    fun onSubmit() {
        val current = state.value
        if (current.isSaving) return
        val problem = when {
            current.currentPassword.isEmpty() -> "Enter your current password."
            current.newPassword == current.currentPassword -> "Choose a password different from the current one."
            else -> AccountRules.passwordProblem(current.newPassword)
        }
        if (problem != null) {
            updateState { it.copy(error = ErrorState.Validation(problem)) }
            return
        }
        updateState { it.copy(isSaving = true, error = null) }
        tryToExecute(
            call = { auth.changePassword(current.currentPassword, current.newPassword) },
            onSuccess = {
                updateState { ChangePasswordUiState(isChanged = true) }
            },
            onError = { error -> updateState { it.copy(isSaving = false, error = error) } },
        )
    }
}
