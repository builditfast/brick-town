package ai.freeapp.bricktown.join

import ai.freeapp.bricktown.auth.AuthRepository
import ai.freeapp.bricktown.core.ui.BaseViewModel
import ai.freeapp.bricktown.core.ui.toMessage
import ai.freeapp.bricktown.town.InviteCodes
import ai.freeapp.bricktown.town.TownResult
import ai.freeapp.bricktown.wire.InvitePreview
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

enum class JoinStep { EnterCode, Looking, Preview, Pick }

@Immutable
data class JoinUiState(
    val step: JoinStep = JoinStep.EnterCode,
    /** What the person typed, as typed. */
    val codeInput: String = "",
    val preview: InvitePreview? = null,
    /** Index into the member palette; null only while there is no preview. */
    val colorIndex: Int? = null,
    val initial: String = "",
    val isJoining: Boolean = false,
    /** The sentence to show: the server's own, or the spec's failure copy. */
    val error: String? = null,
) {
    val canLookUp: Boolean get() = InviteCodes.normalize(codeInput) != null && step != JoinStep.Looking
    val canJoin: Boolean get() = colorIndex != null && initial.isNotEmpty() && !isJoining
}

sealed interface JoinEffect {
    /** Joined: Month greets [townName] once. */
    data class Joined(val townName: String) : JoinEffect
}

/** What joining needs from the town repository, so the ViewModel can be tested without a server. */
interface InviteGateway {
    suspend fun preview(code: String): TownResult<InvitePreview>
    suspend fun accept(code: String, colorIndex: Int, initial: String): TownResult<Unit>
}

class JoinViewModel(
    code: String,
    private val gateway: InviteGateway,
    auth: AuthRepository,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : BaseViewModel<JoinUiState, JoinEffect>(JoinUiState(codeInput = code), dispatcher) {

    private val defaultInitial: String = auth.currentUser.value?.name?.trim()?.firstOrNull { it.isLetter() }?.uppercase().orEmpty()

    init {
        if (code.isNotBlank()) onLookUp()
    }

    fun onCodeChange(value: String) = updateState { it.copy(codeInput = value, error = null) }

    fun onLookUp() {
        val code = InviteCodes.normalize(state.value.codeInput)
            ?: return updateState { it.copy(step = JoinStep.EnterCode, error = BAD_CODE) }
        updateState { it.copy(step = JoinStep.Looking, error = null) }
        tryToExecute(
            call = { gateway.preview(code) },
            onSuccess = { result ->
                when (result) {
                    is TownResult.Ok -> updateState {
                        val first = (0 until MEMBER_COLORS).firstOrNull { c -> c !in result.value.takenColors }
                        it.copy(step = JoinStep.Preview, preview = result.value, colorIndex = first, initial = defaultInitial)
                    }
                    is TownResult.Failed -> updateState { it.copy(step = JoinStep.EnterCode, error = sentence(result)) }
                }
            },
            onError = { e -> updateState { it.copy(step = JoinStep.EnterCode, error = e.toMessage()) } },
        )
    }

    /** From "Join {town}?" on to colour and initial. */
    fun onContinue() = updateState { if (it.preview == null) it else it.copy(step = JoinStep.Pick, error = null) }

    fun onPickColor(index: Int) = updateState { s ->
        if (s.preview == null || index in s.preview.takenColors) s else s.copy(colorIndex = index)
    }

    fun onInitialChange(value: String) =
        updateState { it.copy(initial = value.filter(Char::isLetterOrDigit).take(1).uppercase()) }

    /** Pick goes back to the preview, the preview to the code field. Leaving the screen is the caller's. */
    fun onStepBack(): Boolean {
        val step = state.value.step
        if (step == JoinStep.Pick) {
            updateState { it.copy(step = JoinStep.Preview, error = null) }
            return true
        }
        return false
    }

    fun onJoin() {
        val s = state.value
        val preview = s.preview ?: return
        val color = s.colorIndex ?: return
        if (!s.canJoin) return
        updateState { it.copy(isJoining = true, error = null) }
        tryToExecute(
            call = { gateway.accept(preview.code, color, s.initial) },
            onSuccess = { result ->
                when (result) {
                    is TownResult.Ok -> {
                        updateState { it.copy(isJoining = false) }
                        sendEffect(JoinEffect.Joined(preview.townName))
                    }
                    is TownResult.Failed -> updateState { it.copy(isJoining = false, error = sentence(result)) }
                }
            },
            onError = { e -> updateState { it.copy(isJoining = false, error = e.toMessage()) } },
        )
    }

    /** An expired or revoked invite is a 404; every other refusal arrives as the server's own sentence. */
    private fun sentence(failure: TownResult.Failed): String = if (failure.status == 404) EXPIRED else failure.message

    companion object {
        const val EXPIRED = "That invite has expired. Ask for a new link."
        const val BAD_CODE = "That code does not look right. It has 8 letters and numbers, like 8F2K-P3NY."
        const val MEMBER_COLORS = 8
    }
}
