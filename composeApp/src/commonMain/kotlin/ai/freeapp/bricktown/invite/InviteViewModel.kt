package ai.freeapp.bricktown.invite

import ai.freeapp.bricktown.core.config.NetworkConfig
import ai.freeapp.bricktown.core.ui.BaseViewModel
import ai.freeapp.bricktown.core.ui.toMessage
import ai.freeapp.bricktown.domain.Invite
import ai.freeapp.bricktown.domain.MemberRole
import ai.freeapp.bricktown.domain.TownSnapshot
import ai.freeapp.bricktown.settings.TownGateway
import ai.freeapp.bricktown.town.TownResult
import ai.freeapp.bricktown.wire.InviteRequest
import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.collectLatest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.days

/** The server refuses a third member (TownRules.MAX_MEMBERS), so the screen shows "full" at the same number. */
private const val MAX_MEMBERS = 2

@Immutable
data class InviteAvatar(val initial: String, val colorIndex: Int)

@Immutable
data class PendingInvite(val code: String, val age: String)

@Immutable
data class InviteUiState(
    val loaded: Boolean = false,
    val isOwner: Boolean = true,
    val full: Boolean = false,
    val members: List<InviteAvatar> = emptyList(),
    val canEdit: Boolean = true,
    val canManage: Boolean = false,
    val creating: Boolean = false,
    val link: String? = null,
    val expires: String = "",
    val showQr: Boolean = false,
    val pending: List<PendingInvite> = emptyList(),
    val message: String? = null,
)

sealed interface InviteEffect {
    data class Share(val text: String) : InviteEffect
    data class Copy(val text: String) : InviteEffect
}

/**
 * Invite (6g). The link always belongs to a live invite whose permissions match
 * the two toggles, so what the partner is promised is what the server stored.
 * Changing a toggle reuses a matching live invite or asks for a new one.
 */
class InviteViewModel(
    private val gateway: TownGateway,
    private val workDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val baseUrl: String = NetworkConfig.BASE_URL,
    private val refresh: (suspend () -> Unit)? = null,
) : BaseViewModel<InviteUiState, InviteEffect>(InviteUiState(), workDispatcher) {

    private var snapshot: TownSnapshot? = null

    // A failed create must not loop: the next snapshot would otherwise ask again at once.
    private var createFailed = false

    // One ask per permission pair: a snapshot that still lacks a match must not trigger an endless ask.
    private var requestedFor: Pair<Boolean, Boolean>? = null

    init {
        tryToCollect(
            flow = { gateway.town },
            onEach = { town ->
                snapshot = town
                if (town != null) render(town)
            },
            onError = { error -> updateState { it.copy(loaded = true, message = error.toMessage()) } },
        )
        refresh?.let(::watchForJoin)
    }

    /**
     * TownSync only ticks every 30 s. While an invite waits for its partner this screen asks
     * for the town itself every [WATCH_MILLIS], so the join shows without leaving it. The
     * loop lives only while something is pending, so it ends when the partner joins.
     */
    private fun watchForJoin(refresh: suspend () -> Unit) {
        viewModelScope.launch(workDispatcher) {
            state.map { it.isOwner && !it.full && it.pending.isNotEmpty() }
                .distinctUntilChanged()
                .collectLatest { waiting ->
                    while (waiting) {
                        delay(WATCH_MILLIS)
                        try {
                            refresh()
                        } catch (cancellation: CancellationException) {
                            throw cancellation
                        } catch (_: Exception) {
                            // Offline or a server hiccup: the next tick tries again.
                        }
                    }
                }
        }
    }

    private fun render(town: TownSnapshot) {
        val live = liveInvites(town)
        val current = state.value
        val match = live.filter { it.canEdit == current.canEdit && it.canManage == current.canManage }.maxByOrNull { it.createdAt }
        val isOwner = town.members.firstOrNull { it.userId == town.viewerId }?.role == MemberRole.OWNER
        val full = town.members.size >= MAX_MEMBERS
        updateState {
            it.copy(
                loaded = true,
                isOwner = isOwner,
                full = full,
                members = town.members.map { member -> InviteAvatar(member.initial, member.colorIndex) },
                link = match?.let { invite -> linkFor(invite.code) },
                expires = match?.let { invite -> expiresText(invite, town) }.orEmpty(),
                pending = live.sortedByDescending { invite -> invite.createdAt }.map { invite -> PendingInvite(invite.code, ageText(invite, town)) },
            )
        }
        if (isOwner && !full && match == null && !state.value.creating && !createFailed && requestedFor != (current.canEdit to current.canManage)) createInvite()
    }

    private fun liveInvites(town: TownSnapshot): List<Invite> = town.invites.filter { it.expiresAt > town.serverTime }

    private fun linkFor(code: String) = "$baseUrl/join/$code"

    private fun expiresText(invite: Invite, town: TownSnapshot): String {
        val days = (invite.expiresAt - town.serverTime).inWholeDays.toInt().coerceAtLeast(0)
        return when (days) {
            0 -> "expires today"
            1 -> "expires in 1 day"
            else -> "expires in $days days"
        }
    }

    private fun ageText(invite: Invite, town: TownSnapshot): String {
        val age = (town.serverTime - invite.createdAt)
        val text = when {
            age < 1.days -> "today"
            age < 2.days -> "yesterday"
            else -> "${age.inWholeDays} days ago"
        }
        return "Invited $text · not joined yet"
    }

    private fun createInvite() {
        val request = InviteRequest(canEdit = state.value.canEdit, canManage = state.value.canManage)
        requestedFor = request.canEdit to request.canManage
        updateState { it.copy(creating = true) }
        tryToExecute(
            call = { gateway.createInvite(request) },
            onSuccess = { result ->
                when (result) {
                    // The repository stores the invite, so the snapshot collector draws the link.
                    is TownResult.Ok -> updateState { it.copy(creating = false) }
                    is TownResult.Failed -> {
                        createFailed = true
                        updateState { it.copy(creating = false, message = result.message) }
                    }
                }
            },
            onError = { error ->
                createFailed = true
                updateState { it.copy(creating = false, message = error.toMessage()) }
            },
        )
    }

    fun onCanEditChange(on: Boolean) = changePermission { it.copy(canEdit = on) }

    fun onCanManageChange(on: Boolean) = changePermission { it.copy(canManage = on) }

    private fun changePermission(change: (InviteUiState) -> InviteUiState) {
        createFailed = false
        updateState(change)
        snapshot?.let(::render)
    }

    fun onRetryClick() {
        createFailed = false
        requestedFor = null
        snapshot?.let(::render)
    }

    fun onShareClick() {
        val link = state.value.link
        if (link == null) {
            updateState { it.copy(message = "The link is not ready yet") }
            return
        }
        sendEffect(InviteEffect.Share("Join my town in Brick Town: $link"))
    }

    fun onCopyClick() {
        val link = state.value.link
        if (link == null) {
            updateState { it.copy(message = "The link is not ready yet") }
            return
        }
        sendEffect(InviteEffect.Copy(link))
        updateState { it.copy(message = "Link copied") }
    }

    fun onQrToggle() = updateState { it.copy(showQr = !it.showQr) }

    fun onRevokeClick(code: String) {
        requestedFor = null
        tryToExecute(
            call = { gateway.revokeInvite(code) },
            onSuccess = { result ->
                if (result is TownResult.Failed) updateState { it.copy(message = result.message) }
            },
            onError = { error -> updateState { it.copy(message = error.toMessage()) } },
        )
    }

    fun onMessageShown() = updateState { it.copy(message = null) }

    private companion object {
        const val WATCH_MILLIS = 5_000L
    }
}
