package ai.freeapp.bricktown.reminders

import ai.freeapp.bricktown.domain.lastCompletedWeekStart
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * Which days the platforms book a notification for. Pure, so one set of tests
 * covers Android's one-shot alarm and iOS's dated requests alike.
 */

/** The weekly recap rings at 18:00 on Sunday (spec 5.7, frame 5d). */
val RecapTime = ReminderTime(18, 0)

/** iOS books this many evening nudges ahead (spec 5.8). */
const val NUDGE_DAYS_AHEAD = 7

/** iOS books this many recap Sundays ahead. */
const val RECAP_SUNDAYS_AHEAD = 2

private fun at(date: LocalDate, time: ReminderTime, zone: TimeZone): Instant =
    LocalDateTime(date, LocalTime(time.hour, time.minute)).toInstant(zone)

/**
 * The first Sunday 18:00 after [now] in [zone]: today's when it is Sunday and
 * 18:00 has not come, otherwise next Sunday's. A DST change on that Sunday
 * moves the instant, not the wall clock time.
 */
fun nextRecapAfter(now: Instant, zone: TimeZone): Instant = recapSundaysToBook(now, zone, 1).first().let { at(it, RecapTime, zone) }

/** The next [count] Sundays whose 18:00 is still ahead of [now]. */
fun recapSundaysToBook(now: Instant, zone: TimeZone, count: Int = RECAP_SUNDAYS_AHEAD): List<LocalDate> {
    val today = now.toLocalDateTime(zone).date
    val firstSunday = today.plus((DayOfWeek.SUNDAY.ordinal - today.dayOfWeek.ordinal).mod(7), DateTimeUnit.DAY)
    return generateSequence(firstSunday) { it.plus(7, DateTimeUnit.DAY) }
        .filter { at(it, RecapTime, zone) > now }
        .take(count)
        .toList()
}

/**
 * The Monday of the week a recap that fires on [fireDate] describes: the week
 * ending that Sunday. An alarm that ran past midnight (a Monday) still
 * describes the week that just ended.
 */
fun recapWeekStartFor(fireDate: LocalDate): LocalDate =
    if (fireDate.dayOfWeek == DayOfWeek.SUNDAY) fireDate.minus(6, DateTimeUnit.DAY) else lastCompletedWeekStart(fireDate)

/**
 * The days iOS gets a dated evening nudge for: the next [count] days whose
 * [time] is still ahead of [now], minus the days already in [loggedDays]
 * (a brick or a gold mark exists). Re-run on every app open and every town
 * change, so a brick added today removes today's request.
 */
fun nudgeDatesToBook(
    now: Instant,
    zone: TimeZone,
    time: ReminderTime,
    loggedDays: Set<LocalDate>,
    count: Int = NUDGE_DAYS_AHEAD,
): List<LocalDate> {
    val today = now.toLocalDateTime(zone).date
    return generateSequence(today) { it.plus(1, DateTimeUnit.DAY) }
        .filter { at(it, time, zone) > now }
        .take(count)
        .filter { it !in loggedDays }
        .toList()
}
