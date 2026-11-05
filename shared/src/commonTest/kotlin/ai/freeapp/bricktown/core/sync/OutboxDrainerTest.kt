package ai.freeapp.bricktown.core.sync

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

/**
 * Nothing here calls `drain()`: every write reaches the sender only because
 * the drainer woke up by itself. Time is virtual, so "no timer could have
 * fired yet" is exact.
 */
class OutboxDrainerTest {

    private val store = InMemoryOutboxStore()
    private val tokens = signedInTokens()
    private val sender = FakeSender()
    private val outbox = Outbox(store, tokens) { listOf(sender) }
    private val online = MutableStateFlow(false)
    private val answered = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private fun TestScope.startDrainer(backoff: List<Duration> = listOf(1.hours)) =
        OutboxDrainer(outbox, online, answered, tokens.signedIn, scope = backgroundScope, backoff = backoff).start()

    private suspend fun queueWhileOffline(payload: String) {
        sender.failure = IOException("offline")
        assertEquals(Delivery.Queued, outbox.sendOrQueue("notes.create", payload))
    }

    @Test
    fun theNetworkComingBackSendsTheQueueWithNoUserAction() = runTest {
        queueWhileOffline("a")
        startDrainer()
        runCurrent()
        assertEquals(emptyList(), sender.sent)

        sender.failure = null
        online.value = true
        runCurrent()

        // The only backoff is an hour and no virtual time has passed: the reconnect sent it.
        assertEquals(listOf("a"), sender.sent)
        assertEquals(0, store.count())
    }

    @Test
    fun aServerThatWasDownIsRetriedOnATimerWhileTheNetworkNeverChanged() = runTest {
        online.value = true
        queueWhileOffline("a")
        startDrainer(backoff = listOf(2.seconds))
        runCurrent()
        assertEquals(emptyList(), sender.sent)

        sender.failure = null
        advanceTimeBy(2.seconds + 1.seconds)

        assertEquals(listOf("a"), sender.sent)
    }

    @Test
    fun anyRequestThatWorksWakesTheQueue() = runTest {
        online.value = true
        queueWhileOffline("a")
        startDrainer()
        runCurrent()

        sender.failure = null
        answered.tryEmit(Unit)
        runCurrent()

        assertEquals(listOf("a"), sender.sent)
    }

    @Test
    fun appStartSendsWhatTheLastRunLeftQueued() = runTest {
        store.add("notes.create", "left over")

        startDrainer()
        runCurrent()

        assertEquals(listOf("left over"), sender.sent)
    }

    @Test
    fun nothingIsSentWithoutASession() = runTest {
        store.add("notes.create", "a")
        tokens.clear()
        store.add("notes.create", "b")

        startDrainer()
        online.value = true
        runCurrent()

        assertEquals(emptyList(), sender.sent)
        assertEquals(1, store.count())
    }

    @Test
    fun eachWriteIsSentOnceWhateverWakesTheDrainer() = runTest {
        online.value = true
        queueWhileOffline("a")
        queueWhileOffline("b")
        startDrainer(backoff = listOf(1.seconds))
        runCurrent()

        sender.failure = null
        online.value = false
        online.value = true
        answered.tryEmit(Unit)
        advanceTimeBy(10.seconds)

        assertEquals(listOf("a", "b"), sender.sent)
    }
}
