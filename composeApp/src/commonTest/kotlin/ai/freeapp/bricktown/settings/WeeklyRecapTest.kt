package ai.freeapp.bricktown.settings

import ai.freeapp.bricktown.MemoryTokenStore
import ai.freeapp.bricktown.core.auth.AuthTokens
import ai.freeapp.bricktown.core.auth.ObservableTokenStore
import ai.freeapp.bricktown.core.flags.AppFlags
import ai.freeapp.bricktown.core.flags.Flag
import ai.freeapp.bricktown.core.flags.FlagCache
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What the platform would hold: the booked Sundays, or empty once cancelled. */
class RecordingRecapScheduler : RecapScheduler {
    var booked: List<LocalDate> = emptyList()
    var cancels = 0

    override fun book(sundays: List<LocalDate>) {
        booked = sundays
    }

    override fun cancel() {
        booked = emptyList()
        cancels++
    }
}

fun flagsWith(vararg states: Pair<String, Boolean>): AppFlags {
    val cache = object : FlagCache {
        override fun read() = states.toMap()
        override fun write(flags: Map<String, Boolean>) = Unit
    }
    return AppFlags(source = { emptyMap() }, cache = cache)
}

class WeeklyRecapTest {
    private val scheduler = RecordingRecapScheduler()
    private val tokens = ObservableTokenStore(MemoryTokenStore()).apply { save(AuthTokens("a", "r")) }
    private val prefs = FakePennyPrefs()

    // Wednesday 2026-11-25 09:00 UTC.
    private val now = Instant.parse("2026-11-25T09:00:00Z")

    private fun reminder(flags: AppFlags = flagsWith()) =
        WeeklyRecapReminder(scheduler, prefs, flags, tokens, clock = object : Clock { override fun now() = now }, zone = { TimeZone.UTC })

    @Test
    fun booksTheNextTwoSundaysWhenTheSwitchAndTheFlagAreOn() {
        reminder().restore()
        assertEquals(listOf(LocalDate(2026, 11, 29), LocalDate(2026, 12, 6)), scheduler.booked)
    }

    @Test
    fun theSwitchOffBooksNothing() {
        prefs.setWeeklyRecap(false)
        reminder().restore()
        assertTrue(scheduler.booked.isEmpty())
    }

    @Test
    fun theServerFlagOffBooksNothingAndCancelsWhatWasBooked() {
        reminder().restore()
        reminder(flagsWith(Flag.PENNY_RECAP to false)).restore()
        assertTrue(scheduler.booked.isEmpty())
        assertFalse(reminder(flagsWith(Flag.PENNY_RECAP to false)).allowed())
    }

    @Test
    fun noSessionBooksNothing() {
        tokens.clear()
        reminder().restore()
        assertTrue(scheduler.booked.isEmpty())
    }

    @Test
    fun endingTheSessionCancelsTheBooking() {
        reminder().restore()
        tokens.clear()
        assertTrue(scheduler.booked.isEmpty())
    }

    @Test
    fun turningTheSwitchOffCancelsAndOnBooksAgain() {
        val reminder = reminder()
        reminder.restore()
        reminder.turnOff()
        assertTrue(scheduler.booked.isEmpty())
        assertFalse(prefs.weeklyRecap.value)

        reminder.turnOn()
        assertEquals(2, scheduler.booked.size)
        assertTrue(prefs.weeklyRecap.value)
    }

    @Test
    fun anEmptyWeekGetsNoNote() {
        val town = townSnapshot(expenses = listOf(brick("old", "food", 500, LocalDate(2026, 11, 3))))
        assertNull(recapNoteFor(town, LocalDate(2026, 11, 16)))
        assertNull(recapNoteFor(null, LocalDate(2026, 11, 16)))
    }

    @Test
    fun aWeekWithABrickGetsFiveDsHeaderAndPennysLine() {
        val town = townSnapshot(expenses = listOf(brick("b1", "food", 31200, LocalDate(2026, 11, 18))))
        val note = assertNotNull(recapNoteFor(town, LocalDate(2026, 11, 16)))
        assertEquals("Week 47 · 16–22 Nov", note.title)
        assertEquals(LocalDate(2026, 11, 16), note.weekStart)
        assertTrue(note.text.isNotBlank())
    }

    @Test
    fun aFutureSundayGetsTheHeaderAndAnHonestLine() {
        val note = recapPreviewNote(townSnapshot(), LocalDate(2026, 11, 23))
        assertEquals("Week 48 · 23–29 Nov", note.title)
        assertEquals("Your week is in. Tap to see how it went.", note.text)
    }
}
