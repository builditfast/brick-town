package ai.freeapp.bricktown.reminders

import ai.freeapp.bricktown.core.flags.AppFlags
import ai.freeapp.bricktown.core.flags.Flag
import ai.freeapp.bricktown.core.ui.IosNotificationAccess
import ai.freeapp.bricktown.core.ui.NotificationAccess
import ai.freeapp.bricktown.domain.TownSnapshot
import ai.freeapp.bricktown.domain.loggedDays
import ai.freeapp.bricktown.settings.NUDGE_ACTION_ADD_BRICK
import ai.freeapp.bricktown.settings.NUDGE_ACTION_ZERO_DAY
import ai.freeapp.bricktown.settings.PennyPrefs
import ai.freeapp.bricktown.settings.RecapNote
import ai.freeapp.bricktown.settings.RecapScheduler
import ai.freeapp.bricktown.settings.eveningNudgeFor
import ai.freeapp.bricktown.settings.futureEveningNudge
import ai.freeapp.bricktown.settings.recapNoteFor
import ai.freeapp.bricktown.settings.recapPreviewNote
import ai.freeapp.bricktown.settings.TownGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.dsl.binds
import org.koin.dsl.module
import platform.Foundation.NSDateComponents
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationAction
import platform.UserNotifications.UNNotificationActionOptionForeground
import platform.UserNotifications.UNNotificationActionOptionNone
import platform.UserNotifications.UNNotificationCategory
import platform.UserNotifications.UNNotificationCategoryOptionNone
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter

internal const val NUDGE_CATEGORY = "nudge"
internal const val ACTION_ZERO_DAY = "zero_day"
internal const val ACTION_ADD_BRICK = "add_brick"
internal const val LINK_KEY = "link"
private const val NUDGE_PREFIX = "nudge-"
private const val RECAP_PREFIX = "recap-"

/**
 * iOS keeps no alarm to re-arm, so the app books dated requests instead: the
 * next 7 evening nudges and the next 2 Sunday recaps. Which dates, and which
 * are skipped as already logged, is the common [nudgeDatesToBook] and
 * [recapSundaysToBook]. Everything is booked again when the app opens
 * (`startBackgroundWork`) and whenever the town changes, so a brick added today
 * removes today's request. All work runs on one thread so a town change never
 * interleaves with a switch.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class IosReminders(
    private val gateway: TownGateway,
    private val flags: AppFlags,
    private val prefs: PennyPrefs,
) : ReminderScheduler, RecapScheduler {

    private val center = UNUserNotificationCenter.currentNotificationCenter()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
    private var town: TownSnapshot? = null
    private var nudgeTime: ReminderTime? = null
    private var recapSundays: List<LocalDate> = emptyList()

    init {
        scope.launch {
            gateway.town.collect {
                town = it
                rebook()
            }
        }
    }

    override fun scheduleDaily(time: ReminderTime) {
        scope.launch {
            nudgeTime = time
            rebook()
        }
    }

    override fun cancelAll() {
        scope.launch {
            nudgeTime = null
            rebook()
        }
    }

    override fun book(sundays: List<LocalDate>) {
        scope.launch {
            recapSundays = sundays
            rebook()
        }
    }

    override fun cancel() {
        scope.launch {
            recapSundays = emptyList()
            rebook()
        }
    }

    private fun rebook() {
        val now = Clock.System.now()
        val zone = TimeZone.currentSystemDefault()
        val snapshot = town
        val nudges = nudgeTime?.takeIf { flags.isOn(Flag.PENNY_NUDGE) && snapshot != null }?.let { time ->
            nudgeDatesToBook(now, zone, time, loggedDays(snapshot!!)).map { date -> nudgeRequest(date, time, snapshot, now, zone) }
        }.orEmpty()
        val recaps = if (flags.isOn(Flag.PENNY_RECAP) && prefs.weeklyRecap.value && snapshot != null) {
            val today = now.toLocalDateTime(zone).date
            recapSundays.mapNotNull { sunday -> recapRequest(sunday, snapshot, today) }
        } else {
            emptyList()
        }
        // Replace, not add: old dated requests are dropped first, inside the callback so the order holds.
        center.getPendingNotificationRequestsWithCompletionHandler { pending ->
            val stale = pending.orEmpty().mapNotNull { (it as? UNNotificationRequest)?.identifier }
                .filter { it.startsWith(NUDGE_PREFIX) || it.startsWith(RECAP_PREFIX) }
            center.removePendingNotificationRequestsWithIdentifiers(stale)
            (nudges + recaps).forEach { center.addNotificationRequest(it, withCompletionHandler = null) }
        }
    }

    private fun nudgeRequest(date: LocalDate, time: ReminderTime, snapshot: TownSnapshot, now: Instant, zone: TimeZone): UNNotificationRequest {
        // Only today can name a streak number; later days have not happened yet.
        val nudge = if (date == now.toLocalDateTime(zone).date) eveningNudgeFor(snapshot, date) ?: futureEveningNudge() else futureEveningNudge()
        val content = UNMutableNotificationContent().apply {
            setTitle(nudge.title)
            setBody(nudge.body)
            setSound(UNNotificationSound.defaultSound())
            setCategoryIdentifier(NUDGE_CATEGORY)
        }
        return UNNotificationRequest.requestWithIdentifier("$NUDGE_PREFIX$date", content, trigger(date, time.hour, time.minute))
    }

    private fun recapRequest(sunday: LocalDate, snapshot: TownSnapshot, today: LocalDate): UNNotificationRequest? {
        val weekStart = recapWeekStartFor(sunday)
        // The week is only known when it is the one ending today; a future week is booked and judged when the app next opens.
        val note: RecapNote = if (sunday == today) recapNoteFor(snapshot, weekStart) ?: return null else recapPreviewNote(snapshot, weekStart)
        val content = UNMutableNotificationContent().apply {
            setTitle(note.title)
            setBody(note.text)
            setSound(UNNotificationSound.defaultSound())
            setUserInfo(mapOf(LINK_KEY to "bricktown://recap/$weekStart"))
        }
        return UNNotificationRequest.requestWithIdentifier("$RECAP_PREFIX$sunday", content, trigger(sunday, RecapTime.hour, RecapTime.minute))
    }

    private fun trigger(date: LocalDate, hour: Int, minute: Int): UNCalendarNotificationTrigger {
        val at = NSDateComponents().apply {
            setYear(date.year.toLong())
            setMonth(date.monthNumber.toLong())
            setDay(date.dayOfMonth.toLong())
            setHour(hour.toLong())
            setMinute(minute.toLong())
        }
        return UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(at, repeats = false)
    }

    /** The 4b buttons, registered once at launch (see [NotificationActions]). */
    internal companion object {
        fun category(): UNNotificationCategory = UNNotificationCategory.categoryWithIdentifier(
            NUDGE_CATEGORY,
            actions = listOf(
                UNNotificationAction.actionWithIdentifier(ACTION_ZERO_DAY, NUDGE_ACTION_ZERO_DAY, UNNotificationActionOptionNone),
                UNNotificationAction.actionWithIdentifier(ACTION_ADD_BRICK, NUDGE_ACTION_ADD_BRICK, UNNotificationActionOptionForeground),
            ),
            intentIdentifiers = emptyList<Any>(),
            options = UNNotificationCategoryOptionNone,
        )
    }
}

/** One object is both schedulers, so the nudge and the recap rebook from the same town. */
val iosRemindersModule = module {
    single { IosReminders(get(), get(), get()) } binds arrayOf(ReminderScheduler::class, RecapScheduler::class)
    single<NotificationAccess> { IosNotificationAccess() }
}
