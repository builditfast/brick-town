package ai.freeapp.bricktown.core.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A [TokenStore] that says whether a session exists, for the UI to follow.
 *
 * Every way a session ends goes through [clear]: sign-out, account deletion,
 * and the refresh interceptor giving up after the server refused the refresh
 * token. So the app root watches [signedIn] alone and never has to know which
 * of the three happened. There must be exactly one instance, shared by the
 * HTTP client and the repository; `sharedModule` binds it as the [TokenStore].
 */
class ObservableTokenStore(private val delegate: TokenStore) : TokenStore {

    private val _signedIn = MutableStateFlow(delegate.read() != null)
    val signedIn: StateFlow<Boolean> = _signedIn.asStateFlow()
    private val clearActions = mutableListOf<() -> Unit>()

    /**
     * Runs [action] on every [clear], whichever of the three ended the session,
     * and before [signedIn] turns false, so what it removes is gone by the time
     * the UI shows Landing. Register once, at startup.
     */
    fun doOnClear(action: () -> Unit) {
        clearActions += action
    }

    override fun read(): AuthTokens? = delegate.read()

    override fun save(tokens: AuthTokens) {
        delegate.save(tokens)
        _signedIn.value = true
    }

    override fun clear() {
        delegate.clear()
        try {
            clearActions.forEach { it() }
        } finally {
            _signedIn.value = false
        }
    }
}
