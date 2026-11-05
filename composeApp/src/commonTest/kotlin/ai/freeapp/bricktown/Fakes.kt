package ai.freeapp.bricktown

import ai.freeapp.bricktown.api.ApiClient
import ai.freeapp.bricktown.api.ApiException
import ai.freeapp.bricktown.auth.AuthRepository
import ai.freeapp.bricktown.auth.User
import ai.freeapp.bricktown.core.auth.AuthTokens
import ai.freeapp.bricktown.core.auth.TokenStore
import ai.freeapp.bricktown.reminders.ReminderScheduler
import ai.freeapp.bricktown.reminders.ReminderStore
import ai.freeapp.bricktown.reminders.ReminderTime
import ai.freeapp.bricktown.town.JoinCodeStore
import ai.freeapp.bricktown.town.SyncStatus
import ai.freeapp.bricktown.town.TownLoader
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertFailsWith

val ana = User(id = "u1", email = "ana@example.com", name = "Ana")

/**
 * ViewModels run on [dispatcher] (passed to their constructor) and their
 * viewModelScope runs on Main, so both are this one test dispatcher, which
 * shares runTest's scheduler: after `onX()`, `advanceUntilIdle()` has run
 * everything it launched.
 *
 * Not UnconfinedTestDispatcher: building a real [ApiException] goes through
 * Ktor's own threads, and an unconfined test resumes there inside an event
 * loop that queues the ViewModel's launch behind the assertion.
 */
abstract class ViewModelTest {
    protected val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setMainDispatcher() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun resetMainDispatcher() = Dispatchers.resetMain()
}

/**
 * A real [ApiException], produced by the real client and validator, the way
 * the server answers: a status and an `{"error": ...}` body.
 */
suspend fun apiError(status: HttpStatusCode, message: String? = null): ApiException {
    val body = message?.let { """{"error":"$it"}""" }.orEmpty()
    val engine = MockEngine { respond(body, status, headersOf(HttpHeaders.ContentType, "application/json")) }
    val api = ApiClient(ApiClient.defaultClient(engine = engine), baseUrl = "http://test")
    return assertFailsWith<ApiException> { api.me() }
}

/** Records every call. Set [failure] to make the next calls but sign-out throw, [gate] to hold them mid-flight. */
class FakeAuthRepository(user: User? = null) : AuthRepository {
    override val isSignedIn = MutableStateFlow(user != null)
    override val currentUser = MutableStateFlow(user)

    val calls = mutableListOf<String>()
    var failure: Throwable? = null
    var gate: CompletableDeferred<Unit>? = null
    var userFromServer: User = ana

    private suspend fun step(call: String) {
        calls += call
        gate?.await()
        failure?.let { throw it }
    }

    private fun startSession(user: User): User {
        currentUser.value = user
        isSignedIn.value = true
        return user
    }

    private fun endSession() {
        currentUser.value = null
        isSignedIn.value = false
    }

    override suspend fun signIn(email: String, password: String): User {
        step("signIn $email")
        return startSession(userFromServer.copy(email = email))
    }

    override suspend fun createAccount(name: String, email: String, password: String): User {
        step("createAccount $name $email")
        return startSession(User("u2", email, name))
    }

    override suspend fun refreshUser(): User {
        step("refreshUser")
        currentUser.value = userFromServer
        return userFromServer
    }

    override suspend fun changePassword(currentPassword: String, newPassword: String) {
        step("changePassword")
    }

    // The real one never fails (see AuthRepository.signOut), so [failure] does not reach it.
    override suspend fun signOut() {
        calls += "signOut"
        gate?.await()
        endSession()
    }

    override suspend fun deleteAccount() {
        step("deleteAccount")
        endSession()
    }
}

/** What the platform would have booked: the time, or null once cancelled. */
class RecordingReminderScheduler : ReminderScheduler {
    var booked: ReminderTime? = null

    override fun scheduleDaily(time: ReminderTime) {
        booked = time
    }

    override fun cancelAll() {
        booked = null
    }
}

class MemoryReminderStore : ReminderStore {
    private var saved: ReminderTime? = null
    override fun read(): ReminderTime? = saved
    override fun write(time: ReminderTime?) {
        saved = time
    }
}

class MemoryTokenStore : TokenStore {
    private var tokens: AuthTokens? = null
    override fun read(): AuthTokens? = tokens
    override fun save(tokens: AuthTokens) {
        this.tokens = tokens
    }
    override fun clear() {
        tokens = null
    }
}

/**
 * The town as the server would answer: [refresh] moves [status] to
 * [answer], or throws [failure] and leaves it where it was.
 */
class FakeTownLoader(
    initial: SyncStatus = SyncStatus.Loading,
    var answer: SyncStatus = SyncStatus.Ready,
) : TownLoader {
    override val status = MutableStateFlow(initial)
    var failure: Throwable? = null
    var refreshes = 0
        private set

    override suspend fun refresh() {
        refreshes++
        failure?.let { throw it }
        status.value = answer
    }
}

class MemoryJoinCodeStore(private var code: String? = null) : JoinCodeStore {
    override fun read(): String? = code
    override fun write(code: String?) {
        this.code = code
    }
}
