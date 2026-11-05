package ai.freeapp.bricktown.core

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import kotlinx.serialization.Serializable

/**
 * The one error body every JSON route returns. The client's ErrorMapper turns
 * 400/409/422 into a validation message and 401 into "signed out", so the
 * message is written for the person holding the phone.
 */
@Serializable
data class ErrorResponse(val error: String)

suspend fun ApplicationCall.respondError(status: HttpStatusCode, message: String) =
    respond(status, ErrorResponse(message))
