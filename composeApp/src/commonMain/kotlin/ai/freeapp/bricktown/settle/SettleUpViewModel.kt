package ai.freeapp.bricktown.settle

import ai.freeapp.bricktown.brick.amountText
import ai.freeapp.bricktown.brick.signedAmountText
import ai.freeapp.bricktown.core.time.TownClock
import ai.freeapp.bricktown.core.ui.BaseViewModel
import ai.freeapp.bricktown.core.ui.toMessage
import ai.freeapp.bricktown.domain.Expense
import ai.freeapp.bricktown.domain.TownSnapshot
import ai.freeapp.bricktown.domain.formatExact
import ai.freeapp.bricktown.domain.settleShareCents
import ai.freeapp.bricktown.domain.settleStateOf
import ai.freeapp.bricktown.domain.signedCents
import ai.freeapp.bricktown.settings.TownGateway
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime

private val MONTHS = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")

/**
 * One shared brick on Settle up. [shareText] is the viewer's half: "+€90" when the partner owes it, "−€7" when the viewer
 * does. [partnerInitial] is set on the bricks the partner paid, which show the partner's avatar.
 */
@Immutable
data class SettleBrick(
    val id: String,
    val title: String,
    val meta: String,
    val amount: String,
    val colorIndex: Int,
    val shareText: String,
    val shareOwedToYou: Boolean,
    val partnerInitial: String? = null,
    val partnerColorIndex: Int = 0,
)

@Immutable
data class SettleUpUiState(
    val loaded: Boolean = false,
    val hasPartner: Boolean = false,
    val title: String = "Settle up",
    val youInitial: String = "Y",
    val youColorIndex: Int = 0,
    val partnerName: String = "",
    val partnerInitial: String = "",
    val partnerColorIndex: Int = 1,
    val headline: String = "",
    val allSquare: Boolean = true,
    val summary: String = "",
    val youPaid: List<SettleBrick> = emptyList(),
    val partnerPaid: List<SettleBrick> = emptyList(),
    val canSettle: Boolean = false,
    val settleLabel: String = "Mark as settled",
    val canPing: Boolean = false,
    val message: String? = null,
)

sealed interface SettleUpEffect {
    data class Ping(val text: String) : SettleUpEffect
}

/** Settle up (6b): who owes whom over the unsettled shared bricks, and the one action that clears them. */
class SettleUpViewModel(
    private val gateway: TownGateway,
    private val clock: TownClock,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : BaseViewModel<SettleUpUiState, SettleUpEffect>(SettleUpUiState(), dispatcher) {

    private var pingText: String? = null

    init {
        tryToCollect(
            flow = { gateway.town },
            onEach = { town -> if (town != null) render(town) },
            onError = { error -> updateState { it.copy(loaded = true, message = error.toMessage()) } },
        )
    }

    private fun render(town: TownSnapshot) {
        val today = clock.today(town.town.settings)
        val currency = town.town.settings.currency
        val settle = settleStateOf(town, today)
        val viewer = town.members.firstOrNull { it.userId == town.viewerId }
        val partner = settle.partner
        if (partner == null) {
            pingText = null
            updateState { it.copy(loaded = true, hasPartner = false) }
            return
        }
        val open = settle.youPaid + settle.partnerPaid
        val name = partner.name
        val owed = settle.balanceCents
        val square = owed == 0L
        val towers = town.categories.associateBy { it.id }
        fun row(expense: Expense, partnerPaid: Boolean): SettleBrick {
            val tower = towers[expense.categoryId]
            val share = settleShareCents(expense, town.viewerId)
            return SettleBrick(
                id = expense.id,
                title = expense.note.ifBlank { tower?.name.orEmpty() },
                meta = "${dayLabel(expense.date)} · ${tower?.name.orEmpty()}" + if (expense.recurring) " · Monthly" else "",
                amount = amountText(expense.signedCents, currency),
                colorIndex = tower?.colorIndex ?: 0,
                shareText = signedAmountText(share, currency),
                shareOwedToYou = share >= 0,
                partnerInitial = if (partnerPaid) partner.initial else null,
                partnerColorIndex = partner.colorIndex,
            )
        }
        pingText = if (owed > 0) "We are ${formatExact(owed, currency)} apart in Brick Town. Settle up?" else null
        val lastSettled = settle.lastSettledAt?.toLocalDateTime(TimeZone.UTC)?.date
        updateState {
            it.copy(
                loaded = true,
                hasPartner = true,
                title = "Town with $name",
                youInitial = viewer?.initial ?: "Y",
                youColorIndex = viewer?.colorIndex ?: 0,
                partnerName = name,
                partnerInitial = partner.initial,
                partnerColorIndex = partner.colorIndex,
                headline = when {
                    square -> "All square"
                    owed > 0 -> "$name owes you ${formatExact(owed, currency)}"
                    else -> "You owe $name ${formatExact(-owed, currency)}"
                },
                allSquare = square,
                summary = when (open.size) {
                    0 -> "No shared bricks to settle"
                    1 -> "1 shared brick in ${MONTHS[today.month.number - 1]} · split 50/50"
                    else -> "${open.size} shared bricks in ${MONTHS[today.month.number - 1]} · split 50/50"
                },
                youPaid = settle.youPaid.map { row(it, partnerPaid = false) },
                partnerPaid = settle.partnerPaid.map { row(it, partnerPaid = true) },
                canSettle = open.isNotEmpty(),
                settleLabel = if (open.isNotEmpty()) "Mark as settled" else if (lastSettled != null) "Settled · ${dayLabel(lastSettled)}" else "Nothing to settle",
                canPing = owed > 0,
            )
        }
    }

    private fun dayLabel(day: LocalDate) = "${day.dayOfMonth} ${MONTHS[day.month.number - 1].take(3)}"

    fun onSettleClick() {
        if (!state.value.canSettle) return
        tryToExecute(
            call = {
                val town = gateway.town.first() ?: return@tryToExecute
                gateway.settle(clock.today(town.town.settings))
            },
            onSuccess = { updateState { it.copy(message = "Settled") } },
            onError = { error -> updateState { it.copy(message = error.toMessage()) } },
        )
    }

    fun onPingClick() {
        val text = pingText ?: return
        sendEffect(SettleUpEffect.Ping(text))
    }

    fun onMessageShown() = updateState { it.copy(message = null) }
}
