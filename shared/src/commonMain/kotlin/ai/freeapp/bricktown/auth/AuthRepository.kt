package ai.freeapp.bricktown.auth

import ai.freeapp.bricktown.api.ApiClient
import ai.freeapp.bricktown.core.auth.ObservableTokenStore
import ai.freeapp.bricktown.db.AppDatabase
import ai.freeapp.bricktown.town.wipeTownData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.coroutines.cancellation.CancellationException

/**
 * The account the app is signed in to. ViewModels depend on this interface,
 * so their tests run against a fake.
 *
 * Failures propagate as exceptions, like every repository in the template;
 * the base ViewModel turns them into `ErrorState`.
 */
interface AuthRepository {
    /** True while a token pair is stored. Flips to false on every way a session ends. */
    val isSignedIn: StateFlow<Boolean>

    /** Known after sign-in or a successful [refreshUser]; null before that and after sign-out. */
    val currentUser: StateFlow<User?>

    suspend fun signIn(email: String, password: String): User
    suspend fun createAccount(name: String, email: String, password: String): User

    /** `GET /me`: proves the stored session is still accepted and loads the account. */
    suspend fun refreshUser(): User

    suspend fun changePassword(currentPassword: String, newPassword: String)

    /** Ends the session here, and on the server when it can be reached. Never fails. */
    suspend fun signOut()

    /** Deletes the account on the server, then ends the session here. Fails if the server cannot be reached. */
    suspend fun deleteAccount()
}

class AuthRepositoryImpl(
    private val api: ApiClient,
    private val tokens: ObservableTokenStore,
    private val db: AppDatabase,
) : AuthRepository {

    private val _currentUser = MutableStateFlow<User?>(null)
    override val isSignedIn: StateFlow<Boolean> = tokens.signedIn
    override val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    init {
        // The refresh interceptor ends a session the server refused (the
        // account deleted on the web, the password changed on another phone)
        // without passing through here; its rows must not outlive it either.
        tokens.doOnClear {
            wipeLocalData()
            _currentUser.value = null
        }
    }

    override suspend fun signIn(email: String, password: String): User =
        startSession(api.login(LoginRequest(AccountRules.normalizeEmail(email), password)))

    override suspend fun createAccount(name: String, email: String, password: String): User =
        startSession(api.register(RegisterRequest(AccountRules.normalizeEmail(email), password, name.trim())))

    override suspend fun refreshUser(): User = api.me().also { _currentUser.value = it }

    override suspend fun changePassword(currentPassword: String, newPassword: String) {
        api.changePassword(ChangePasswordRequest(currentPassword, newPassword))
    }

    override suspend fun signOut() {
        val refreshToken = tokens.read()?.refreshToken
        try {
            if (refreshToken != null) api.logout(refreshToken)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            // Revoking on the server is best effort: an offline phone must
            // still be able to sign out, and the token expires on its own.
        } finally {
            tokens.clear()
        }
    }

    override suspend fun deleteAccount() {
        api.deleteAccount()
        tokens.clear()
    }

    /**
     * Starts from the slate every session end leaves: `tokens.clear()` runs
     * every registered wipe action (this cache, the outbox, the daily
     * reminder) before the new tokens are saved, because saving them is what
     * tells the UI to show the signed-in screens. That also covers a session
     * that ended without a clear (a token store that could no longer be
     * decrypted reads as empty): the next account never sees its rows.
     */
    private fun startSession(auth: AuthResponse): User {
        tokens.clear()
        _currentUser.value = auth.user
        tokens.save(auth.tokens)
        return auth.user
    }

    /**
     * EXTENSION POINT: every table that holds the user's data is emptied here.
     * State kept outside SQLDelight registers its own `tokens.doOnClear`, as
     * `Outbox` and `DailyReminder` do.
     */
    private fun wipeLocalData() {
        db.wipeTownData()
    }
}
