package ai.freeapp.bricktown.auth

import kotlinx.serialization.Serializable
import java.util.UUID

/*
 * Wire DTOs. Field names must match the shared module's auth DTOs byte for
 * byte, and the token pair must match core/auth/AuthTokens, which the phone's
 * refresh interceptor decodes.
 */

@Serializable
data class UserDto(val id: String, val email: String, val name: String)

@Serializable
data class RegisterRequest(val email: String, val password: String, val name: String)

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class RefreshRequest(val refreshToken: String)

@Serializable
data class ChangePasswordRequest(val currentPassword: String, val newPassword: String)

@Serializable
data class AuthResponse(val accessToken: String, val refreshToken: String, val user: UserDto)

/** What POST /auth/refresh answers: the new pair and nothing else. */
@Serializable
data class AuthTokens(val accessToken: String, val refreshToken: String)

data class StoredUser(
    val id: UUID,
    val email: String,
    val name: String,
    val passwordHash: String,
) {
    fun toDto() = UserDto(id = id.toString(), email = email, name = name)
}

object AccountRules {
    const val MIN_PASSWORD_LENGTH = 8

    /** bcrypt ignores everything past 72 bytes; refuse rather than silently truncate. */
    const val MAX_PASSWORD_BYTES = 72
    const val MAX_NAME_LENGTH = 80

    fun normalizeEmail(email: String): String = email.trim().lowercase()

    fun isValidEmail(email: String): Boolean {
        val at = email.indexOf('@')
        return at > 0 && email.indexOf('.', at) > at + 1 && !email.endsWith('.') && ' ' !in email
    }

    /** The message to show, or null when the password is acceptable. */
    fun passwordProblem(password: String): String? = when {
        password.length < MIN_PASSWORD_LENGTH -> "Use at least $MIN_PASSWORD_LENGTH characters."
        password.encodeToByteArray().size > MAX_PASSWORD_BYTES -> "Use at most $MAX_PASSWORD_BYTES characters."
        else -> null
    }
}
