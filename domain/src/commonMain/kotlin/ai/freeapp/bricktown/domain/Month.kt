package ai.freeapp.bricktown.domain

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.plus
import kotlinx.datetime.minus
import kotlinx.datetime.daysUntil

/** One brick is €25. */
const val BRICK_CENTS: Long = 2_500L

/** The tower chart is 250 pt tall with 3 pt gaps between bricks. */
const val CHART_HEIGHT: Int = 250
const val BRICK_GAP: Int = 3

/**
 * Bricks a tower shows: round(total / €25), at least 1 while the total is
 * above zero, 0 when refunds bring it to zero or below.
 */
fun bricksFor(totalCents: Long): Int {
    if (totalCents <= 0) return 0
    return roundDiv(totalCents, BRICK_CENTS).toInt().coerceAtLeast(1)
}

/** clamp(4, 13, floor((250 − (maxN − 1)·3) / maxN) − 3), so the tallest tower always fits. 13 when no tower has bricks. */
fun brickHeight(maxBricks: Int): Int {
    if (maxBricks <= 0) return 13
    return ((CHART_HEIGHT - (maxBricks - 1) * BRICK_GAP) / maxBricks - BRICK_GAP).coerceIn(4, 13)
}

/**
 * One pay month: from payday to the day before the next payday. Payday 1 is the
 * calendar month. A payday past the end of a short month falls on its last day.
 */
data class PayMonth(val start: LocalDate, val endInclusive: LocalDate) {
    /** Length in days: the divisor of the daily budget. */
    val days: Int get() = start.daysUntil(endInclusive) + 1

    /** The calendar month holding the window's midpoint ("December" for 28 Nov to 27 Dec). */
    val name: Month get() = start.plus(days / 2, DateTimeUnit.DAY).month

    operator fun contains(date: LocalDate): Boolean = date in start..endInclusive

    /** 1 on [start]. */
    fun dayNumber(date: LocalDate): Int = start.daysUntil(date) + 1
}

fun payMonthOf(date: LocalDate, payday: Int): PayMonth {
    val thisStart = payMonthStart(date.year, date.monthNumber, payday)
    val start = if (date >= thisStart) thisStart else previousMonthStart(date.year, date.monthNumber, payday)
    val nextStart = if (start.monthNumber == 12) payMonthStart(start.year + 1, 1, payday) else payMonthStart(start.year, start.monthNumber + 1, payday)
    return PayMonth(start, nextStart.minus(1, DateTimeUnit.DAY))
}

private fun previousMonthStart(year: Int, month: Int, payday: Int): LocalDate =
    if (month == 1) payMonthStart(year - 1, 12, payday) else payMonthStart(year, month - 1, payday)

/** The payday clamped to the month's last day: payday 31 in November starts on the 30th. */
private fun payMonthStart(year: Int, month: Int, payday: Int): LocalDate =
    LocalDate(year, month, payday.coerceIn(1, lastDayOfMonth(year, month)))

internal fun lastDayOfMonth(year: Int, month: Int): Int {
    val first = LocalDate(year, month, 1)
    return first.daysUntil(first.plus(1, DateTimeUnit.MONTH))
}

fun PayMonth.previous(payday: Int): PayMonth = payMonthOf(start.minus(1, DateTimeUnit.DAY), payday)

fun PayMonth.next(payday: Int): PayMonth = payMonthOf(endInclusive.plus(1, DateTimeUnit.DAY), payday)

enum class PennyMood { SMILE, FROWN, NEUTRAL }

/** A monthly bill from last pay month that has no brick this pay month yet and was not skipped. */
data class GhostBill(
    /** [seriesKeyOf] of the source brick. */
    val seriesKey: String,
    val categoryId: String,
    val note: String,
    val amountCents: Long,
    /** Same day of month as last time, clamped into this pay month. */
    val dueDate: LocalDate,
    val sourceExpenseId: String,
)

/** Category id plus the lower-cased, trimmed note: "the same bill" across months. */
fun seriesKeyOf(expense: Expense): String = expense.categoryId + "|" + expense.note.trim().lowercase()

data class TowerSummary(
    val category: Category,
    /** Signed: refunds subtract. */
    val totalCents: Long,
    val bricks: Int,
    /** Rows in the category sheet ("8 bricks" there counts these). */
    val expenseCount: Int,
    /** round(cap / €25); null without a cap. */
    val capBricks: Int?,
    /** total − cap when above it, else null. */
    val overCapCents: Long?,
    /** This tower's total over the same number of days last pay month (Compare deltas). */
    val lastMonthSameDayCents: Long,
    val ghostBills: List<GhostBill>,
)

data class MonthSummary(
    val month: PayMonth,
    val today: LocalDate,
    val dayNumber: Int,
    /** Signed sum of every brick dated in this pay month. */
    val spentCents: Long,
    /** The recurring part of [spentCents]. */
    val billsCents: Long,
    /** Ghost bills not yet confirmed or skipped: pencilled in, not spent. */
    val pencilledCents: Long,
    /** spent − bills. */
    val movingCents: Long,
    /** round((budget − bills) / days) when bills first, else round(budget / days); whole units. Pencilled bills do not count (6c shows €60 of €60 beside €358 pencilled). */
    val dailyBudgetCents: Long,
    /** Signed sum of today's non-recurring bricks. */
    val todaySpentCents: Long,
    val todayLeftCents: Long,
    /** budget − spent − pencilled. */
    val monthLeftCents: Long,
    /** Spent over the first [dayNumber] days of last pay month. */
    val lastMonthSameDayCents: Long,
    /** spent − lastMonthSameDay. Negative is ahead (green), positive behind (red). */
    val aheadCents: Long,
    /** In category position order, every category, empty ones included. */
    val towers: List<TowerSummary>,
    val brickHeight: Int,
    /** SMILE when today's left is zero or more, FROWN below, NEUTRAL before the town has any brick. */
    val mood: PennyMood,
    /** The newest brick another member added (the activity pill), or null. */
    val partnerLatest: Expense?,
) {
    /** No brick laid yet this pay month: 6c's fresh month, where the + pulses. */
    val fresh: Boolean get() = spentCents == 0L
}


fun summarize(snapshot: TownSnapshot, today: LocalDate): MonthSummary {
    val settings = snapshot.town.settings
    val month = payMonthOf(today, settings.payday)
    val inMonth = snapshot.expenses.filter { it.date in month }
    val spent = inMonth.sumOf { it.signedCents }
    val bills = inMonth.filter { it.recurring }.sumOf { it.signedCents }
    val ghosts = ghostBillsFor(snapshot, month)
    val pencilled = ghosts.sumOf { it.amountCents }
    val dayNumber = month.dayNumber(today)
    val towers = towerSummaries(snapshot, month, dayNumber, ghosts)
    val lastSameDay = lastMonthSameDay(snapshot.expenses, month, settings.payday, dayNumber)
    val daily = dailyBudgetCents(settings, bills, month.days)
    val todaySpent = snapshot.expenses.filter { it.date == today && !it.recurring }.sumOf { it.signedCents }
    val todayLeft = daily - todaySpent
    return MonthSummary(
        month = month,
        today = today,
        dayNumber = dayNumber,
        spentCents = spent,
        billsCents = bills,
        pencilledCents = pencilled,
        movingCents = spent - bills,
        dailyBudgetCents = daily,
        todaySpentCents = todaySpent,
        todayLeftCents = todayLeft,
        monthLeftCents = settings.monthlyBudgetCents - spent - pencilled,
        lastMonthSameDayCents = lastSameDay,
        aheadCents = spent - lastSameDay,
        towers = towers,
        brickHeight = brickHeight(towers.maxOfOrNull { it.bricks } ?: 0),
        mood = when {
            snapshot.expenses.isEmpty() -> PennyMood.NEUTRAL
            todayLeft >= 0 -> PennyMood.SMILE
            else -> PennyMood.FROWN
        },
        partnerLatest = snapshot.expenses
            .filter { it.paidBy != snapshot.viewerId }
            .maxByOrNull { it.createdAt },
    )
}

/** Half up for positive and negative numerators alike. */
internal fun roundDiv(numerator: Long, denominator: Long): Long =
    (2 * numerator + denominator).floorDiv(2 * denominator)

/** Whole currency units, as the design shows the daily budget ("€60 of €60"). */
internal fun dailyBudgetCents(settings: TownSettings, billsCents: Long, days: Int): Long {
    val base = if (settings.billsFirst) settings.monthlyBudgetCents - billsCents else settings.monthlyBudgetCents
    return roundDiv(base, 100L * days) * 100
}

/** Signed spend over the first [dayCount] days of the pay month before [month]. */
private fun lastMonthSameDay(expenses: List<Expense>, month: PayMonth, payday: Int, dayCount: Int): Long {
    val previous = month.previous(payday)
    val last = minOf(previous.start.plus(dayCount - 1, DateTimeUnit.DAY), previous.endInclusive)
    return expenses.filter { it.date >= previous.start && it.date <= last }.sumOf { it.signedCents }
}

internal fun towerSummaries(
    snapshot: TownSnapshot,
    month: PayMonth,
    dayCount: Int,
    ghosts: List<GhostBill>,
): List<TowerSummary> {
    val previous = month.previous(snapshot.town.settings.payday)
    val last = minOf(previous.start.plus(dayCount - 1, DateTimeUnit.DAY), previous.endInclusive)
    return snapshot.categories.sortedBy { it.position }.map { category ->
        val rows = snapshot.expenses.filter { it.categoryId == category.id && it.date in month }
        val total = rows.sumOf { it.signedCents }
        val cap = category.capCents
        TowerSummary(
            category = category,
            totalCents = total,
            bricks = bricksFor(total),
            expenseCount = rows.size,
            capBricks = cap?.let { roundDiv(it, BRICK_CENTS).toInt() },
            overCapCents = if (cap != null && total > cap) total - cap else null,
            lastMonthSameDayCents = snapshot.expenses
                .filter { it.categoryId == category.id && it.date >= previous.start && it.date <= last }
                .sumOf { it.signedCents },
            ghostBills = ghosts.filter { it.categoryId == category.id },
        )
    }
}

/**
 * Recurring bricks of the previous pay month whose series has no brick of any
 * kind in [month] (a confirmed bill is just that brick) and was not skipped.
 * The newest source wins when a series repeats inside one month.
 */
internal fun ghostBillsFor(snapshot: TownSnapshot, month: PayMonth): List<GhostBill> {
    val previous = month.previous(snapshot.town.settings.payday)
    val present = snapshot.expenses.filter { it.date in month }.map { seriesKeyOf(it) }.toSet()
    val skipped = snapshot.billSkips.filter { it.monthStart == month.start }.map { it.seriesKey }.toSet()
    return snapshot.expenses
        .filter { it.recurring && !it.refund && it.date in previous }
        .groupBy { seriesKeyOf(it) }
        .filterKeys { it !in present && it !in skipped }
        .map { (key, sources) ->
            val source = sources.maxWith(compareBy<Expense>({ it.date }, { it.createdAt }))
            GhostBill(
                seriesKey = key,
                categoryId = source.categoryId,
                note = source.note,
                amountCents = source.amountCents,
                dueDate = dueDateIn(month, source.date.dayOfMonth),
                sourceExpenseId = source.id,
            )
        }
        .sortedWith(compareBy({ it.dueDate }, { it.sourceExpenseId }))
}

/** The window day with that day of month, else the window's last day (day 31 in a window that has no 31st). */
private fun dueDateIn(month: PayMonth, dayOfMonth: Int): LocalDate {
    var d = month.start
    while (d <= month.endInclusive) {
        if (d.dayOfMonth == dayOfMonth) return d
        d = d.plus(1, DateTimeUnit.DAY)
    }
    return month.endInclusive
}

internal fun LocalDate.plusDays(days: Int): LocalDate = plus(days, DateTimeUnit.DAY)

internal fun englishMonthName(month: Month): String = month.name.lowercase().replaceFirstChar { it.uppercase() }
