package ai.freeapp.bricktown.wire

import ai.freeapp.bricktown.domain.Currency
import ai.freeapp.bricktown.domain.TownSnapshot
import ai.freeapp.bricktown.domain.englishMonthName
import ai.freeapp.bricktown.domain.streakOf
import ai.freeapp.bricktown.domain.summarize
import kotlinx.datetime.LocalDate

/**
 * What GET /town/summary answers, built from summarize() and streakOf() so the
 * voice agent and the Month screen read the same numbers.
 *
 * [AgentSummary.sentence] is spoken aloud: digits without grouping, the
 * currency as a word (euros, dollars, pounds, lei; "1 euro" singular), no
 * symbols. Under budget: "You have 57 euros left today and 1442 for the
 * month." Over today: "You are 12 euros over today, with 488 euros left for
 * the month." Over the month too: "..., and 40 euros over for the month."
 */
fun agentSummaryOf(snapshot: TownSnapshot, today: LocalDate): AgentSummary {
    val settings = snapshot.town.settings
    val summary = summarize(snapshot, today)
    return AgentSummary(
        date = today,
        monthName = englishMonthName(summary.month.name),
        dayNumber = summary.dayNumber,
        currency = settings.currency.name,
        budgetCents = settings.monthlyBudgetCents,
        spentCents = summary.spentCents,
        billsCents = summary.billsCents,
        dailyBudgetCents = summary.dailyBudgetCents,
        todaySpentCents = summary.todaySpentCents,
        todayLeftCents = summary.todayLeftCents,
        monthLeftCents = summary.monthLeftCents,
        aheadCents = summary.aheadCents,
        streak = streakOf(snapshot, today).current,
        towers = summary.towers.map { AgentTower(it.category.id, it.category.name, it.totalCents, it.bricks, it.category.capCents) },
        sentence = spokenSentence(summary.todayLeftCents, summary.monthLeftCents, settings.currency),
    )
}

private fun spokenSentence(todayLeft: Long, monthLeft: Long, currency: Currency): String {
    val todayPart = if (todayLeft >= 0) "You have ${money(todayLeft, currency)} left today" else "You are ${over(todayLeft, currency)} over today"
    val monthPart = when {
        todayLeft >= 0 && monthLeft >= 0 -> "and ${units(monthLeft)} for the month"
        todayLeft >= 0 -> "but ${over(monthLeft, currency)} over for the month"
        monthLeft >= 0 -> "with ${money(monthLeft, currency)} left for the month"
        else -> "and ${over(monthLeft, currency)} over for the month"
    }
    val separator = if (todayLeft >= 0 && monthLeft >= 0) " " else ", "
    return "$todayPart$separator$monthPart."
}

/** Whole units, half up, no grouping: speech engines read "1442" better than "1 442". */
private fun units(cents: Long): Long = (cents + 50) / 100

private fun money(cents: Long, currency: Currency): String = word(units(cents), currency)

/** Over amounts never round down to "0 euros over". */
private fun over(cents: Long, currency: Currency): String = word(maxOf(1L, units(-cents)), currency)

private fun word(amount: Long, currency: Currency): String {
    val single = amount == 1L
    val name = when (currency) {
        Currency.EUR -> if (single) "euro" else "euros"
        Currency.USD -> if (single) "dollar" else "dollars"
        Currency.GBP -> if (single) "pound" else "pounds"
        Currency.RON -> if (single) "leu" else "lei"
    }
    return "$amount $name"
}
