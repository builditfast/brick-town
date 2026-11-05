package ai.freeapp.bricktown.settings

import ai.freeapp.bricktown.core.auth.ObservableTokenStore
import ai.freeapp.bricktown.core.flags.AppFlags
import ai.freeapp.bricktown.core.flags.Flag
import ai.freeapp.bricktown.domain.TownSnapshot
import ai.freeapp.bricktown.domain.weekRecap
import ai.freeapp.bricktown.recap.weekRange
import ai.freeapp.bricktown.reminders.RECAP_SUNDAYS_AHEAD
import ai.freeapp.bricktown.reminders.ReminderRestorer
import ai.freeapp.bricktown.reminders.recapSundaysToBook
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/** What the Sunday 18:00 notification says (spec 5.7, frame 5d). */
data class RecapNote(val weekStart: LocalDate, val title: String, val text: String)

/**
 * The note for the week starting [weekStart], or null when that week has no
 * bricks: the same rule that hides History's recap card. The title is 5d's own
 * header ("Week 47 · 17–23 Nov") and the text is Penny's line from the same
 * frame, so the notification and the page it opens say the same thing.
 * No sentence in it is ours.
 */
fun recapNoteFor(town: TownSnapshot?, weekStart: LocalDate): RecapNote? {
    if (town == null) return null
    val recap = weekRecap(town, weekStart)
    if (town.expenses.none { it.date >= recap.start && it.date <= recap.end }) return null
    return RecapNote(
        weekStart = weekStart,
        title = "Week ${recap.weekNumber} · ${weekRange(recap.start, recap.end)}",
        text = recap.pennyLine,
    )
}

/**
 * The note for a Sunday that has not come, which iOS books ahead of time.
 * Title is the same header; the line cannot be Penny's (the week has not
 * happened), so it is ours: "Your week is in. Tap to see how it went."
 */
fun recapPreviewNote(town: TownSnapshot, weekStart: LocalDate): RecapNote {
    val recap = weekRecap(town, weekStart)
    return RecapNote(
        weekStart = weekStart,
        title = "Week ${recap.weekNumber} · ${weekRange(recap.start, recap.end)}",
        text = "Your week is in. Tap to see how it went.",
    )
}

/** Books the platform's Sunday notifications. Android keeps the first date; iOS keeps them all. */
interface RecapScheduler {
    fun book(sundays: List<LocalDate>)
    fun cancel()
}

/**
 * The weekly recap switch and the notification the platform books for it.
 * Booked only while a session exists, the Settings switch is on and the
 * server flag [Flag.PENNY_RECAP] is on; anything else cancels. Like
 * `DailyReminder`, a session end cancels the booking.
 */
class WeeklyRecapReminder(
    private val scheduler: RecapScheduler,
    private val prefs: PennyPrefs,
    private val flags: AppFlags,
    private val tokens: ObservableTokenStore,
    private val clock: Clock = Clock.System,
    private val zone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) : ReminderRestorer {

    init {
        tokens.doOnClear(scheduler::cancel)
    }

    /** True when a notification may be booked or shown right now. */
    fun allowed(): Boolean = tokens.signedIn.value && prefs.weeklyRecap.value && flags.isOn(Flag.PENNY_RECAP)

    fun turnOn() {
        prefs.setWeeklyRecap(true)
        restore()
    }

    fun turnOff() {
        prefs.setWeeklyRecap(false)
        scheduler.cancel()
    }

    override fun restore() {
        if (allowed()) {
            scheduler.book(recapSundaysToBook(clock.now(), zone(), RECAP_SUNDAYS_AHEAD))
        } else {
            scheduler.cancel()
        }
    }
}
