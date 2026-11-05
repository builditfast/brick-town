package ai.freeapp.bricktown.reminders

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

class ReminderDatesTest {
    private val utc = TimeZone.UTC
    private val bucharest = TimeZone.of("Europe/Bucharest")

    @Test
    fun sundayBeforeSixIsToday() {
        // 2026-11-22 is a Sunday.
        assertEquals(Instant.parse("2026-11-22T18:00:00Z"), nextRecapAfter(Instant.parse("2026-11-22T09:00:00Z"), utc))
    }

    @Test
    fun sundayAfterSixIsNextSunday() {
        assertEquals(Instant.parse("2026-11-29T18:00:00Z"), nextRecapAfter(Instant.parse("2026-11-22T18:30:00Z"), utc))
        assertEquals(Instant.parse("2026-11-29T18:00:00Z"), nextRecapAfter(Instant.parse("2026-11-22T18:00:00Z"), utc))
    }

    @Test
    fun midweekIsTheComingSunday() {
        assertEquals(Instant.parse("2026-11-22T18:00:00Z"), nextRecapAfter(Instant.parse("2026-11-18T12:00:00Z"), utc))
    }

    @Test
    fun theWallClockHoldsAcrossTheAutumnClockChange() {
        // Bucharest leaves summer time on Sunday 25 Oct 2026 (UTC+3 to UTC+2): 18:00 is 16:00 UTC that day.
        assertEquals(Instant.parse("2026-10-25T16:00:00Z"), nextRecapAfter(Instant.parse("2026-10-24T12:00:00Z"), bucharest))
        // The Sunday before it was still summer time: 18:00 is 15:00 UTC.
        assertEquals(Instant.parse("2026-10-18T15:00:00Z"), nextRecapAfter(Instant.parse("2026-10-17T12:00:00Z"), bucharest))
    }

    @Test
    fun theWallClockHoldsAcrossTheSpringClockChange() {
        // Bucharest enters summer time on Sunday 29 Mar 2026 (UTC+2 to UTC+3): 18:00 is 15:00 UTC.
        assertEquals(Instant.parse("2026-03-29T15:00:00Z"), nextRecapAfter(Instant.parse("2026-03-28T12:00:00Z"), bucharest))
    }

    @Test
    fun theZoneDecidesWhichDayItIs() {
        // Sunday 23:30 UTC is already Monday in Bucharest, so the next Sunday is a week later.
        assertEquals(Instant.parse("2026-11-29T16:00:00Z"), nextRecapAfter(Instant.parse("2026-11-22T23:30:00Z"), bucharest))
    }

    @Test
    fun iosBooksTheNextTwoSundays() {
        assertEquals(
            listOf(LocalDate(2026, 11, 22), LocalDate(2026, 11, 29)),
            recapSundaysToBook(Instant.parse("2026-11-22T09:00:00Z"), utc),
        )
        assertEquals(
            listOf(LocalDate(2026, 11, 29), LocalDate(2026, 12, 6)),
            recapSundaysToBook(Instant.parse("2026-11-22T19:00:00Z"), utc),
        )
    }

    @Test
    fun theRecapDescribesTheWeekEndingThatSunday() {
        assertEquals(LocalDate(2026, 11, 16), recapWeekStartFor(LocalDate(2026, 11, 22)))
        // An alarm that ran into Monday still describes the week that just ended.
        assertEquals(LocalDate(2026, 11, 16), recapWeekStartFor(LocalDate(2026, 11, 23)))
    }

    @Test
    fun nudgeBooksSevenDaysAheadStartingTodayWhileTheTimeIsAhead() {
        val dates = nudgeDatesToBook(Instant.parse("2026-11-05T10:00:00Z"), utc, ReminderTime(20, 0), emptySet())
        assertEquals((5..11).map { LocalDate(2026, 11, it) }, dates)
    }

    @Test
    fun nudgeStartsTomorrowOnceTodaysTimeHasPassed() {
        val dates = nudgeDatesToBook(Instant.parse("2026-11-05T20:00:00Z"), utc, ReminderTime(20, 0), emptySet())
        assertEquals((6..12).map { LocalDate(2026, 11, it) }, dates)
    }

    @Test
    fun aLoggedDayGetsNoNudge() {
        val dates = nudgeDatesToBook(Instant.parse("2026-11-05T10:00:00Z"), utc, ReminderTime(20, 0), setOf(LocalDate(2026, 11, 5)))
        assertEquals((6..11).map { LocalDate(2026, 11, it) }, dates)
    }

    @Test
    fun nudgeDatesFollowTheMonthEnd() {
        val dates = nudgeDatesToBook(Instant.parse("2026-12-29T10:00:00Z"), utc, ReminderTime(20, 0), emptySet())
        assertEquals(LocalDate(2027, 1, 4), dates.last())
    }
}
