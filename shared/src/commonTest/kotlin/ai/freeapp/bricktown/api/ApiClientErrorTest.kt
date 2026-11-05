package ai.freeapp.bricktown.api

import ai.freeapp.bricktown.auth.ChangePasswordRequest
import ai.freeapp.bricktown.auth.RegisterRequest
import ai.freeapp.bricktown.core.error.ErrorState
import ai.freeapp.bricktown.core.error.toErrorState
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** The server's `{"error": ...}` replies reach the UI as its own sentence, not as a parse failure. */
class ApiClientErrorTest {

    private fun apiAnswering(status: HttpStatusCode, body: String): ApiClient {
        val engine = MockEngine { respond(body, status, headersOf(HttpHeaders.ContentType, "application/json")) }
        return ApiClient(ApiClient.defaultClient(engine = engine), baseUrl = "http://test")
    }

    @Test
    fun aTakenEmailIsAValidationErrorWithTheServersSentence() = runTest {
        val api = apiAnswering(HttpStatusCode.Conflict, """{"error":"An account with this email already exists."}""")

        val error = assertFailsWith<ApiException> { api.register(RegisterRequest("a@b.co", "password1", "Ana")) }

        assertEquals(ErrorState.Validation("An account with this email already exists."), error.toErrorState())
    }

    @Test
    fun aWrongCurrentPasswordIsAValidationErrorNotAnEndedSession() = runTest {
        val api = apiAnswering(HttpStatusCode.BadRequest, """{"error":"Your current password is wrong."}""")

        val error = assertFailsWith<ApiException> { api.changePassword(ChangePasswordRequest("nope", "password2")) }

        assertEquals(ErrorState.Validation("Your current password is wrong."), error.toErrorState())
    }

    @Test
    fun aServerErrorKeepsItsStatus() = runTest {
        val api = apiAnswering(HttpStatusCode.InternalServerError, "")

        val error = assertFailsWith<ApiException> { api.me() }

        assertEquals(ErrorState.ServerError(500), error.toErrorState())
    }

    @Test
    fun aNoContentReplyIsSuccess() = runTest {
        val api = apiAnswering(HttpStatusCode.NoContent, "")

        api.deleteAccount()
        api.logout("r1")
    }
}
