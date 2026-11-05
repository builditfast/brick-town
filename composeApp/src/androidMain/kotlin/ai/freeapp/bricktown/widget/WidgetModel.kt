package ai.freeapp.bricktown.widget

import ai.freeapp.bricktown.domain.TownSnapshot
import ai.freeapp.bricktown.domain.formatWhole
import ai.freeapp.bricktown.domain.streakOf
import ai.freeapp.bricktown.domain.summarize
import kotlinx.datetime.LocalDate

/** One column of the pink widget's mini tower chart: [height] is 0..1 of the tallest tower. */
data class WidgetBar(val colorIndex: Int, val height: Float)

/**
 * What the three widgets show. Built by [widgetModelOf] from the cached town,
 * so a widget never computes money, a month or a streak itself.
 */
sealed interface WidgetModel {
    /** The `widgets` flag is off: nothing to tap. */
    data object Off : WidgetModel

    data object SignedOut : WidgetModel

    data object NoTown : WidgetModel

    data class Ready(
        /** "€57", or "Over €12" once today's allowance is spent. */
        val headline: String,
        /** "left today of €81". */
        val subline: String,
        val bars: List<WidgetBar>,
        /** "€1 312 · €488 left". */
        val monthLine: String,
        val streak: Int,
        val streakLine: String,
    ) : WidgetModel
}

private const val MAX_BARS = 6

fun widgetModelOf(
    signedIn: Boolean,
    snapshot: TownSnapshot?,
    widgetsOn: Boolean,
    today: LocalDate?,
): WidgetModel {
    if (!widgetsOn) return WidgetModel.Off
    if (!signedIn) return WidgetModel.SignedOut
    if (snapshot == null || today == null) return WidgetModel.NoTown
    val currency = snapshot.town.settings.currency
    val month = summarize(snapshot, today)
    val streak = streakOf(snapshot, today)

    val tallest = month.towers.maxOfOrNull { it.bricks }?.coerceAtLeast(1) ?: 1
    val bars = month.towers.take(MAX_BARS).map {
        WidgetBar(it.category.colorIndex, (it.bricks.coerceAtLeast(0).toFloat() / tallest).coerceIn(0.08f, 1f))
    }
    val left = month.todayLeftCents
    return WidgetModel.Ready(
        headline = if (left >= 0) formatWhole(left, currency) else "Over ${formatWhole(-left, currency)}",
        subline = if (left >= 0) {
            "left today of ${formatWhole(month.dailyBudgetCents, currency)}"
        } else {
            "today. Tomorrow resets."
        },
        bars = bars,
        monthLine = formatWhole(month.spentCents, currency) + " · " +
            if (month.monthLeftCents >= 0) {
                "${formatWhole(month.monthLeftCents, currency)} left"
            } else {
                "${formatWhole(-month.monthLeftCents, currency)} over"
            },
        streak = streak.current,
        streakLine = when {
            streak.current == 0 && !streak.loggedToday -> "log today to start a streak"
            streak.loggedToday -> "day streak"
            else -> "day streak · log today"
        },
    )
}
