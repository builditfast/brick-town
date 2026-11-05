package ai.freeapp.bricktown.domain

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toLocalDateTime

/**
 * Streaks, gold days, badges, weekly recaps and month trophies. Every value is
 * computed from the snapshot, never stored, so an edit or a delete on another
 * phone changes them the same way everywhere. They belong to the town: a day
 * either member logs counts for both.
 */

/** A day with at least one brick (any kind) or a €0 mark. */
fun loggedDays(snapshot: TownSnapshot): Set<LocalDate> =
    snapshot.expenses.map { it.date }.toSet() + snapshot.goldMarks.map { it.date }

/** The non-recurring signed spend per day, the "moving money" the daily budget is about. */
private fun movingByDate(snapshot: TownSnapshot): Map<LocalDate, Long> =
    snapshot.expenses.filter { !it.recurring }.groupBy { it.date }.mapValues { (_, rows) -> rows.sumOf { it.signedCents } }

/** Days with a €0 mark and no non-recurring brick. */
fun goldDays(snapshot: TownSnapshot): Set<LocalDate> {
    val spendingDays = snapshot.expenses.filter { !it.recurring && it.signedCents > 0 }.map { it.date }.toSet()
    return snapshot.goldMarks.map { it.date }.filter { it !in spendingDays }.toSet()
}

data class StreakState(
    /** Consecutive logged days ending today, or yesterday while today is not logged yet. Frozen days keep it alive but do not add to it. */
    val current: Int,
    /** 1 while this pay month's freeze is unused, else 0. */
    val freezesLeft: Int,
    val loggedToday: Boolean,
    /** Missed days a freeze covered: one per pay month, only a single missed day between two logged days. */
    val frozenDays: Set<LocalDate>,
)

fun streakOf(snapshot: TownSnapshot, today: LocalDate): StreakState {
    val timeline = streakTimeline(snapshot, today)
    val payday = snapshot.town.settings.payday
    val thisMonth = payMonthOf(today, payday)
    val freezeUsed = timeline.frozen.any { it in thisMonth }
    return StreakState(
        current = timeline.current,
        freezesLeft = if (freezeUsed) 0 else 1,
        loggedToday = today in loggedDays(snapshot),
        frozenDays = timeline.frozen,
    )
}

private class StreakTimeline(
    /** The streak at the end of each logged or frozen day, in date order. */
    val after: Map<LocalDate, Int>,
    val frozen: Set<LocalDate>,
    val current: Int,
)

/**
 * Replays the days oldest first so a freeze lands on the earliest gap of its
 * pay month and stays there as time passes. A missed day is bridged only when
 * the days either side of it are logged, and only once per pay month. Today,
 * still open, never breaks the streak.
 */
private fun streakTimeline(snapshot: TownSnapshot, today: LocalDate): StreakTimeline {
    val logged = loggedDays(snapshot).filter { it <= today }.toSet()
    val first = logged.minOrNull() ?: return StreakTimeline(emptyMap(), emptySet(), 0)
    val payday = snapshot.town.settings.payday
    val after = LinkedHashMap<LocalDate, Int>()
    val frozen = mutableSetOf<LocalDate>()
    val usedIn = mutableSetOf<LocalDate>()
    var streak = 0
    var day = first
    while (day <= today) {
        if (day in logged) {
            streak++
            after[day] = streak
        } else if (day != today) {
            val monthStart = payMonthOf(day, payday).start
            val bridgeable = day.plusDays(-1) in logged && day.plusDays(1) in logged
            if (bridgeable && monthStart !in usedIn) {
                usedIn += monthStart
                frozen += day
                after[day] = streak
            } else {
                streak = 0
            }
        }
        day = day.plusDays(1)
    }
    return StreakTimeline(after, frozen, streak)
}

/** The trail, in this order. Titles are the design's; the rules are ours (spec section 7). */
enum class BadgeId(val title: String) {
    FIRST_WEEK("First week logged"),
    NO_SPEND_WEEKEND("No-spend weekend"),
    UNDER_BUDGET_X3("Under budget ×3"),
    STREAK_12("12-day streak"),
    LEAK_PLUGGED("Leak plugged"),
    MONTH_UNDER_BUDGET("Month under budget"),
    STREAK_30("30-day streak"),
    ZERO_DELIVERY_WEEK("Zero-delivery week"),
}

data class BadgeState(
    val id: BadgeId,
    /** Earned: "Earned 12 Nov". Not earned: the rule in one short line. */
    val subtitle: String,
    val earnedOn: LocalDate?,
    /** For the first unearned badge: what is left ("3 no-spend days away"). Null otherwise. */
    val progress: String?,
)

/** All eight, trail order. */
fun badgesOf(snapshot: TownSnapshot, today: LocalDate): List<BadgeState> {
    val facts = badgeFacts(snapshot, today)
    val nextUnearned = BadgeId.entries.firstOrNull { facts.earned[it] == null }
    return BadgeId.entries.map { id ->
        val earnedOn = facts.earned[id]
        BadgeState(
            id = id,
            subtitle = if (earnedOn != null) "Earned ${earnedOn.dayOfMonth} ${monthShort(earnedOn.month)}" else ruleLine(id),
            earnedOn = earnedOn,
            progress = if (id == nextUnearned) progressLine(id, facts) else null,
        )
    }
}

private fun monthShort(month: Month): String = englishMonthName(month).take(3)

private fun ruleLine(id: BadgeId): String = when (id) {
    BadgeId.FIRST_WEEK -> "Log 7 different days"
    BadgeId.NO_SPEND_WEEKEND -> "A \u20ac0 Saturday and Sunday"
    BadgeId.UNDER_BUDGET_X3 -> "3 days in a row under budget"
    BadgeId.STREAK_12 -> "Log 12 days in a row"
    BadgeId.LEAK_PLUGGED -> "Bring a capped tower back under its cap"
    BadgeId.MONTH_UNDER_BUDGET -> "End a month under budget"
    BadgeId.STREAK_30 -> "Log 30 days in a row"
    BadgeId.ZERO_DELIVERY_WEEK -> "A week with no delivery apps"
}

private fun progressLine(id: BadgeId, facts: BadgeFacts): String = when (id) {
    BadgeId.FIRST_WEEK -> "${7 - facts.loggedCount} logged days away"
    BadgeId.NO_SPEND_WEEKEND -> "a gold weekend away"
    BadgeId.UNDER_BUDGET_X3 -> {
        val left = 3 - facts.underBudgetRun
        if (left == 1) "1 day under budget away" else "$left days under budget away"
    }
    BadgeId.STREAK_12 -> "${12 - facts.currentStreak} streak days away"
    BadgeId.LEAK_PLUGGED, BadgeId.MONTH_UNDER_BUDGET -> "the end of the month away"
    BadgeId.STREAK_30 -> "${30 - facts.currentStreak} streak days away"
    BadgeId.ZERO_DELIVERY_WEEK -> "a delivery-free week away"
}

private class BadgeFacts(
    val earned: Map<BadgeId, LocalDate?>,
    val loggedCount: Int,
    val underBudgetRun: Int,
    val currentStreak: Int,
)

private val DELIVERY_APPS = listOf(
    "wolt", "glovo", "uber eats", "deliveroo", "just eat", "bolt food", "tazz",
    "foodpanda", "takeaway", "lieferando", "doordash", "grubhub",
)

private fun isDelivery(expense: Expense): Boolean {
    val note = expense.note.lowercase()
    return DELIVERY_APPS.any { it in note }
}

/** Every badge's first true day, worked out from the whole history up to [today]. */
private fun badgeFacts(snapshot: TownSnapshot, today: LocalDate): BadgeFacts {
    val settings = snapshot.town.settings
    val logged = loggedDays(snapshot).filter { it <= today }.sorted()
    val gold = goldDays(snapshot).filter { it <= today }.toSet()
    val timeline = streakTimeline(snapshot, today)
    val earned = mutableMapOf<BadgeId, LocalDate?>()

    earned[BadgeId.FIRST_WEEK] = logged.getOrNull(6)
    earned[BadgeId.NO_SPEND_WEEKEND] = gold.sorted().firstOrNull { it.dayOfWeek == DayOfWeek.SUNDAY && it.plusDays(-1) in gold }
    earned[BadgeId.STREAK_12] = timeline.after.entries.firstOrNull { it.value >= 12 }?.key
    earned[BadgeId.STREAK_30] = timeline.after.entries.firstOrNull { it.value >= 30 }?.key

    val moving = movingByDate(snapshot)
    val dailyByMonth = mutableMapOf<LocalDate, Long>()
    var run = 0
    var underBudget: LocalDate? = null
    if (logged.isNotEmpty()) {
        var day = logged.first()
        while (day < today) {
            val month = payMonthOf(day, settings.payday)
            val daily = dailyByMonth.getOrPut(month.start) { dailyBudgetFor(snapshot, month) }
            run = if ((moving[day] ?: 0L) <= daily) run + 1 else 0
            if (run >= 3 && underBudget == null) underBudget = day
            day = day.plusDays(1)
        }
    }
    earned[BadgeId.UNDER_BUDGET_X3] = underBudget

    var leak: LocalDate? = null
    var monthUnder: LocalDate? = null
    if (logged.isNotEmpty()) {
        var month = payMonthOf(logged.first(), settings.payday)
        var previousTotals: Map<String, Long>? = null
        while (month.endInclusive < today) {
            val rows = snapshot.expenses.filter { it.date in month }
            if (rows.isNotEmpty()) {
                val totals = rows.groupBy { it.categoryId }.mapValues { (_, r) -> r.sumOf { it.signedCents } }
                if (monthUnder == null && rows.sumOf { it.signedCents } <= settings.monthlyBudgetCents) monthUnder = month.endInclusive
                val before = previousTotals
                if (leak == null && before != null && snapshot.categories.any { c ->
                        val cap = c.capCents
                        cap != null && (before[c.id] ?: 0L) > cap && (totals[c.id] ?: 0L) <= cap
                    }
                ) leak = month.endInclusive
                previousTotals = totals
            } else {
                previousTotals = null
            }
            month = month.next(settings.payday)
        }
    }
    earned[BadgeId.LEAK_PLUGGED] = leak
    earned[BadgeId.MONTH_UNDER_BUDGET] = monthUnder

    val firstDelivery = snapshot.expenses.filter { isDelivery(it) && it.date <= today }.minOfOrNull { it.date }
    var zeroDelivery: LocalDate? = null
    if (firstDelivery != null) {
        var weekStart = mondayOf(firstDelivery).plusDays(7)
        while (weekStart.plusDays(6) < today) {
            val weekEnd = weekStart.plusDays(6)
            if (snapshot.expenses.none { isDelivery(it) && it.date >= weekStart && it.date <= weekEnd }) {
                zeroDelivery = weekEnd
                break
            }
            weekStart = weekStart.plusDays(7)
        }
    }
    earned[BadgeId.ZERO_DELIVERY_WEEK] = zeroDelivery

    return BadgeFacts(earned, logged.size, run, timeline.current)
}

private fun dailyBudgetFor(snapshot: TownSnapshot, month: PayMonth): Long {
    val bills = snapshot.expenses.filter { it.recurring && it.date in month }.sumOf { it.signedCents }
    return dailyBudgetCents(snapshot.town.settings, bills, month.days)
}

private fun mondayOf(date: LocalDate): LocalDate = date.plusDays(-(date.dayOfWeek.isoDayNumber - 1))

data class DayBar(
    val date: LocalDate,
    /** Signed non-recurring spend that day. */
    val movingCents: Long,
    val gold: Boolean,
)

data class WeekRecap(
    /** ISO week number. */
    val weekNumber: Int,
    /** Monday. */
    val start: LocalDate,
    /** Sunday. */
    val end: LocalDate,
    /** Signed non-recurring spend over the week. */
    val spentCents: Long,
    val previousWeekCents: Long,
    /** Seven, Monday first. */
    val days: List<DayBar>,
    val goldDays: Int,
    /** The category with the most moving spend this week, with its brick count. */
    val biggestTowerId: String?,
    val biggestTowerCents: Long,
    /** A capped category and how far under (positive) or over (negative) its cap the pay month is at the week's end. */
    val capTowerId: String?,
    val capHeadroomCents: Long?,
    /** Bricks other members added this week. */
    val partnerBricks: List<Expense>,
    /** Lowest spend of the completed weeks inside the current pay month. */
    val bestWeekOfMonth: Boolean,
    /** Penny's line, built from the facts above. */
    val pennyLine: String,
)

/** The Monday of the newest week that has fully ended by [today]. */
fun lastCompletedWeekStart(today: LocalDate): LocalDate = mondayOf(today).plusDays(-7)

fun weekRecap(snapshot: TownSnapshot, weekStart: LocalDate): WeekRecap {
    val settings = snapshot.town.settings
    val end = weekStart.plusDays(6)
    val moving = movingByDate(snapshot)
    val gold = goldDays(snapshot)
    val days = (0..6).map { i ->
        val d = weekStart.plusDays(i)
        DayBar(d, moving[d] ?: 0L, d in gold)
    }
    val spent = days.sumOf { it.movingCents }
    val previous = (0..6).sumOf { moving[weekStart.plusDays(-7 + it)] ?: 0L }
    val weekRows = snapshot.expenses.filter { it.date >= weekStart && it.date <= end && !it.recurring }
    val biggest = snapshot.categories.sortedBy { it.position }
        .map { c -> c to weekRows.filter { it.categoryId == c.id }.sumOf { it.signedCents } }
        .filter { it.second > 0 }
        .maxByOrNull { it.second }

    val month = payMonthOf(end, settings.payday)
    val capTower = snapshot.categories.sortedBy { it.position }
        .filter { it.capCents != null }
        .map { c ->
            val total = snapshot.expenses.filter { it.categoryId == c.id && it.date >= month.start && it.date <= end }.sumOf { it.signedCents }
            c to (c.capCents!! - total)
        }
        .minByOrNull { it.second }

    val partner = snapshot.expenses
        .filter { it.date >= weekStart && it.date <= end && it.paidBy != snapshot.viewerId }
        .sortedWith(compareBy<Expense>({ it.date }, { it.createdAt }))

    val goldCount = days.count { it.gold }
    val best = isBestWeekOfMonth(snapshot, weekStart, spent, month)
    return WeekRecap(
        weekNumber = isoWeekNumber(weekStart),
        start = weekStart,
        end = end,
        spentCents = spent,
        previousWeekCents = previous,
        days = days,
        goldDays = goldCount,
        biggestTowerId = biggest?.first?.id,
        biggestTowerCents = biggest?.second ?: 0L,
        capTowerId = capTower?.first?.id,
        capHeadroomCents = capTower?.second,
        partnerBricks = partner,
        bestWeekOfMonth = best,
        pennyLine = pennyLine(best, spent, previous, goldCount, capTower?.first?.name, capTower?.second),
    )
}

/**
 * Compared against the full Monday-to-Sunday weeks that lie inside the week's
 * pay month and have ended by the snapshot's server date. A week that
 * straddles a payday belongs to no month and is never "best", and neither is
 * the first full week of a month: with no other week ended it beat nothing.
 */
private fun isBestWeekOfMonth(snapshot: TownSnapshot, weekStart: LocalDate, spent: Long, month: PayMonth): Boolean {
    if (weekStart < month.start) return false
    val zone = try { TimeZone.of(snapshot.town.settings.timeZone) } catch (_: Exception) { TimeZone.UTC }
    val serverDay = snapshot.serverTime.toLocalDateTime(zone).date
    val moving = movingByDate(snapshot)
    var start = mondayOf(month.start).let { if (it < month.start) it.plusDays(7) else it }
    var compared = 0
    while (start.plusDays(6) <= month.endInclusive) {
        val end = start.plusDays(6)
        if (start != weekStart && end < serverDay) {
            val other = (0..6).sumOf { moving[start.plusDays(it)] ?: 0L }
            if (other < spent) return false
            compared++
        }
        start = start.plusDays(7)
    }
    return compared > 0
}

private val NUMBER_WORDS = listOf("Zero", "One", "Two", "Three", "Four", "Five", "Six", "Seven")

private fun pennyLine(best: Boolean, spent: Long, previous: Long, gold: Int, capName: String?, headroom: Long?): String {
    val lead = when {
        best -> "Your best week this month."
        spent < previous -> "A lighter week than the one before."
        spent > previous -> "A heavier week than the one before."
        else -> "Right on last week's pace."
    }
    val goldPart = if (gold > 0) "${NUMBER_WORDS[gold]} gold ${if (gold == 1) "day" else "days"}" else null
    val capPart = if (capName != null && headroom != null) {
        if (headroom >= 0) "$capName stayed under its cap" else "$capName went over its cap"
    } else null
    val tail = listOfNotNull(goldPart, capPart).joinToString(" and ")
    return if (tail.isEmpty()) lead else "$lead $tail."
}

/** Thursday decides the year and the week, which is the ISO 8601 rule. */
private fun isoWeekNumber(date: LocalDate): Int {
    val thursday = mondayOf(date).plusDays(3)
    return (thursday.dayOfYear - 1) / 7 + 1
}

data class MonthTrophy(
    val month: PayMonth,
    val budgetCents: Long,
    val spentCents: Long,
    val billsCents: Long,
    val underBudget: Boolean,
    val goldDays: Int,
    /** spent − previous month's spent. */
    val vsPreviousCents: Long,
    val streakAtEnd: Int,
    val badgesEarned: List<BadgeId>,
    val towers: List<TowerSummary>,
)

/** Every pay month that has fully ended since the town started, newest first. Uses today's budget and payday. */
fun closedMonths(snapshot: TownSnapshot, today: LocalDate): List<MonthTrophy> {
    val settings = snapshot.town.settings
    val firstBrick = snapshot.expenses.minOfOrNull { it.date } ?: return emptyList()
    val timeline = streakTimeline(snapshot, today)
    val gold = goldDays(snapshot)
    val badges = badgeFacts(snapshot, today).earned
    val result = mutableListOf<MonthTrophy>()
    var month = payMonthOf(today, settings.payday).previous(settings.payday)
    while (month.endInclusive >= firstBrick) {
        val rows = snapshot.expenses.filter { it.date in month }
        if (rows.isNotEmpty()) {
            val spent = rows.sumOf { it.signedCents }
            val before = month.previous(settings.payday)
            val spentBefore = snapshot.expenses.filter { it.date in before }.sumOf { it.signedCents }
            result += MonthTrophy(
                month = month,
                budgetCents = settings.monthlyBudgetCents,
                spentCents = spent,
                billsCents = rows.filter { it.recurring }.sumOf { it.signedCents },
                underBudget = spent <= settings.monthlyBudgetCents,
                goldDays = gold.count { it in month },
                vsPreviousCents = spent - spentBefore,
                streakAtEnd = timeline.after[month.endInclusive] ?: 0,
                badgesEarned = BadgeId.entries.filter { id -> badges[id]?.let { it in month } == true },
                towers = towerSummaries(snapshot, month, month.days, emptyList()),
            )
        }
        month = month.previous(settings.payday)
    }
    return result
}

data class SettleState(
    /** Positive: the other member owes the viewer. Negative: the viewer owes. Zero: all square. */
    val balanceCents: Long,
    val partner: Member?,
    /** Unsettled shared bricks the viewer paid, newest first. */
    val youPaid: List<Expense>,
    /** Unsettled shared bricks the other member paid, newest first. */
    val partnerPaid: List<Expense>,
    /** The newest settledAt in the town, for "Settled · 24 Nov". */
    val lastSettledAt: Instant?,
)

/** balance = Σ(viewer's unsettled shared, signed) / 2 − Σ(partner's unsettled shared, signed) / 2, over bricks dated up to [today]. */
fun settleStateOf(snapshot: TownSnapshot, today: LocalDate): SettleState {
    val partner = snapshot.members.firstOrNull { it.userId != snapshot.viewerId }
    val lastSettled = snapshot.expenses.mapNotNull { it.settledAt }.maxOrNull()
    if (partner == null) return SettleState(0L, null, emptyList(), emptyList(), lastSettled)
    val open = snapshot.expenses
        .filter { it.shared && it.settledAt == null && it.date <= today }
        .sortedWith(compareByDescending<Expense> { it.date }.thenByDescending { it.createdAt })
    val (mine, theirs) = open.partition { it.paidBy == snapshot.viewerId }
    return SettleState(
        balanceCents = (mine.sumOf { it.signedCents } - theirs.sumOf { it.signedCents }) / 2,
        partner = partner,
        youPaid = mine,
        partnerPaid = theirs,
        lastSettledAt = lastSettled,
    )
}

/**
 * The viewer's side of one shared brick on Settle up: plus half when the viewer paid it, minus half when the
 * partner did. Rounds toward zero, so on odd cents the rows can sum one cent away from [SettleState.balanceCents].
 */
fun settleShareCents(expense: Expense, viewerId: String): Long {
    val half = expense.signedCents / 2
    return if (expense.paidBy == viewerId) half else -half
}
