package ai.freeapp.bricktown.core.time

import ai.freeapp.bricktown.domain.TownSettings
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.test.Test
import kotlin.test.assertEquals

class TownClockTest {

    // 22:30 UTC on 30 Nov is already 1 Dec in Bucharest and still 30 Nov in New York.
    private val lateEvening = object : Clock {
        override fun now() = Instant.parse("2026-11-30T22:30:00Z")
    }

    private fun settings(zone: String) = TownSettings(monthlyBudgetCents = 180_000, timeZone = zone)

    @Test
    fun todayIsTheTownsDayNotTheUtcDay() {
        val clock = SystemTownClock(lateEvening)
        assertEquals(LocalDate(2026, 12, 1), clock.today(settings("Europe/Bucharest")))
        assertEquals(LocalDate(2026, 11, 30), clock.today(settings("America/New_York")))
    }

    @Test
    fun anUnknownZoneFallsBackToTheDevice() {
        val clock = SystemTownClock(lateEvening)
        assertEquals(lateEvening.todayIn(TimeZone.currentSystemDefault()), clock.today(settings("Mars/Olympus")))
    }
}
