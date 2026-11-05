package ai.freeapp.bricktown.core.auth

import ai.freeapp.bricktown.core.config.NetworkConfig
import ai.freeapp.bricktown.core.data.getEngine
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.plugin
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Body of the refresh and logout calls. */
@Serializable
data class RefreshRequest(val refreshToken: String)

/** Exchanges a refresh token for a new pair, or null if the session is over. */
interface TokenRefresher {
    /**
     * Null only when the server refused the token: the session is over.
     * Anything else (no network, a timeout, a 5xx, a proxy's 429 or 404)
     * throws, so a phone that is merely offline or rate limited keeps its
     * session.
     */
    suspend fun refresh(refreshToken: String): AuthTokens?
}

/**
 * Calls the server's refresh endpoint, which rotates the pair: the token it
 * was given is revoked as soon as the new one is issued.
 *
 * It owns a bare HttpClient on purpose: the refresh call must not travel
 * through the interceptor that triggered it, or a dead refresh token produces
 * an endless 401 loop.
 *
 * EXTENSION POINT: point [endpoint] at your own route, or replace this class
 * entirely if refreshing needs different credentials.
 */
class HttpTokenRefresher(
    private val endpoint: String = "${NetworkConfig.BASE_URL}/auth/refresh",
    private val client: HttpClient = HttpClient(getEngine()) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        // The call holds the refresh lock and cannot be cancelled once sent,
        // so every request that met a 401 waits at most this long behind it.
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 15_000
        }
    },
) : TokenRefresher {

    override suspend fun refresh(refreshToken: String): AuthTokens? {
        val response = client.post(endpoint) {
            contentType(ContentType.Application.Json)
            setBody(RefreshRequest(refreshToken))
        }
        return when {
            response.status.isSuccess() -> response.body<AuthTokens>()
            // What the refresh route answers a spent, revoked or garbled token.
            response.status == HttpStatusCode.Unauthorized || response.status == HttpStatusCode.BadRequest -> null
            else -> throw ResponseException(response, "Refresh failed: ${response.status}")
        }
    }
}

/**
 * Attaches the stored access token to every request and, on a 401, refreshes
 * the pair once and retries the original request once.
 *
 * A second 401 after a fresh token is a real 401: it is returned to the caller,
 * where the base ViewModel maps it to `ErrorState.Unauthorized`. When the
 * server refuses the refresh token, the store is cleared, which is how the app
 * learns the session ended (`ObservableTokenStore.signedIn`).
 *
 * Constraint: the retry replays the request builder, so a request with a
 * one-shot streaming body cannot be retried this way. Requests carrying an
 * upload stream should be sent on a client without this interceptor.
 */
fun HttpClient.installTokenRefresh(
    tokens: TokenStore,
    refresher: TokenRefresher,
) {
    val refreshLock = Mutex()

    plugin(HttpSend).intercept { request ->
        val stored = tokens.read()
        if (stored != null) {
            request.headers[HttpHeaders.Authorization] = "Bearer ${stored.accessToken}"
        }

        val call = execute(request)
        if (call.response.status != HttpStatusCode.Unauthorized) return@intercept call

        val refreshed = refreshLock.withLock {
            val current = tokens.read() ?: return@withLock null
            // Parallel requests can pile up behind one 401. If another of them
            // already refreshed while this one waited for the lock, reuse that
            // result rather than spending the refresh token a second time.
            if (stored != null && current.accessToken != stored.accessToken) return@withLock current

            // The server spends the old refresh token as soon as it answers. A
            // caller that leaves mid-refresh (a popped screen) must not throw
            // away the only pair that still works, so the exchange and the save
            // finish without it.
            withContext(NonCancellable) {
                val fresh = refresher.refresh(current.refreshToken)
                when {
                    // The store changed while the refresh was in flight: the user
                    // signed out (or out and in again). Saving now would quietly
                    // undo that, and clearing would end the newer session.
                    tokens.read() != current -> null
                    fresh == null -> {
                        tokens.clear()
                        null
                    }
                    else -> fresh.also(tokens::save)
                }
            }
        }

        if (refreshed == null) return@intercept call

        request.headers[HttpHeaders.Authorization] = "Bearer ${refreshed.accessToken}"
        execute(request)
    }
}
