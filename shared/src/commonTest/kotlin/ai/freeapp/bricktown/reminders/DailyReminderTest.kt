package ai.freeapp.bricktown.reminders

import ai.freeapp.bricktown.core.sync.signedInTokens
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RecordingScheduler : ReminderScheduler {
    var booked: ReminderTime? = null
    var cancels = 0

    override fun scheduleDaily(time: ReminderTime) {
        booked = time
    }

    override fun cancelAll() {
        booked = null
        cancels++
    }
}

/** The saved time, in memory; the same instance across a "restart" stands for the phone's storage. */
class InMemoryReminderStore : ReminderStore {
    private var saved: ReminderTime? = null
    override fun read(): ReminderTime? = saved
    override fun write(time: ReminderTime?) {
        saved = time
    }
}

class DailyReminderTest {

    private val settings = InMemoryReminderStore()
    private val scheduler = RecordingScheduler()
    private val tokens = signedInTokens()
    private val reminder = DailyReminder(scheduler, settings, tokens)

    @Test
    fun turningItOnBooksAndRemembersTheTime() {
        reminder.turnOn(ReminderTime(8, 30))

        assertEquals(ReminderTime(8, 30), scheduler.booked)
        assertEquals(ReminderTime(8, 30), reminder.time.value)
        assertEquals(ReminderTime(8, 30), settings.read())
    }

    @Test
    fun turningItOffCancelsAndForgets() {
        reminder.turnOn(ReminderTime(8, 30))

        reminder.turnOff()

        assertNull(scheduler.booked)
        assertNull(reminder.time.value)
        assertNull(settings.read())
    }

    @Test
    fun theNextAppStartBooksTheSavedTimeAgain() {
        reminder.turnOn(ReminderTime(21, 5))
        // A reboot or a new process: a fresh scheduler, the same saved settings.
        val afterRestart = RecordingScheduler()

        val restored = DailyReminder(afterRestart, settings, tokens)
        restored.restore()

        assertEquals(ReminderTime(21, 5), afterRestart.booked)
        assertEquals(ReminderTime(21, 5), restored.time.value)
    }

    @Test
    fun sessionEndCancelsTheReminderAndForgetsIt() {
        reminder.turnOn(ReminderTime(8, 30))

        tokens.clear()

        assertNull(scheduler.booked)
        assertNull(reminder.time.value)
        assertNull(settings.read())
    }

    @Test
    fun aSavedTimeWithNoSessionIsWipedOnStartNotBooked() {
        settings.write(ReminderTime(7, 0))
        val signedOut = signedInTokens().apply { clear() }
        val afterRestart = RecordingScheduler()

        DailyReminder(afterRestart, settings, signedOut).restore()

        assertNull(afterRestart.booked)
        assertNull(settings.read())
    }
}

class ReminderTimeTest {

    @Test
    fun parsesTheTimesPeopleType() {
        assertEquals(ReminderTime(8, 30), ReminderTime.parse("8:30"))
        assertEquals(ReminderTime(8, 30), ReminderTime.parse(" 08:30 "))
        assertEquals(ReminderTime(23, 59), ReminderTime.parse("23:59"))
        for (bad in listOf("24:00", "08:60", "830", "8.30", "", "ab:cd")) assertNull(ReminderTime.parse(bad), bad)
    }

    @Test
    fun printsWithLeadingZeros() {
        assertEquals("08:05", ReminderTime(8, 5).toString())
    }

    @Test
    fun theNextRingIsLaterTodayOrElseTomorrow() {
        val now = Instant.parse("2026-10-21T10:00:00Z")

        assertEquals(Instant.parse("2026-10-21T10:02:00Z"), ReminderTime(10, 2).nextAfter(now, TimeZone.UTC))
        assertEquals(Instant.parse("2026-10-22T09:00:00Z"), ReminderTime(9, 0).nextAfter(now, TimeZone.UTC))
        assertEquals(Instant.parse("2026-10-22T10:00:00Z"), ReminderTime(10, 0).nextAfter(now, TimeZone.UTC))
    }
}
