package ai.freeapp.bricktown.town

import ai.freeapp.bricktown.api.ApiClient
import ai.freeapp.bricktown.auth.AuthRepositoryImpl
import ai.freeapp.bricktown.core.sync.DrainResult
import ai.freeapp.bricktown.core.sync.Outbox
import ai.freeapp.bricktown.core.sync.SqlOutboxStore
import ai.freeapp.bricktown.core.sync.signedInTokens
import ai.freeapp.bricktown.db.AppDatabase
import ai.freeapp.bricktown.domain.Category
import ai.freeapp.bricktown.domain.Currency
import ai.freeapp.bricktown.domain.Expense
import ai.freeapp.bricktown.domain.Member
import ai.freeapp.bricktown.domain.MemberRole
import ai.freeapp.bricktown.domain.Town
import ai.freeapp.bricktown.domain.TownSettings
import ai.freeapp.bricktown.domain.TownSnapshot
import ai.freeapp.bricktown.wire.CreateTownRequest
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import kotlinx.coroutines.CoroutineScope
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.ConnectException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** On the JVM against the real schema and a fake server, because what the cache keeps is the point. */
class TownRepositoryTest {

    private val db = AppDatabase(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { AppDatabase.Schema.create(it) })
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private val json = Json

    private val now = Instant.parse("2026-11-04T10:00:00Z")
    private val settings = TownSettings(200_000, 1, Currency.EUR, true, "Europe/Bucharest")
    private val me = Member("me", "Emanuel Test", "E", 0, MemberRole.OWNER, true, true, now)
    private val ana = Member("ana", "Ana Pop", "A", 1, MemberRole.MEMBER, true, false, now)
    private val food = Category("food", "Food", "FOOD", 0, null, 0)
    private val brick = Expense("e1", "food", 1_000, LocalDate(2026, 11, 3), "Bread", paidBy = "me", createdAt = now)

    /** What the fake server holds and how it answers. */
    private var serverExpenses = listOf(brick)
    private var serverMembers = listOf(me, ana)
    private var serverInvites = emptyList<ai.freeapp.bricktown.domain.Invite>()
    private var serverDown = false
    private var townMissing = false
    private var putStatus = HttpStatusCode.OK
    private var townAnswerHeld: CompletableDeferred<Unit>? = null
    private val requests = mutableListOf<String>()

    private fun snapshot() = TownSnapshot(
        Town("t1", "me", "Emanuel's town", settings, now), "me", serverMembers, listOf(food),
        serverExpenses, emptyList(), emptyList(), serverInvites, now,
    )

    private val engine = MockEngine { request ->
        if (serverDown) throw ConnectException("Connection refused")
        val path = request.url.encodedPath
        requests += "${request.method.value} $path"
        if (path == "/town") townAnswerHeld?.await()
        when {
            request.method == HttpMethod.Get && path == "/town" ->
                if (townMissing) respond("""{"error":"No town"}""", HttpStatusCode.NotFound, jsonHeaders)
                else respond(json.encodeToString(snapshot()), HttpStatusCode.OK, jsonHeaders)
            request.method == HttpMethod.Put && path.startsWith("/town/expenses/") ->
                if (putStatus != HttpStatusCode.OK) {
                    respond("""{"error":"Only the owner can change that."}""", putStatus, jsonHeaders)
                } else {
                    val body = request.body.toByteArray().decodeToString()
                    val saved = json.decodeFromString(Expense.serializer(), body)
                    serverExpenses = serverExpenses.filterNot { it.id == saved.id } + saved
                    respond(body, HttpStatusCode.OK, jsonHeaders)
                }
            request.method == HttpMethod.Delete -> respond("", HttpStatusCode.NoContent)
            else -> respond("""{"error":"Not expected"}""", HttpStatusCode.InternalServerError, jsonHeaders)
        }
    }

    private val api = ApiClient(ApiClient.defaultClient(engine = engine), baseUrl = "http://test")
    private lateinit var repository: TownRepository
    private val tokens = signedInTokens()
    private val outbox = Outbox(SqlOutboxStore(db), tokens) { listOf(repository) }

    init {
        repository = TownRepository(api, db, outbox, tokens, Dispatchers.Unconfined)
    }

    private fun <T> run(block: suspend CoroutineScope.() -> T): T = runBlocking { withTimeout(20_000) { block() } }

    private suspend fun bricks() = repository.town.first()!!.expenses
    private fun tableCounts() = listOf(
        db.townQueries.selectTown().executeAsList().size,
        db.townQueries.selectMembers().executeAsList().size,
        db.townQueries.selectCategories().executeAsList().size,
        db.townQueries.selectExpenses().executeAsList().size,
    )

    @Test
    fun aBrickAddedOfflineIsVisibleSurvivesARefreshAndIsSentOnce() = run {
        repository.refresh()
        serverDown = true
        val added = Expense("e2", "food", 250, LocalDate(2026, 11, 4), "Coffee", paidBy = "me", createdAt = now)

        repository.putExpense(added)
        assertEquals(setOf("e1", "e2"), bricks().map { it.id }.toSet())

        // Back online, but the queued write has not been sent: a refresh must keep the local brick.
        serverDown = false
        repository.refresh()
        assertEquals(setOf("e1", "e2"), bricks().map { it.id }.toSet())
        assertEquals(0, requests.count { it.startsWith("PUT /town/expenses/e2") })

        assertEquals(DrainResult.Done, outbox.drain())
        assertEquals(1, requests.count { it == "PUT /town/expenses/e2" })
        assertEquals(setOf("e1", "e2"), bricks().map { it.id }.toSet())
        assertEquals(0L, db.townQueries.selectExpenseById("e2").executeAsOne().pending)
    }

    @Test
    fun anInviteTheServerUsedUpLeavesTheCacheWithTheNewMember() = run {
        serverMembers = listOf(me)
        serverInvites = listOf(ai.freeapp.bricktown.domain.Invite("ABCD-2345", now, Instant.parse("2026-11-11T10:00:00Z"), canEdit = true, canManage = false))
        repository.refresh()
        assertEquals(listOf("ABCD-2345"), repository.town.first()!!.invites.map { it.code })

        serverMembers = listOf(me, ana)
        serverInvites = emptyList()
        repository.refresh()
        val town = repository.town.first()!!
        assertEquals(2, town.members.size)
        assertTrue(town.invites.isEmpty())
    }

    @Test
    fun aPendingDeleteStaysHiddenThroughARefresh() = run {
        repository.refresh()
        serverDown = true
        repository.deleteExpense("e1")
        assertTrue(bricks().isEmpty())

        serverDown = false
        repository.refresh()
        assertTrue(bricks().isEmpty())
        assertEquals(1L, db.townQueries.selectExpenseById("e1").executeAsOne().deleted)
    }

    @Test
    fun aRefusedQueuedEditRestoresTheServersBrickAndSaysSo() = run {
        repository.refresh()
        serverDown = true
        repository.putExpense(brick.copy(amountCents = 9_999))
        assertEquals(9_999L, bricks().single().amountCents)

        serverDown = false
        putStatus = HttpStatusCode.Forbidden
        val event = async(start = CoroutineStart.UNDISPATCHED) { repository.events.first() }
        outbox.drain()

        val heard = assertIs<TownEvent.WriteNotSaved>(event.await())
        assertEquals("Ana changed the town, so that change was not saved.", heard.message)
        // The refresh after an undo runs on the repository's own scope.
        assertEquals(1_000L, repository.town.first { it!!.expenses.isNotEmpty() }!!.expenses.single().amountCents)
        assertEquals(0L, db.townQueries.selectExpenseById("e1").executeAsOne().pending)
    }

    @Test
    fun aMissingTownKeepsAPendingCreate() = run {
        serverDown = true
        repository.createTown(
            CreateTownRequest("t1", settings, listOf(food), null), "me", "Emanuel Test",
        )
        serverDown = false
        townMissing = true

        repository.refresh()

        assertNotNull(repository.town.first())
        assertEquals(SyncStatus.Ready, repository.status.value)
    }

    @Test
    fun aMissingTownThatTheServerOnceConfirmedIsWipedAndReported() = run {
        repository.refresh()
        assertEquals(SyncStatus.Ready, repository.status.value)
        townMissing = true

        repository.refresh()

        assertNull(repository.town.first())
        assertEquals(listOf(0, 0, 0, 0), tableCounts())
        assertEquals(SyncStatus.TownDeleted, repository.status.value)
    }

    @Test
    fun theNextAccountOnThePhoneDoesNotInheritTheLastOnesTown() = run {
        repository.refresh()
        assertEquals(SyncStatus.Ready, repository.status.value)

        AuthRepositoryImpl(api, tokens, db).signOut()

        assertEquals(SyncStatus.Loading, repository.status.value)
        townMissing = true
        repository.refresh()
        assertEquals(SyncStatus.NoTown, repository.status.value)
    }

    @Test
    fun aRefreshStillOutWhenTheSessionEndsLaysNothing() = run {
        val held = CompletableDeferred<Unit>()
        townAnswerHeld = held
        val refreshing = async { repository.refresh() }
        while (requests.none { it == "GET /town" }) yield()

        AuthRepositoryImpl(api, tokens, db).signOut()
        held.complete(Unit)
        refreshing.await()

        assertEquals(listOf(0, 0, 0, 0), tableCounts())
        assertEquals(SyncStatus.Loading, repository.status.value)
    }

    @Test
    fun aMissingTownWithNothingCachedMeansNoTown() = run {
        townMissing = true

        repository.refresh()

        assertEquals(SyncStatus.NoTown, repository.status.value)
        assertNull(repository.town.first())
    }

    @Test
    fun signingOutEmptiesEveryTownTable() = run {
        repository.refresh()
        db.townQueries.putGoldPending("2026-11-02", "me")
        db.townQueries.putBillSkipPending("2026-11-01", "rent")
        assertTrue(tableCounts().all { it > 0 })
        val tokens = signedInTokens()
        val auth = AuthRepositoryImpl(api, tokens, db)

        auth.signOut()

        assertEquals(listOf(0, 0, 0, 0), tableCounts())
        assertEquals(0, db.townQueries.selectGoldMarks().executeAsList().size)
        assertEquals(0, db.townQueries.selectBillSkips().executeAsList().size)
        assertEquals(0L, db.townQueries.countTownRows().executeAsOne())
    }
}
