package ai.freeapp.bricktown.api

import ai.freeapp.bricktown.auth.AuthResponse
import ai.freeapp.bricktown.auth.ChangePasswordRequest
import ai.freeapp.bricktown.auth.LoginRequest
import ai.freeapp.bricktown.auth.RegisterRequest
import ai.freeapp.bricktown.auth.User
import ai.freeapp.bricktown.core.auth.HttpTokenRefresher
import ai.freeapp.bricktown.core.auth.RefreshRequest
import ai.freeapp.bricktown.core.auth.TokenRefresher
import ai.freeapp.bricktown.core.auth.TokenStore
import ai.freeapp.bricktown.core.auth.installTokenRefresh
import ai.freeapp.bricktown.core.config.NetworkConfig
import ai.freeapp.bricktown.core.data.getEngine
import ai.freeapp.bricktown.core.sync.ServerReachability
import ai.freeapp.bricktown.domain.BillSkip
import ai.freeapp.bricktown.domain.Category
import ai.freeapp.bricktown.domain.Expense
import ai.freeapp.bricktown.domain.Invite
import ai.freeapp.bricktown.domain.Member
import ai.freeapp.bricktown.domain.PdfImportResult
import ai.freeapp.bricktown.domain.TownSettings
import ai.freeapp.bricktown.domain.TownSnapshot
import ai.freeapp.bricktown.wire.AcceptInvite
import ai.freeapp.bricktown.wire.CategoryOrder
import ai.freeapp.bricktown.wire.CreateTownRequest
import ai.freeapp.bricktown.wire.InvitePreview
import ai.freeapp.bricktown.wire.InviteRequest
import ai.freeapp.bricktown.wire.MemberUpdate
import ai.freeapp.bricktown.wire.SettleRequest
import kotlinx.datetime.LocalDate
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A non-2xx reply. [serverMessage] is the server's `{"error": ...}` sentence,
 * already written for a person to read; `toErrorState()` carries it into
 * `ErrorState.Validation`.
 */
class ApiException(response: HttpResponse, val serverMessage: String?) :
    ResponseException(response, serverMessage.orEmpty())

/** The server's error body (`server/.../core/Errors.kt`). */
@Serializable
private data class ErrorResponse(val error: String)

/**
 * Single entry-point for talking to the Ktor backend.
 *
 * EXTENSION POINT: add new endpoint methods here when adding new entities.
 * Keep one repository per domain rather than overloading this class with
 * unrelated business logic.
 */
class ApiClient(
    private val httpClient: HttpClient = defaultClient(),
    private val baseUrl: String = NetworkConfig.BASE_URL,
) {
    // Accounts: the contract is section 7 of the store-ready spec and
    // server/.../auth/AuthRoutes.kt. The 204 routes return Unit and never read
    // a body.

    suspend fun register(request: RegisterRequest): AuthResponse =
        httpClient.post("$baseUrl/auth/register") { json(request) }.body()

    suspend fun login(request: LoginRequest): AuthResponse =
        httpClient.post("$baseUrl/auth/login") { json(request) }.body()

    /** Revokes [refreshToken] on the server. Always 204, even for a token it no longer knows. */
    suspend fun logout(refreshToken: String) {
        httpClient.post("$baseUrl/auth/logout") { json(RefreshRequest(refreshToken)) }
    }

    suspend fun me(): User = httpClient.get("$baseUrl/me").body()

    /** Keeps this session and signs out every other one. A wrong current password is a 400. */
    suspend fun changePassword(request: ChangePasswordRequest) {
        httpClient.post("$baseUrl/me/password") { json(request) }
    }

    /** Hard delete: the account and every row that belongs to it. */
    suspend fun deleteAccount() {
        httpClient.delete("$baseUrl/me")
    }

    // The town: section 5 of the design doc and server/.../town/TownRoutes.kt.
    // Bodies are always @Serializable types, never a JsonObject: `json(body)`
    // would fail on one at runtime.

    /** The caller's whole town. A 404 means she has none (or it was deleted). */
    suspend fun getTown(): TownSnapshot = httpClient.get("$baseUrl/town").body()

    /** The owner's switches for this user: `GET /flags`, a name to on or off. */
    suspend fun getFlags(): Map<String, Boolean> = httpClient.get("$baseUrl/flags").body()

    suspend fun createTown(request: CreateTownRequest): TownSnapshot =
        httpClient.post("$baseUrl/town") { json(request) }.body()

    suspend fun deleteTown() {
        httpClient.delete("$baseUrl/town")
    }

    suspend fun leaveTown() {
        httpClient.post("$baseUrl/town/leave")
    }

    suspend fun putSettings(settings: TownSettings): TownSettings =
        httpClient.put("$baseUrl/town/settings") { json(settings) }.body()

    suspend fun putCategory(category: Category): Category =
        httpClient.put("$baseUrl/town/categories/${category.id}") { json(category) }.body()

    suspend fun deleteCategory(id: String, mergeInto: String?) {
        httpClient.delete("$baseUrl/town/categories/$id") { if (mergeInto != null) parameter("mergeInto", mergeInto) }
    }

    suspend fun reorderCategories(order: CategoryOrder) {
        httpClient.put("$baseUrl/town/categories/order") { json(order) }
    }

    suspend fun putExpense(expense: Expense): Expense =
        httpClient.put("$baseUrl/town/expenses/${expense.id}") { json(expense) }.body()

    suspend fun deleteExpense(id: String) {
        httpClient.delete("$baseUrl/town/expenses/$id")
    }

    suspend fun putGoldMark(date: LocalDate) {
        httpClient.put("$baseUrl/town/gold/$date")
    }

    suspend fun deleteGoldMark(date: LocalDate) {
        httpClient.delete("$baseUrl/town/gold/$date")
    }

    suspend fun putBillSkip(skip: BillSkip) {
        httpClient.put("$baseUrl/town/bill-skips") { json(skip) }
    }

    suspend fun settle(request: SettleRequest): TownSnapshot =
        httpClient.post("$baseUrl/town/settle") { json(request) }.body()

    suspend fun updateMember(userId: String, change: MemberUpdate): Member =
        httpClient.put("$baseUrl/town/members/$userId") { json(change) }.body()

    suspend fun createInvite(request: InviteRequest): Invite =
        httpClient.post("$baseUrl/town/invites") { json(request) }.body()

    suspend fun revokeInvite(code: String) {
        httpClient.delete("$baseUrl/town/invites/$code")
    }

    suspend fun previewInvite(code: String): InvitePreview =
        httpClient.get("$baseUrl/invites/$code").body()

    suspend fun acceptInvite(code: String, body: AcceptInvite): TownSnapshot =
        httpClient.post("$baseUrl/invites/$code/accept") { json(body) }.body()

    /** The raw PDF goes up, the server reads it in memory and answers rows or a reason; nothing is kept there. */
    suspend fun readPdfStatement(bytes: ByteArray): PdfImportResult =
        httpClient.post("$baseUrl/town/import/pdf") {
            contentType(ContentType.Application.Pdf)
            setBody(bytes)
        }.body()

    private fun HttpRequestBuilder.json(body: Any) {
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        /**
         * EXTENSION POINT: pass a [TokenStore] once the API needs auth. The
         * client then sends the stored bearer token on every request and, on a
         * 401, refreshes it once and retries. With no store the client behaves
         * exactly as an unauthenticated one.
         *
         * The refresh interceptor is installed after the client is built, and
         * so after the response validator, on purpose: HttpSend runs later
         * interceptors closer to the network, so a 401 reaches the refresh
         * logic before the validator turns it into an [ApiException].
         * `TokenRefreshTest` holds that order.
         *
         * Pass a [ServerReachability] and every 2xx tells the outbox drainer
         * that the server answers again, so queued writes go right away.
         */
        fun defaultClient(
            tokens: TokenStore? = null,
            refresher: TokenRefresher? = null,
            engine: HttpClientEngine = getEngine(),
            reachability: ServerReachability? = null,
        ): HttpClient = HttpClient(engine) {
            install(ContentNegotiation) { json(json) }
            install(HttpTimeout) {
                connectTimeoutMillis = 10_000
                requestTimeoutMillis = 20_000
            }
            // INFO logs the request line and the status only: a body would put
            // passwords in logcat, and the bearer token is masked regardless.
            install(Logging) {
                level = LogLevel.INFO
                sanitizeHeader { it == HttpHeaders.Authorization }
            }
            HttpResponseValidator {
                validateResponse { response ->
                    if (response.status.isSuccess()) {
                        reachability?.markAnswered()
                        return@validateResponse
                    }
                    val message = runCatching {
                        json.decodeFromString<ErrorResponse>(response.bodyAsText()).error
                    }.getOrNull()
                    throw ApiException(response, message)
                }
            }
        }.apply {
            if (tokens != null) {
                installTokenRefresh(tokens, refresher ?: HttpTokenRefresher())
            }
        }
    }
}
