package ai.freeapp.bricktown.compare

import ai.freeapp.bricktown.history.monthName
import ai.freeapp.bricktown.history.shortMonthName
import ai.freeapp.bricktown.domain.TownSnapshot
import ai.freeapp.bricktown.domain.formatSigned
import ai.freeapp.bricktown.domain.formatWhole
import ai.freeapp.bricktown.domain.previous
import ai.freeapp.bricktown.domain.summarize
import androidx.compose.runtime.Immutable
import kotlinx.datetime.LocalDate
import kotlin.math.abs

@Immutable
data class CompareRowUi(
    val id: String,
    val name: String,
    val short: String,
    val colorIndex: Int,
    val amountText: String,
    /** 0..1 against the largest tower. */
    val barFraction: Float,
    val deltaText: String,
    /** True when this tower is above last month's same day (red), false when level or below. */
    val deltaUp: Boolean,
)

@Immutable
data class CompareUiState(
    val ready: Boolean = false,
    val title: String = "",
    /** Spent this month over the budget, capped at 1. */
    val outer: Float = 0f,
    /** Last month's same-day spend over the budget, capped at 1. */
    val inner: Float = 0f,
    val aheadText: String = "",
    val aheadLabel: String = "",
    /** True ahead (green), false behind (red), null level. */
    val ahead: Boolean? = null,
    val thisLegend: String = "",
    val lastLegend: String = "",
    val rows: List<CompareRowUi> = emptyList(),
)

internal fun fraction(cents: Long, budgetCents: Long): Float =
    if (budgetCents <= 0L) 0f else (cents.toFloat() / budgetCents.toFloat()).coerceIn(0f, 1f)

internal fun compareUiState(town: TownSnapshot, today: LocalDate): CompareUiState {
    val settings = town.town.settings
    val s = summarize(town, today)
    val currency = settings.currency
    val thisName = monthName(s.month.name)
    val prev = s.month.previous(settings.payday)
    val prevName = monthName(prev.name)
    val prevShort = shortMonthName(prev.name)
    val budget = settings.monthlyBudgetCents
    val shown = s.towers.filter { it.totalCents != 0L || it.lastMonthSameDayCents != 0L }
        .sortedByDescending { it.totalCents }
    val largest = shown.maxOfOrNull { it.totalCents }?.coerceAtLeast(1L) ?: 1L
    val ahead = when {
        s.aheadCents < 0L -> true
        s.aheadCents > 0L -> false
        else -> null
    }
    return CompareUiState(
        ready = true,
        title = "$thisName vs $prevName · same day",
        outer = fraction(s.spentCents, budget),
        inner = fraction(s.lastMonthSameDayCents, budget),
        aheadText = formatWhole(abs(s.aheadCents), currency),
        aheadLabel = when (ahead) {
            true -> "ahead of last month"
            false -> "behind last month"
            null -> "level with last month"
        },
        ahead = ahead,
        thisLegend = "${shortMonthName(s.month.name)} ${formatWhole(s.spentCents, currency)}",
        lastLegend = "$prevShort ${formatWhole(s.lastMonthSameDayCents, currency)}",
        rows = shown.map { t ->
            val delta = t.totalCents - t.lastMonthSameDayCents
            CompareRowUi(
                id = t.category.id,
                name = t.category.name,
                short = t.category.short,
                colorIndex = t.category.colorIndex,
                amountText = formatWhole(t.totalCents, currency),
                barFraction = (t.totalCents.coerceAtLeast(0L).toFloat() / largest.toFloat()).coerceIn(0f, 1f),
                deltaText = "${formatSigned(delta, currency)} vs $prevShort",
                deltaUp = delta > 0L,
            )
        },
    )
}
