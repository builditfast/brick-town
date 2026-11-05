package ai.freeapp.bricktown.auth

import ai.freeapp.bricktown.core.error.ErrorState
import ai.freeapp.bricktown.core.ui.BaseViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/*
 * Sign in and Create account. Neither navigates on success: a saved session
 * flips AuthRepository.isSignedIn, and App moves the whole stack to Month.
 * So these ViewModels have no effects of their own (Nothing).
 */

data class SignInUiState(
    val email: String = "",
    val password: String = "",
    val isSubmitting: Boolean = false,
    val error: ErrorState? = null,
)

class SignInViewModel(
    private val auth: AuthRepository,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : BaseViewModel<SignInUiState, Nothing>(SignInUiState(), dispatcher) {

    fun onEmailChange(value: String) = updateState { it.copy(email = value, error = null) }

    fun onPasswordChange(value: String) = updateState { it.copy(password = value, error = null) }

    fun onSubmit() {
        val current = state.value
        if (current.isSubmitting) return
        val problem = when {
            !AccountRules.isValidEmail(current.email.trim()) -> "Enter the email address you signed up with."
            current.password.isEmpty() -> "Enter your password."
            else -> null
        }
        if (problem != null) {
            updateState { it.copy(error = ErrorState.Validation(problem)) }
            return
        }
        updateState { it.copy(isSubmitting = true, error = null) }
        tryToExecute(
            call = { auth.signIn(current.email, current.password) },
            onSuccess = { updateState { it.copy(isSubmitting = false, password = "") } },
            onError = { error ->
                // On this screen a 401 means the credentials, not an ended session.
                val shown = if (error == ErrorState.Unauthorized) ErrorState.Validation(WRONG_CREDENTIALS) else error
                updateState { it.copy(isSubmitting = false, error = shown) }
            },
        )
    }

    companion object {
        const val WRONG_CREDENTIALS = "That email and password don't match. Try again, or create an account."
    }
}

data class CreateAccountUiState(
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val isSubmitting: Boolean = false,
    val error: ErrorState? = null,
)

class CreateAccountViewModel(
    private val auth: AuthRepository,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : BaseViewModel<CreateAccountUiState, Nothing>(CreateAccountUiState(), dispatcher) {

    fun onNameChange(value: String) = updateState { it.copy(name = value, error = null) }

    fun onEmailChange(value: String) = updateState { it.copy(email = value, error = null) }

    fun onPasswordChange(value: String) = updateState { it.copy(password = value, error = null) }

    fun onSubmit() {
        val current = state.value
        if (current.isSubmitting) return
        val problem = AccountRules.nameProblem(current.name)
            ?: "Enter a valid email address.".takeUnless { AccountRules.isValidEmail(current.email.trim()) }
            ?: AccountRules.passwordProblem(current.password)
        if (problem != null) {
            updateState { it.copy(error = ErrorState.Validation(problem)) }
            return
        }
        updateState { it.copy(isSubmitting = true, error = null) }
        tryToExecute(
            call = { auth.createAccount(current.name, current.email, current.password) },
            onSuccess = { updateState { it.copy(isSubmitting = false, password = "") } },
            // A taken email arrives as a 409 with the server's own sentence.
            onError = { error -> updateState { it.copy(isSubmitting = false, error = error) } },
        )
    }
}
