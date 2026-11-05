package ai.freeapp.bricktown.core.sync

import ai.freeapp.bricktown.api.ApiClient
import ai.freeapp.bricktown.api.ApiException
import ai.freeapp.bricktown.core.auth.AuthTokens
import ai.freeapp.bricktown.core.auth.ObservableTokenStore
import ai.freeapp.bricktown.core.auth.TokenStore
import ai.freeapp.bricktown.db.OutboxRow
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlin.test.assertFailsWith

class InMemoryTokenStore : TokenStore {
    private var tokens: AuthTokens? = null
    override fun read(): AuthTokens? = tokens
    override fun save(tokens: AuthTokens) {
        this.tokens = tokens
    }
    override fun clear() {
        tokens = null
    }
}

/** A token store holding a session, in memory. `clear()` runs the registered wipes like the real one. */
fun signedInTokens(): ObservableTokenStore =
    ObservableTokenStore(InMemoryTokenStore()).apply { save(AuthTokens("access", "refresh")) }

/** The outbox table, in memory. */
class InMemoryOutboxStore : OutboxStore {
    private val rows = MutableStateFlow<List<OutboxRow>>(emptyList())
    private var nextId = 1L

    override fun observeCount(): Flow<Long> = rows.map { it.size.toLong() }
    override fun count(): Long = rows.value.size.toLong()

    override fun add(kind: String, payload: String): Long {
        val id = nextId++
        rows.value = rows.value + OutboxRow(id, kind, payload)
        return id
    }

    override fun oldest(): OutboxRow? = rows.value.firstOrNull()

    override fun remove(id: Long): Boolean {
        val before = rows.value
        rows.value = before.filterNot { it.id == id }
        return rows.value.size < before.size
    }

    override fun clear() {
        rows.value = emptyList()
    }
}

/**
 * Records what reaches the server. [failure] makes every send throw it;
 * [gate] holds a send mid-flight until completed.
 */
class FakeSender(override val outboxKinds: Set<String> = setOf("notes.create")) : OutboxSender {
    val sent = mutableListOf<String>()
    val undone = mutableListOf<String>()
    var failure: Throwable? = null
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun send(kind: String, payload: String) {
        gate?.await()
        failure?.let { throw it }
        sent += payload
    }

    override fun undo(kind: String, payload: String) {
        undone += payload
    }
}

/** A real [ApiException] from the real client and validator, as the server answers. */
suspend fun httpError(status: HttpStatusCode): ApiException {
    val engine = MockEngine { respond("""{"error":"refused"}""", status, headersOf(HttpHeaders.ContentType, "application/json")) }
    val api = ApiClient(ApiClient.defaultClient(engine = engine), baseUrl = "http://test")
    return assertFailsWith<ApiException> { api.me() }
}
