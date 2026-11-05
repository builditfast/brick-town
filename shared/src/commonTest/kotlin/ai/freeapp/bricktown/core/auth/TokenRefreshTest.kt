package ai.freeapp.bricktown.core.auth

import ai.freeapp.bricktown.api.ApiClient
import ai.freeapp.bricktown.api.ApiException
import ai.freeapp.bricktown.core.error.ErrorState
import ai.freeapp.bricktown.core.error.toErrorState
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TokenRefreshTest {

    private class MemoryTokens(var tokens: AuthTokens?) : TokenStore {
        override fun read() = tokens
        override fun save(tokens: AuthTokens) {
            this.tokens = tokens
        }
        override fun clear() {
            tokens = null
        }
    }

    private class FakeRefresher(private val result: suspend () -> AuthTokens?) : TokenRefresher {
        var calls = 0
        override suspend fun refresh(refreshToken: String): AuthTokens? {
            calls++
            return result()
        }
    }

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    /** Answers 401 to the stale token and 200 to the fresh one, like the server's JWT check. */
    private val engine = MockEngine { request ->
        if (request.headers[HttpHeaders.Authorization] == "Bearer fresh") {
            respond("ok", HttpStatusCode.OK)
        } else {
            respond("""{"error":"Sign in again."}""", HttpStatusCode.Unauthorized, jsonHeaders)
        }
    }

    @Test
    fun a401IsRefreshedAndRetriedBeforeTheValidatorSeesIt() = runTest {
        val tokens = MemoryTokens(AuthTokens("stale", "refresh"))
        val refresher = FakeRefresher { AuthTokens("fresh", "refresh-2") }
        val client = ApiClient.defaultClient(tokens, refresher, engine)

        val body = client.get("http://test/items").bodyAsText()

        assertEquals("ok", body)
        assertEquals(1, refresher.calls)
        assertEquals(AuthTokens("fresh", "refresh-2"), tokens.read())
    }

    @Test
    fun aRefusedRefreshEndsTheSessionAndSurfacesThe401() = runTest {
        val tokens = ObservableTokenStore(MemoryTokens(AuthTokens("stale", "refresh")))
        val client = ApiClient.defaultClient(tokens, FakeRefresher { null }, engine)
        assertTrue(tokens.signedIn.value)

        val error = assertFailsWith<ApiException> { client.get("http://test/items") }

        assertEquals(HttpStatusCode.Unauthorized, error.response.status)
        assertEquals("Sign in again.", error.serverMessage)
        assertEquals(ErrorState.Unauthorized, error.toErrorState())
        assertNull(tokens.read())
        assertFalse(tokens.signedIn.value)
    }

    @Test
    fun aSessionTheServerEndedRunsTheClearActionsBeforeSignedInTurnsFalse() = runTest {
        val tokens = ObservableTokenStore(MemoryTokens(AuthTokens("stale", "refresh")))
        val signedInDuringClear = mutableListOf<Boolean>()
        tokens.doOnClear { signedInDuringClear += tokens.signedIn.value }
        val client = ApiClient.defaultClient(tokens, FakeRefresher { null }, engine)

        assertFailsWith<ApiException> { client.get("http://test/items") }

        assertEquals(listOf(true), signedInDuringClear)
        assertFalse(tokens.signedIn.value)
    }

    @Test
    fun aRefreshThatCannotReachTheServerKeepsTheSession() = runTest {
        val tokens = ObservableTokenStore(MemoryTokens(AuthTokens("stale", "refresh")))
        val client = ApiClient.defaultClient(tokens, FakeRefresher { throw IOException("offline") }, engine)

        val error = assertFailsWith<IOException> { client.get("http://test/items") }

        assertEquals(ErrorState.NoInternet, error.toErrorState())
        assertEquals(AuthTokens("stale", "refresh"), tokens.read())
        assertTrue(tokens.signedIn.value)
    }

    @Test
    fun aSignOutDuringTheRefreshIsNotUndone() = runTest {
        val tokens = MemoryTokens(AuthTokens("stale", "refresh"))
        // The user signs out while the refresh call is on the wire.
        val refresher = FakeRefresher {
            tokens.clear()
            AuthTokens("fresh", "refresh-2")
        }
        val client = ApiClient.defaultClient(tokens, refresher, engine)

        assertFailsWith<ApiException> { client.get("http://test/items") }

        assertNull(tokens.read())
    }

    @Test
    fun aCallerThatLeavesMidRefreshStillSavesTheRotatedPair() = runTest {
        val tokens = MemoryTokens(AuthTokens("stale", "refresh"))
        val sent = CompletableDeferred<Unit>()
        val answer = CompletableDeferred<Unit>()
        // By the time the answer arrives the server has already spent "refresh".
        val refresher = FakeRefresher {
            sent.complete(Unit)
            answer.await()
            AuthTokens("fresh", "refresh-2")
        }
        val client = ApiClient.defaultClient(tokens, refresher, engine)

        val call = launch { client.get("http://test/items") }
        sent.await()
        call.cancel()
        answer.complete(Unit)
        call.join()

        assertEquals(AuthTokens("fresh", "refresh-2"), tokens.read())
    }

    @Test
    fun withNoSessionA401IsReturnedWithoutRefreshing() = runTest {
        val refresher = FakeRefresher { AuthTokens("fresh", "refresh-2") }
        val client = ApiClient.defaultClient(MemoryTokens(null), refresher, engine)

        // What a wrong password on Sign in looks like to the interceptor.
        assertFailsWith<ApiException> { client.get("http://test/auth/login") }

        assertEquals(0, refresher.calls)
    }

    private fun refresherAnswering(status: HttpStatusCode, body: String) = HttpTokenRefresher(
        endpoint = "http://test/auth/refresh",
        client = HttpClient(MockEngine { respond(body, status, jsonHeaders) }) {
            install(ContentNegotiation) { json() }
        },
    )

    @Test
    fun theRefresherReadsTheRotatedPair() = runTest {
        val refresher = refresherAnswering(HttpStatusCode.OK, """{"accessToken":"a2","refreshToken":"r2"}""")

        assertEquals(AuthTokens("a2", "r2"), refresher.refresh("r1"))
    }

    @Test
    fun theRefresherReportsARefusedTokenAsNull() = runTest {
        val refresher = refresherAnswering(HttpStatusCode.Unauthorized, """{"error":"Sign in again."}""")

        assertNull(refresher.refresh("revoked"))
    }

    @Test
    fun theRefresherThrowsOnAServerError() = runTest {
        val refresher = refresherAnswering(HttpStatusCode.InternalServerError, "")

        assertFailsWith<Exception> { refresher.refresh("r1") }
    }

    @Test
    fun aRateLimitOrAProxyErrorIsNotTheEndOfTheSession() = runTest {
        for (status in listOf(HttpStatusCode.TooManyRequests, HttpStatusCode.NotFound, HttpStatusCode.Forbidden)) {
            val refresher = refresherAnswering(status, "")

            assertFailsWith<Exception>(status.toString()) { refresher.refresh("r1") }
        }
    }
}
