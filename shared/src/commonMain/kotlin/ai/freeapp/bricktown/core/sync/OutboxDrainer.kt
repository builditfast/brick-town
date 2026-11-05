package ai.freeapp.bricktown.core.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Sends queued writes with no user action. It wakes on:
 *
 * - start (the app process starting, on Android also for the boot receiver)
 * - a write being queued ([Outbox.pendingCount] above zero)
 * - the network coming back ([online], from [ConnectivityMonitor])
 * - any request answering ([serverAnswered], from [ServerReachability])
 * - sign-in ([signedIn] turning true)
 *
 * While a write still cannot go, it tries again after [backoff], capped at the
 * last value. The timer is what delivers when the server was down and the
 * network never changed: no callback fires for that.
 *
 * Started once by `startBackgroundWork`; runs for the life of the process.
 */
class OutboxDrainer(
    private val outbox: Outbox,
    private val online: Flow<Boolean>,
    private val serverAnswered: Flow<Unit>,
    private val signedIn: StateFlow<Boolean>,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val backoff: List<Duration> = DEFAULT_BACKOFF,
) {
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            // Conflated: a burst of reasons is one attempt, and none is lost.
            val wake = Channel<Unit>(Channel.CONFLATED)
            wake.trySend(Unit)
            launch { outbox.pendingCount.collect { if (it > 0) wake.trySend(Unit) } }
            launch { online.collect { if (it) wake.trySend(Unit) } }
            launch { serverAnswered.collect { wake.trySend(Unit) } }
            launch { signedIn.collect { if (it) wake.trySend(Unit) } }

            var failures = 0
            while (true) {
                wake.receive()
                // Nothing goes out without a session; a sign-in wakes this again.
                while (signedIn.value && outbox.drain() == DrainResult.Retry) {
                    val pause = backoff[minOf(failures, backoff.lastIndex)]
                    failures++
                    // A wake-up (network back, a request that worked) cuts the pause short.
                    withTimeoutOrNull(pause) { wake.receive() }
                }
                failures = 0
            }
        }
    }

    companion object {
        val DEFAULT_BACKOFF: List<Duration> = listOf(2.seconds, 5.seconds, 10.seconds, 20.seconds, 30.seconds)
    }
}
