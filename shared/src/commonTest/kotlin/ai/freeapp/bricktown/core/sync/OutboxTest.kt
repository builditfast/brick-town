package ai.freeapp.bricktown.core.sync

import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OutboxTest {

    private val store = InMemoryOutboxStore()
    private val tokens = signedInTokens()
    private val sender = FakeSender()
    private val outbox = Outbox(store, tokens) { listOf(sender) }

    @Test
    fun anOnlineWriteIsSentAtOnceAndLeavesNothingQueued() = runTest {
        assertEquals(Delivery.Sent, outbox.sendOrQueue("notes.create", "a"))

        assertEquals(listOf("a"), sender.sent)
        assertEquals(0, store.count())
    }

    @Test
    fun aNetworkErrorQueuesTheWriteInsteadOfFailing() = runTest {
        sender.failure = IOException("no route to host")

        assertEquals(Delivery.Queued, outbox.sendOrQueue("notes.create", "a"))

        assertEquals(1, store.count())
        assertEquals(emptyList(), sender.undone)
    }

    @Test
    fun serverTroubleIsRetriedAndARefusalIsNot() = runTest {
        for (status in listOf(401, 408, 429, 500, 502, 503)) {
            assertTrue(httpError(HttpStatusCode.fromValue(status)).isWorthRetrying(), "$status")
        }
        for (status in listOf(400, 403, 404, 409, 422)) {
            assertFalse(httpError(HttpStatusCode.fromValue(status)).isWorthRetrying(), "$status")
        }
        assertTrue(IOException("connection refused").isWorthRetrying())
    }

    @Test
    fun aDrainSendsEveryQueuedWriteOnceInOrder() = runTest {
        sender.failure = IOException("offline")
        outbox.sendOrQueue("notes.create", "a")
        outbox.sendOrQueue("notes.create", "b")
        sender.failure = null

        assertEquals(DrainResult.Done, outbox.drain())
        assertEquals(DrainResult.Done, outbox.drain())

        assertEquals(listOf("a", "b"), sender.sent)
        assertEquals(0, store.count())
    }

    @Test
    fun aNewWriteWaitsBehindOlderOnesInsteadOfJumpingTheQueue() = runTest {
        sender.failure = IOException("offline")
        outbox.sendOrQueue("notes.create", "a")
        sender.failure = null

        assertEquals(Delivery.Queued, outbox.sendOrQueue("notes.create", "b"))
        assertEquals(emptyList(), sender.sent)

        outbox.drain()
        assertEquals(listOf("a", "b"), sender.sent)
    }

    @Test
    fun twoDrainsAtOnceNeverSendTheSameWriteTwice() = runTest {
        sender.failure = IOException("offline")
        outbox.sendOrQueue("notes.create", "a")
        sender.failure = null
        sender.gate = CompletableDeferred()

        val first = async { outbox.drain() }
        val second = async { outbox.drain() }
        runCurrent()
        sender.gate!!.complete(Unit)

        assertEquals(DrainResult.Done, first.await())
        assertEquals(DrainResult.Done, second.await())
        assertEquals(listOf("a"), sender.sent)
    }

    @Test
    fun aQueuedWriteTheServerRefusesIsDroppedAndSurfacedOnce() = runTest {
        sender.failure = IOException("offline")
        outbox.sendOrQueue("notes.create", "blank")
        outbox.sendOrQueue("notes.create", "b")
        sender.failure = httpError(HttpStatusCode.BadRequest)

        assertEquals(DrainResult.Done, outbox.drain())

        assertEquals(0, store.count())
        assertEquals(listOf("blank", "b"), sender.undone)
        val rejected = outbox.rejected.value
        assertEquals(2, rejected.size)
        rejected.forEach(outbox::acknowledge)
        assertEquals(emptyList(), outbox.rejected.value)
    }

    @Test
    fun aRefusalWhileTheUserWaitsIsThrownToTheScreenAndUndone() = runTest {
        sender.failure = httpError(HttpStatusCode.BadRequest)

        assertFailsWith<Exception> { outbox.sendOrQueue("notes.create", "blank") }

        assertEquals(0, store.count())
        assertEquals(listOf("blank"), sender.undone)
        assertEquals(emptyList(), outbox.rejected.value)
    }

    @Test
    fun aRetryStopsTheDrainAndKeepsTheOrder() = runTest {
        sender.failure = IOException("offline")
        outbox.sendOrQueue("notes.create", "a")
        outbox.sendOrQueue("notes.create", "b")

        assertEquals(DrainResult.Retry, outbox.drain())
        assertEquals(2, store.count())
        assertEquals("a", store.oldest()?.payload)
    }

    @Test
    fun sessionEndEmptiesTheQueue() = runTest {
        sender.failure = IOException("offline")
        outbox.sendOrQueue("notes.create", "a")

        tokens.clear()

        assertEquals(0, store.count())
        sender.failure = null
        outbox.drain()
        assertEquals(emptyList(), sender.sent)
    }

    @Test
    fun aWriteWipedWhileItWasSendingIsNotSurfacedToTheNextAccount() = runTest {
        sender.failure = IOException("offline")
        outbox.sendOrQueue("notes.create", "a")
        sender.failure = httpError(HttpStatusCode.BadRequest)
        sender.gate = CompletableDeferred()

        val draining = async { outbox.drain() }
        runCurrent()
        tokens.clear()
        sender.gate!!.complete(Unit)
        draining.await()

        assertEquals(emptyList(), outbox.rejected.value)
        assertEquals(emptyList(), sender.undone)
    }

    @Test
    fun aKindNobodySendsFailsFastAtTheCaller() = runTest {
        val failure = assertFailsWith<IllegalStateException> { outbox.sendOrQueue("books.create", "x") }
        assertTrue("bind OutboxSender" in failure.message.orEmpty())
        assertEquals(0, store.count())
    }
}
