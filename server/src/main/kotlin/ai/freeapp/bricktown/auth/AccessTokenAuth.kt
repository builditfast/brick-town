package ai.freeapp.bricktown.auth

import ai.freeapp.bricktown.core.respondError
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.principal
import org.koin.ktor.ext.inject
import java.util.UUID

/** Pass to `authenticate(...)` around every route that needs a signed-in user. */
const val ACCESS_TOKEN_AUTH = "access-token"

fun Application.installAccessTokenAuth() {
    val accessTokens by inject<AccessTokens>()
    val users by inject<UserRepository>()

    install(Authentication) {
        jwt(ACCESS_TOKEN_AUTH) {
            realm = AccessTokens.REALM
            verifier(accessTokens.verifier)
            // A signature is not enough: a deleted account's token stays valid
            // for up to 15 minutes, and its owner row is gone. Checking here
            // turns that into one 401 everywhere instead of a 500 on the first
            // insert that references the user.
            validate { credential ->
                val userId = credential.subject?.toUuidOrNull()
                if (userId != null && users.exists(userId)) JWTPrincipal(credential.payload) else null
            }
            challenge { _, _ ->
                call.respondError(HttpStatusCode.Unauthorized, "Sign in again.")
            }
        }
    }
}

/** The signed-in user. Only valid inside `authenticate(ACCESS_TOKEN_AUTH)`. */
fun ApplicationCall.userId(): UUID =
    UUID.fromString(requireNotNull(principal<JWTPrincipal>()?.subject) { "route is not authenticated" })

/** The session (refresh token row) the access token was issued with. */
fun ApplicationCall.sessionId(): UUID? =
    principal<JWTPrincipal>()?.payload?.getClaim(AccessTokens.CLAIM_SESSION)?.asString()?.toUuidOrNull()

private fun String.toUuidOrNull(): UUID? = runCatching { UUID.fromString(this) }.getOrNull()
