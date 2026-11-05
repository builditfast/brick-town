package ai.freeapp.bricktown.core.sync

import ai.freeapp.bricktown.core.auth.ObservableTokenStore
import ai.freeapp.bricktown.core.error.ErrorState
import ai.freeapp.bricktown.core.error.toErrorState
import ai.freeapp.bricktown.db.OutboxRow
import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException

/**
 * Sends the writes of one or more kinds. A repository whose writes must
 * survive a lost connection implements it, and is bound to it in Koin so the
 * outbox finds it:
 *
 *     single { BooksRepository(get(), get(), get()) } bind OutboxSender::class
 *
 * EXTENSION POINT: `TownRepository` is the reference sender. Copy how it
 * shows a write locally, then `sendOrQueue`s it, and its `send` and `undo`.
 */
interface OutboxSender {
    /** The kinds this sender owns, like "expense.put". Unique across the app. */
    val outboxKinds: Set<String>

    /** Sends one write and applies the server's answer to the local cache. Throws like any API call. */
    suspend fun send(kind: String, payload: String)

    /** The server refused the write for good: remove what the screen showed for it. */
    fun undo(kind: String, payload: String)
}

/** What [Outbox.sendOrQueue] did with a write. Neither is an error. */
enum class Delivery { Sent, Queued }

enum class DrainResult {
    /** Nothing is left to send. */
    Done,

    /** The oldest write could not be sent yet; it stays, and so does everything behind it. */
    Retry,
}

/** A queued write the server refused for good. Shown once, then [Outbox.acknowledge]d. */
data class RejectedWrite(val id: Long, val kind: String, val error: ErrorState)

/**
 * Writes the server has not confirmed yet, sent oldest first.
 *
 * A repository calls [sendOrQueue] right after it has shown the change
 * locally. Online, that is the one request it would have made anyway. With no
 * connection the write stays in the table and [OutboxDrainer] sends it later,
 * with nobody tapping anything, so the screen shows a quiet pending state and
 * never "No connection".
 *
 * Every send, inline or drained, holds one lock, so two triggers firing at
 * once never send the same row twice. A row is removed only after its send
 * returned. A crash in between sends it again on the next start, so a sender
 * must tolerate a repeat (see the design doc, outstanding issue 1).
 *
 * The queue belongs to the session that filled it: [clear] is registered on
 * the token store and runs on every session end and start.
 */
class Outbox(
    private val store: OutboxStore,
    tokens: ObservableTokenStore,
    private val senders: () -> List<OutboxSender>,
) {
    private val sending = Mutex()
    private val _rejected = MutableStateFlow<List<RejectedWrite>>(emptyList())

    /** How many writes wait. The screen's pending line, and one of the drainer's wake-ups. */
    val pendingCount: Flow<Long> = store.observeCount()

    /** Refusals not yet shown. Held until a screen calls [acknowledge], so none is lost while no screen listens. */
    val rejected: StateFlow<List<RejectedWrite>> = _rejected.asStateFlow()

    init {
        tokens.doOnClear(::clear)
    }

    /**
     * Stores the write, then tries it once if nothing older waits. A network
     * failure (or a 401, 408, 429, 5xx) leaves it queued and returns
     * [Delivery.Queued]. Any other failure, such as a 400 for a blank name,
     * calls the sender's `undo` and throws to the caller, who is still on the
     * screen and shows it like any other error.
     */
    suspend fun sendOrQueue(kind: String, payload: String): Delivery {
        val sender = senderFor(kind)
            ?: error("No OutboxSender owns \"$kind\": bind its repository with `bind OutboxSender::class` in SharedModule.")
        val waiting = store.count()
        val id = store.add(kind, payload)
        // Older writes go first, and a drain that holds the lock reaches this
        // row before it lets go. A cancelled caller leaves the row for the drainer.
        if (waiting > 0 || !sending.tryLock()) return Delivery.Queued
        try {
            return when (val attempt = attempt(OutboxRow(id, kind, payload))) {
                Attempt.Sent -> {
                    store.remove(id)
                    Delivery.Sent
                }
                Attempt.Retry -> Delivery.Queued
                is Attempt.Refused -> {
                    if (store.remove(id)) sender.undo(kind, payload)
                    throw attempt.failure
                }
            }
        } finally {
            sending.unlock()
        }
    }

    /** Sends every queued write in order. Called by [OutboxDrainer]; nothing in the UI calls it. */
    suspend fun drain(): DrainResult = sending.withLock { drainHoldingLock() }

    fun acknowledge(write: RejectedWrite) {
        _rejected.update { it - write }
    }

    /** Forgets every queued write and every unshown refusal. */
    fun clear() {
        store.clear()
        _rejected.value = emptyList()
    }

    private suspend fun drainHoldingLock(): DrainResult {
        while (true) {
            val row = store.oldest() ?: return DrainResult.Done
            when (val attempt = attempt(row)) {
                Attempt.Sent -> store.remove(row.id)
                Attempt.Retry -> return DrainResult.Retry
                // A row that vanished during its send was wiped with the
                // session: the next account must not hear about it.
                is Attempt.Refused -> if (store.remove(row.id)) {
                    senderFor(row.kind)?.undo(row.kind, row.payload)
                    _rejected.update { it + RejectedWrite(row.id, row.kind, attempt.failure.toErrorState()) }
                }
            }
        }
    }

    private suspend fun attempt(row: OutboxRow): Attempt {
        // A kind nobody sends any more (removed in an app update) is refused,
        // not retried: it would block every write behind it forever.
        val sender = senderFor(row.kind)
            ?: return Attempt.Refused(IllegalStateException("No OutboxSender owns \"${row.kind}\""))
        return try {
            sender.send(row.kind, row.payload)
            Attempt.Sent
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            if (failure.isWorthRetrying()) Attempt.Retry else Attempt.Refused(failure)
        }
    }

    private fun senderFor(kind: String): OutboxSender? = senders().firstOrNull { kind in it.outboxKinds }

    private sealed interface Attempt {
        data object Sent : Attempt
        data object Retry : Attempt
        data class Refused(val failure: Throwable) : Attempt
    }
}

/**
 * Whether a failed send should stay queued and go again. The 401 is here
 * because the refresh interceptor has already run: either the session ended,
 * and its wipe emptied the outbox, or the next attempt carries a fresh token.
 */
internal fun Throwable.isWorthRetrying(): Boolean {
    val status = (this as? ResponseException)?.response?.status?.value
    if (status != null) return status == 401 || status == 408 || status == 429 || status >= 500
    val error = toErrorState()
    return error == ErrorState.NoInternet || error == ErrorState.RequestTimeout
}
