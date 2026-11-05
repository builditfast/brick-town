package ai.freeapp.bricktown.town

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/** Where the phone stands with the server's copy of the town. */
enum class SyncStatus {
    /** Nothing cached yet and no answer from the server. */
    Loading,

    /** The server says this account has no town: show onboarding or the join screen. */
    NoTown,

    /** A town is cached (and refreshed when the server can be reached). */
    Ready,

    /** The server no longer has the town this phone had: the owner deleted it. The tables are already empty. */
    TownDeleted,
}

/** The answer of a call that needs the server: a value, or the sentence to show. */
sealed interface TownResult<out T> {
    data class Ok<T>(val value: T) : TownResult<T>

    /** [message] is the server's own sentence when it sent one, so a screen shows it as it is. */
    data class Failed(val message: String, val status: Int? = null) : TownResult<Nothing>
}

/** One-shot things the screen reacts to once. */
sealed interface TownEvent {
    /**
     * A queued write was refused for good, and the phone went back to the
     * server's version. [message] is the toast: "Ana changed the town, so that
     * change was not saved."
     */
    data class WriteNotSaved(val kind: String, val message: String) : TownEvent
}

// Outbox payloads. Each holds what a send needs after a restart, because the
// outbox keeps only text.

@Serializable
internal data class IdPayload(val id: String)

@Serializable
internal data class DatePayload(val date: LocalDate)

/** [movedIds] are the bricks the merge moved on the phone, so an ack or an undo knows which rows it touched. */
@Serializable
internal data class CategoryDelete(val id: String, val mergeInto: String? = null, val movedIds: List<String> = emptyList())

@Serializable
internal data class MemberChange(
    val userId: String,
    val canEdit: Boolean? = null,
    val canManage: Boolean? = null,
    val colorIndex: Int? = null,
    val initial: String? = null,
)
