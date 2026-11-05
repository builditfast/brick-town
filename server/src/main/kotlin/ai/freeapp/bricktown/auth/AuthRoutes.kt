package ai.freeapp.bricktown.auth

import ai.freeapp.bricktown.core.respondError
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject

/** Public routes: create an account, sign in, trade a refresh token, sign out. */
fun Route.authRoutes() {
    val accounts by inject<AccountService>()

    route("/auth") {
        post("/register") {
            when (val result = accounts.register(call.receive<RegisterRequest>())) {
                is AccountService.Registration.Created -> call.respond(HttpStatusCode.Created, result.auth)
                is AccountService.Registration.Invalid -> call.respondError(HttpStatusCode.BadRequest, result.message)
                AccountService.Registration.EmailTaken ->
                    call.respondError(HttpStatusCode.Conflict, "An account with this email already exists.")
            }
        }

        post("/login") {
            val body = call.receive<LoginRequest>()
            val auth = accounts.login(body.email, body.password)
                ?: return@post call.respondError(
                    HttpStatusCode.Unauthorized,
                    "That email and password don't match. Try again, or create an account.",
                )
            call.respond(auth)
        }

        post("/refresh") {
            val tokens = accounts.refresh(call.receive<RefreshRequest>().refreshToken)
                ?: return@post call.respondError(HttpStatusCode.Unauthorized, "Sign in again.")
            call.respond(tokens)
        }

        // Always 204: signing out an unknown or already revoked token is not an error.
        post("/logout") {
            accounts.logout(call.receive<RefreshRequest>().refreshToken)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

/** The signed-in account. Mount inside `authenticate(ACCESS_TOKEN_AUTH)`. */
fun Route.meRoutes() {
    val accounts by inject<AccountService>()

    route("/me") {
        get {
            val user = accounts.findUser(call.userId())
                ?: return@get call.respondError(HttpStatusCode.Unauthorized, "Sign in again.")
            call.respond(user.toDto())
        }

        post("/password") {
            val body = call.receive<ChangePasswordRequest>()
            when (val result = accounts.changePassword(call.userId(), call.sessionId(), body)) {
                AccountService.PasswordChange.Changed -> call.respond(HttpStatusCode.NoContent)
                // A wrong current password is a 400, not a 401: the phone treats
                // 401 as an ended session and would sign the user out.
                AccountService.PasswordChange.WrongCurrentPassword ->
                    call.respondError(HttpStatusCode.BadRequest, "Your current password is wrong.")
                is AccountService.PasswordChange.Invalid ->
                    call.respondError(HttpStatusCode.BadRequest, result.message)
            }
        }

        delete {
            accounts.delete(call.userId())
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
