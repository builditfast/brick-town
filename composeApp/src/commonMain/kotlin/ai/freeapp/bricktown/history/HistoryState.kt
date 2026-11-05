package ai.freeapp.bricktown.history

import ai.freeapp.bricktown.brick.amountText
import ai.freeapp.bricktown.domain.Currency
import ai.freeapp.bricktown.domain.Expense
import ai.freeapp.bricktown.domain.PayMonth
import ai.freeapp.bricktown.domain.TownSnapshot
import ai.freeapp.bricktown.domain.closedMonths
import ai.freeapp.bricktown.domain.formatWhole
import ai.freeapp.bricktown.domain.lastCompletedWeekStart
import ai.freeapp.bricktown.domain.weekRecap
import ai.freeapp.bricktown.month.shortDate
import androidx.compose.runtime.Immutable
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.plus

@Immutable
data class HistoryRowUi(
    val id: String,
    val dateTile: String,
    val note: String,
    val categoryName: String,
    val colorIndex: Int,
    val monthly: Boolean,
    /** The initial of whoever paid when the brick is shared or was paid by someone else, else null. */
    val whoInitial: String?,
    val whoColorIndex: Int,
    val amountText: String,
    val refund: Boolean,
)

/** "Week 47 recap is ready". */
@Immutable
data class RecapCardUi(val weekStart: LocalDate, val weekNumber: Int, val summary: String)

@Immutable
data class TrophyUi(
    val monthStart: LocalDate,
    val title: String,
    val spentText: String,
    val goldDaysText: String,
    val underBudget: Boolean,
)

@Immutable
data class HistoryUiState(
    val ready: Boolean = false,
    val query: String = "",
    val count: Int = 0,
    val recap: RecapCardUi? = null,
    val rows: List<HistoryRowUi> = emptyList(),
    val trophies: List<TrophyUi> = emptyList(),
    val monthsOpen: Boolean = false,
) {
    /** The header: no count until the town has loaded, so a real "0" is never confused with "not yet". */
    val title: String get() = if (ready) "History · $count" else "History"

    val monthsSubtitle: String get() = if (trophies.size == 1) "1 trophy" else "${trophies.size} trophies"
}

private val MonthNames = Month.entries.map { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }

internal fun monthName(month: Month): String = MonthNames[month.ordinal]

internal fun shortMonthName(month: Month): String = monthName(month).take(3)

/** "November 2026", named after the calendar month holding the pay month's midpoint. */
internal fun PayMonth.title(): String {
    val middle = start.plus(days / 2, DateTimeUnit.DAY)
    return "${monthName(middle.month)} ${middle.year}"
}

/** True when [expense] matches [query] on its note or its tower name, ignoring case. */
internal fun matches(expense: Expense, towerName: String, query: String): Boolean {
    val needle = query.trim()
    if (needle.isEmpty()) return true
    return expense.note.contains(needle, ignoreCase = true) || towerName.contains(needle, ignoreCase = true)
}

/** Pure mapping from the synced town: every brick newest first, filtered by [query]. */
internal fun historyUiState(town: TownSnapshot, today: LocalDate, query: String): HistoryUiState {
    val currency = town.town.settings.currency
    val categories = town.categories.associateBy { it.id }
    val members = town.members.associateBy { it.userId }
    val rows = town.expenses
        .sortedWith(compareByDescending<Expense> { it.date }.thenByDescending { it.createdAt })
        .mapNotNull { e ->
            val category = categories[e.categoryId]
            val tower = category?.name.orEmpty()
            if (!matches(e, tower, query)) return@mapNotNull null
            val payer = if (e.shared || e.paidBy != town.viewerId) members[e.paidBy] else null
            HistoryRowUi(
                id = e.id,
                dateTile = shortDate(e.date),
                note = e.note.ifBlank { tower },
                categoryName = tower,
                colorIndex = category?.colorIndex ?: 0,
                monthly = e.recurring,
                whoInitial = payer?.initial,
                whoColorIndex = payer?.colorIndex ?: 0,
                amountText = (if (e.refund) "−" else "") + amountText(e.amountCents, currency),
                refund = e.refund,
            )
        }
    return HistoryUiState(
        ready = true,
        query = query,
        count = rows.size,
        recap = recapCard(town, today, currency),
        rows = rows,
        trophies = closedMonths(town, today).map { t ->
            TrophyUi(
                monthStart = t.month.start,
                title = t.month.title(),
                spentText = "${formatWhole(t.spentCents, currency)} of ${formatWhole(t.budgetCents, currency)}",
                goldDaysText = if (t.goldDays == 1) "1 gold day" else "${t.goldDays} gold days",
                underBudget = t.underBudget,
            )
        },
    )
}

/** Hidden when last week has no bricks. */
private fun recapCard(town: TownSnapshot, today: LocalDate, currency: Currency): RecapCardUi? {
    val weekStart = lastCompletedWeekStart(today)
    val recap = weekRecap(town, weekStart)
    val hasBricks = town.expenses.any { it.date >= recap.start && it.date <= recap.end }
    if (!hasBricks) return null
    val parts = buildList {
        add(formatWhole(recap.spentCents, currency))
        if (recap.bestWeekOfMonth) add("best week this month")
        if (recap.goldDays > 0) add(if (recap.goldDays == 1) "1 gold day" else "${recap.goldDays} gold days")
    }
    return RecapCardUi(weekStart, recap.weekNumber, parts.joinToString(" · "))
}
