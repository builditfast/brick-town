package ai.freeapp.bricktown.recap

import ai.freeapp.bricktown.domain.DayBar
import ai.freeapp.bricktown.domain.TownSnapshot
import ai.freeapp.bricktown.domain.WeekRecap
import ai.freeapp.bricktown.domain.bricksFor
import ai.freeapp.bricktown.domain.formatSigned
import ai.freeapp.bricktown.domain.formatWhole
import ai.freeapp.bricktown.domain.signedCents
import ai.freeapp.bricktown.domain.weekRecap
import ai.freeapp.bricktown.history.shortMonthName
import androidx.compose.runtime.Immutable
import kotlinx.datetime.LocalDate

@Immutable
data class RecapDayUi(val letter: String, val fraction: Float, val gold: Boolean)

@Immutable
data class RecapInsightUi(
    /** Palette index for a tower tile, or a member colour when [initial] is set. */
    val colorIndex: Int,
    val initial: String?,
    val title: String,
    val subtitle: String,
    val amountText: String,
)

@Immutable
data class RecapUiState(
    val ready: Boolean = false,
    val header: String = "",
    val pennyHappy: Boolean = true,
    val pennyLine: String = "",
    val spentText: String = "",
    val deltaText: String = "",
    val deltaLabel: String = "",
    /** True when this week cost less than the one before (green). */
    val deltaGood: Boolean = true,
    val days: List<RecapDayUi> = emptyList(),
    val insights: List<RecapInsightUi> = emptyList(),
    val shareText: String = "",
)

private val DayLetters = listOf("M", "T", "W", "T", "F", "S", "S")

/** "16–22 Nov", or "30 Nov – 6 Dec" when the week crosses a month. */
internal fun weekRange(start: LocalDate, end: LocalDate): String =
    if (start.month == end.month) {
        "${start.dayOfMonth}–${end.dayOfMonth} ${shortMonthName(end.month)}"
    } else {
        "${start.dayOfMonth} ${shortMonthName(start.month)} – ${end.dayOfMonth} ${shortMonthName(end.month)}"
    }

internal fun recapUiState(town: TownSnapshot, weekStart: LocalDate): RecapUiState {
    val currency = town.town.settings.currency
    val r = weekRecap(town, weekStart)
    val tallest = r.days.maxOfOrNull { it.movingCents.coerceAtLeast(0L) }?.coerceAtLeast(1L) ?: 1L
    val delta = r.spentCents - r.previousWeekCents
    val spent = formatWhole(r.spentCents, currency)
    return RecapUiState(
        ready = true,
        header = "Week ${r.weekNumber} · ${weekRange(r.start, r.end)}",
        pennyHappy = delta <= 0L,
        pennyLine = r.pennyLine,
        spentText = spent,
        deltaText = formatSigned(delta, currency),
        deltaLabel = "vs week ${r.weekNumber - 1}",
        deltaGood = delta <= 0L,
        days = r.days.mapIndexed { i, d -> day(i, d, tallest) },
        insights = insights(town, r),
        shareText = "Week ${r.weekNumber} in Brick Town: $spent, ${if (r.goldDays == 1) "1 gold day" else "${r.goldDays} gold days"}.",
    )
}

private fun day(i: Int, d: DayBar, tallest: Long) =
    RecapDayUi(DayLetters[i % 7], (d.movingCents.coerceAtLeast(0L).toFloat() / tallest.toFloat()).coerceIn(0f, 1f), d.gold)

private fun insights(town: TownSnapshot, r: WeekRecap): List<RecapInsightUi> {
    val currency = town.town.settings.currency
    val rows = mutableListOf<RecapInsightUi>()
    town.categories.firstOrNull { it.id == r.biggestTowerId }?.let { c ->
        val bricks = bricksFor(r.biggestTowerCents)
        rows += RecapInsightUi(
            c.colorIndex, null, c.name,
            "biggest tower · ${if (bricks == 1) "1 brick" else "$bricks bricks"}",
            formatWhole(r.biggestTowerCents, currency),
        )
    }
    val capTower = town.categories.firstOrNull { it.id == r.capTowerId }
    val headroom = r.capHeadroomCents
    if (capTower != null && headroom != null) {
        val cap = capTower.capCents ?: 0L
        val line = if (headroom >= 0L) "${formatWhole(headroom, currency)} under its cap" else "${formatWhole(-headroom, currency)} over its cap"
        rows += RecapInsightUi(capTower.colorIndex, null, capTower.name, line, formatWhole(cap - headroom, currency))
    }
    if (r.partnerBricks.isNotEmpty()) {
        val who = town.members.firstOrNull { it.userId == r.partnerBricks.first().paidBy }
        val n = r.partnerBricks.size
        rows += RecapInsightUi(
            who?.colorIndex ?: 0,
            who?.initial ?: "?",
            "${who?.name ?: "A partner"} added ${if (n == 1) "1 brick" else "$n bricks"}",
            r.partnerBricks.map { it.note }.filter { it.isNotBlank() }.distinct().take(2).joinToString(" · "),
            formatWhole(r.partnerBricks.sumOf { it.signedCents }, currency),
        )
    }
    return rows
}
