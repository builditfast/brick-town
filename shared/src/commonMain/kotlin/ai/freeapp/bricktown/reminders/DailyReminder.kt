package ai.freeapp.bricktown.reminders

import ai.freeapp.bricktown.core.auth.ObservableTokenStore
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/** A time of day on the 24-hour clock, like 08:30. */
data class ReminderTime(val hour: Int, val minute: Int) {
    init {
        require(hour in 0..23 && minute in 0..59) { "Not a time of day: $hour:$minute" }
    }

    val minuteOfDay: Int get() = hour * 60 + minute

    /** The first moment after [now] that shows this time in [zone]: later today, or tomorrow. */
    fun nextAfter(now: Instant, zone: TimeZone): Instant {
        val today = now.toLocalDateTime(zone).date
        val at = LocalTime(hour, minute)
        val todayAt = LocalDateTime(today, at).toInstant(zone)
        return if (todayAt > now) todayAt else LocalDateTime(today.plus(1, DateTimeUnit.DAY), at).toInstant(zone)
    }

    override fun toString(): String = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

    companion object {
        private val PATTERN = Regex("""([01]?\d|2[0-3]):([0-5]\d)""")

        /** "8:30" and "08:30" are 08:30; anything else is null. */
        fun parse(text: String): ReminderTime? =
            PATTERN.matchEntire(text.trim())?.let { ReminderTime(it.groupValues[1].toInt(), it.groupValues[2].toInt()) }

        fun fromMinuteOfDay(minuteOfDay: Int): ReminderTime? =
            minuteOfDay.takeIf { it in 0 until 24 * 60 }?.let { ReminderTime(it / 60, it % 60) }
    }
}

/**
 * Books the platform's daily notification. Android: `AndroidReminderScheduler`
 * (an inexact AlarmManager window, booked again each time it fires); iOS:
 * `IosReminders` (dated requests booked ahead). Each platform
 * registers its own in Koin; [DailyReminder] is the only caller.
 */
interface ReminderScheduler {
    /** Replaces any booked reminder with one at [time], every day. */
    fun scheduleDaily(time: ReminderTime)

    /** Removes the booked reminder and any reminder still on screen. */
    fun cancelAll()
}

/** Where the reminder time lives between launches. Null means off. */
interface ReminderStore {
    fun read(): ReminderTime?
    fun write(time: ReminderTime?)
}

/** On the phone only (SharedPreferences / NSUserDefaults); the server never sees it. */
class SettingsReminderStore(private val settings: Settings = Settings()) : ReminderStore {
    override fun read(): ReminderTime? = settings.getIntOrNull(KEY)?.let(ReminderTime::fromMinuteOfDay)

    override fun write(time: ReminderTime?) {
        if (time == null) settings.remove(KEY) else settings.putInt(KEY, time.minuteOfDay)
    }

    private companion object {
        const val KEY = "reminder.daily.minuteOfDay"
    }
}

/**
 * Anything that books a notification and must book it again at app start,
 * after a reboot and when its alarm fires. `startBackgroundWork` and the boot
 * receiver restore every binding of this type, so a new reminder needs no
 * edit in either.
 */
fun interface ReminderRestorer {
    fun restore()
}

/**
 * The app's daily reminder: the setting the Settings switch shows and the
 * notification the platform books for it, kept equal. A screen never calls
 * the scheduler itself.
 *
 * - [turnOn] only after the notification permission is granted
 *   (`rememberNotificationPermissionRequest`), so the switch is never on while
 *   nothing can ring.
 * - [restore] runs at app start (`startBackgroundWork`), after a reboot, and
 *   each time the Android alarm fires, because that alarm is one-shot and
 *   Android forgets every alarm on reboot.
 * - The setting belongs to the session: [turnOff] is registered on the token
 *   store, so every session end and start cancels the reminder and forgets it.
 *
 * EXTENSION POINT: a reminder per entity (per habit, per plant) keeps this
 * shape with an id: `turnOn(id, time)`, one alarm per id (a distinct
 * PendingIntent data URI on Android, a distinct request identifier on iOS).
 */
class DailyReminder(
    private val scheduler: ReminderScheduler,
    private val store: ReminderStore,
    private val tokens: ObservableTokenStore,
) : ReminderRestorer {
    private val _time = MutableStateFlow(store.read())

    /** When the reminder rings each day; null while it is off. */
    val time: StateFlow<ReminderTime?> = _time.asStateFlow()

    init {
        tokens.doOnClear(::turnOff)
    }

    fun turnOn(at: ReminderTime) {
        store.write(at)
        scheduler.scheduleDaily(at)
        _time.value = at
    }

    fun turnOff() {
        scheduler.cancelAll()
        store.write(null)
        _time.value = null
    }

    /**
     * Books the saved reminder again. With no session the saved time is left
     * over from one that ended without a clear, so it is wiped instead.
     */
    override fun restore() {
        val saved = store.read()
        if (saved != null && tokens.signedIn.value) {
            scheduler.scheduleDaily(saved)
            _time.value = saved
        } else {
            turnOff()
        }
    }
}
