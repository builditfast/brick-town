package ai.freeapp.bricktown.rollover

import ai.freeapp.bricktown.domain.BadgeId
import ai.freeapp.bricktown.domain.TownSnapshot
import ai.freeapp.bricktown.domain.closedMonths
import ai.freeapp.bricktown.domain.formatSigned
import ai.freeapp.bricktown.domain.formatWhole
import ai.freeapp.bricktown.domain.payMonthOf
import ai.freeapp.bricktown.domain.previous
import ai.freeapp.bricktown.history.monthName
import androidx.compose.runtime.Immutable
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

@Immutable
data class RolloverBarUi(val colorIndex: Int, val fraction: Float)

@Immutable
data class RolloverUiState(
    val ready: Boolean = false,
    /** False when the ended month has no trophy yet: the screen then shows only the way out. */
    val found: Boolean = false,
    val dateLine: String = "",
    val title: String = "Month complete",
    val underBudget: Boolean = true,
    val spentText: String = "",
    val budgetText: String = "",
    val bars: List<RolloverBarUi> = emptyList(),
    val goldDays: Int = 0,
    val vsText: String = "",
    val vsLabel: String = "",
    val vsGood: Boolean = true,
    val streak: Int = 0,
    val badges: List<String> = emptyList(),
    val note: String = "",
    val startLabel: String = "Start the new month",
)

/** The trophy of the pay month that began on [monthStart], or the bare fallback when none exists. */
internal fun rolloverUiState(town: TownSnapshot, monthStart: LocalDate, today: LocalDate): RolloverUiState {
    val settings = town.town.settings
    val trophy = closedMonths(town, today).firstOrNull { it.month.start == monthStart }
        ?: return RolloverUiState(ready = true)
    val currency = settings.currency
    val first = trophy.month.endInclusive.plus(1, DateTimeUnit.DAY)
    val next = payMonthOf(first, settings.payday)
    val previous = trophy.month.previous(settings.payday)
    val nextName = monthName(next.name)
    val tallest = trophy.towers.maxOfOrNull { it.totalCents.coerceAtLeast(0L) }?.coerceAtLeast(1L) ?: 1L
    return RolloverUiState(
        ready = true,
        found = true,
        dateLine = "${first.dayOfMonth} $nextName",
        title = "${monthName(trophy.month.name)} is done!",
        underBudget = trophy.underBudget,
        spentText = formatWhole(trophy.spentCents, currency),
        budgetText = "of ${formatWhole(trophy.budgetCents, currency)}",
        bars = trophy.towers.filter { it.totalCents > 0L }.map {
            RolloverBarUi(it.category.colorIndex, (it.totalCents.toFloat() / tallest.toFloat()).coerceIn(0.08f, 1f))
        },
        goldDays = trophy.goldDays,
        vsText = formatSigned(trophy.vsPreviousCents, currency),
        vsLabel = "vs ${monthName(previous.name)}",
        vsGood = trophy.vsPreviousCents <= 0L,
        streak = trophy.streakAtEnd,
        badges = trophy.badgesEarned.map(BadgeId::title),
        note = "Bills (${formatWhole(trophy.billsCents, currency)}) are already pencilled into $nextName. " +
            "Towers start empty; the trophy lives in History → Months.",
        startLabel = "Start $nextName",
    )
}
