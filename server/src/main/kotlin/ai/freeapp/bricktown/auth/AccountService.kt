package ai.freeapp.bricktown.auth

import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.time.toJavaDuration

/**
 * The account rules, kept out of the routes so the JSON API and the web
 * deletion page share one implementation of "check the password" and
 * "delete everything".
 */
class AccountService(
    private val users: UserRepository,
    private val sessions: RefreshTokenRepository,
    private val hasher: PasswordHasher,
    private val accessTokens: AccessTokens,
) {
    // Verifying against a real hash when the email is unknown keeps the
    // response time from telling an attacker which emails have accounts.
    private val decoyHash by lazy { hasher.hash("decoy-password-for-timing") }

    sealed interface Registration {
        data class Created(val auth: AuthResponse) : Registration
        data class Invalid(val message: String) : Registration
        data object EmailTaken : Registration
    }

    sealed interface PasswordChange {
        data object Changed : PasswordChange
        data object WrongCurrentPassword : PasswordChange
        data class Invalid(val message: String) : PasswordChange
    }

    suspend fun register(request: RegisterRequest): Registration {
        val name = request.name.trim()
        val email = AccountRules.normalizeEmail(request.email)
        val problem = when {
            name.isEmpty() -> "Tell us your name."
            name.length > AccountRules.MAX_NAME_LENGTH -> "That name is too long."
            !AccountRules.isValidEmail(email) -> "That doesn't look like an email address."
            else -> AccountRules.passwordProblem(request.password)
        }
        if (problem != null) return Registration.Invalid(problem)

        val user = users.create(email, name, hasher.hash(request.password)) ?: return Registration.EmailTaken
        return Registration.Created(startSession(user))
    }

    /** Null when the email and password do not match an account. */
    suspend fun login(email: String, password: String): AuthResponse? =
        verifiedUser(email, password)?.let { startSession(it) }

    /** A new pair for a live refresh token, which is spent; null means sign in again. */
    suspend fun refresh(refreshToken: String): AuthTokens? {
        val newToken = RefreshTokens.generate()
        val session = sessions.rotate(RefreshTokens.hash(refreshToken), RefreshTokens.hash(newToken), refreshExpiry())
            ?: return null
        return AuthTokens(accessTokens.issue(session.userId, session.id), newToken)
    }

    suspend fun logout(refreshToken: String) {
        sessions.revoke(RefreshTokens.hash(refreshToken))
    }

    suspend fun findUser(id: UUID): StoredUser? = users.findById(id)

    /**
     * Keeps [currentSession] signed in and ends every other session, so a
     * password change locks out whoever else had the account open.
     */
    suspend fun changePassword(
        userId: UUID,
        currentSession: UUID?,
        request: ChangePasswordRequest,
    ): PasswordChange {
        val user = users.findById(userId) ?: return PasswordChange.WrongCurrentPassword
        if (!hasher.verify(request.currentPassword, user.passwordHash)) return PasswordChange.WrongCurrentPassword
        AccountRules.passwordProblem(request.newPassword)?.let { return PasswordChange.Invalid(it) }

        users.updatePassword(userId, hasher.hash(request.newPassword))
        sessions.revokeAllExcept(userId, keep = currentSession)
        return PasswordChange.Changed
    }

    /** Hard delete of the user and, by cascade, everything they own. */
    suspend fun delete(userId: UUID): Boolean = users.delete(userId)

    /** The web deletion page: false when the email and password do not match. */
    suspend fun deleteWithPassword(email: String, password: String): Boolean {
        val user = verifiedUser(email, password) ?: return false
        return users.delete(user.id)
    }

    private suspend fun verifiedUser(email: String, password: String): StoredUser? {
        val user = users.findByEmail(AccountRules.normalizeEmail(email))
        val matches = hasher.verify(password, user?.passwordHash ?: decoyHash)
        return user?.takeIf { matches }
    }

    private suspend fun startSession(user: StoredUser): AuthResponse {
        val refreshToken = RefreshTokens.generate()
        val sessionId = sessions.create(user.id, RefreshTokens.hash(refreshToken), refreshExpiry())
        return AuthResponse(accessTokens.issue(user.id, sessionId), refreshToken, user.toDto())
    }

    private fun refreshExpiry(): OffsetDateTime =
        OffsetDateTime.now(ZoneOffset.UTC).plus(RefreshTokens.TTL.toJavaDuration())
}
