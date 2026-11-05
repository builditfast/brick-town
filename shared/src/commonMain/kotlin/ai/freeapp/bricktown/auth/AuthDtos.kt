package ai.freeapp.bricktown.auth

import ai.freeapp.bricktown.core.auth.AuthTokens
import kotlinx.serialization.Serializable

/*
 * Wire DTOs for the account routes. Field names match
 * server/.../auth/AccountModels.kt byte for byte; the class names need not.
 */

/** The signed-in account, as `GET /me` and sign-in return it. */
@Serializable
data class User(val id: String, val email: String, val name: String)

@Serializable
data class RegisterRequest(val email: String, val password: String, val name: String)

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class ChangePasswordRequest(val currentPassword: String, val newPassword: String)

/** What register and login answer. Refresh answers the token pair alone. */
@Serializable
data class AuthResponse(val accessToken: String, val refreshToken: String, val user: User) {
    val tokens: AuthTokens get() = AuthTokens(accessToken, refreshToken)
}
