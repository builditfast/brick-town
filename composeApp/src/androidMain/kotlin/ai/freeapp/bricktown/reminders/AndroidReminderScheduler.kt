package ai.freeapp.bricktown.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone

/**
 * One alarm, booked one day at a time: [ReminderAlarmReceiver] books the next
 * one when it fires, and `DailyReminder.restore()` books it at app start and
 * after a reboot.
 *
 * Inexact on purpose. Exact alarms need SCHEDULE_EXACT_ALARM or
 * USE_EXACT_ALARM, which Play grants only to alarm-clock and calendar apps, so
 * the template never asks for either. A window alarm still rings within
 * minutes of the time, where `setInexactRepeating` can drift by an hour. API
 * 31+ stretches any window under 10 minutes to 10, so 10 is the honest value.
 */
class AndroidReminderScheduler(private val context: Context) : ReminderScheduler {

    private val alarms = context.getSystemService(AlarmManager::class.java)

    override fun scheduleDaily(time: ReminderTime) {
        val next = time.nextAfter(Clock.System.now(), TimeZone.currentSystemDefault())
        alarms.setWindow(AlarmManager.RTC_WAKEUP, next.toEpochMilliseconds(), WINDOW_MILLIS, alarmIntent())
    }

    override fun cancelAll() {
        alarms.cancel(alarmIntent())
        NotificationManagerCompat.from(context).cancel(ReminderAlarmReceiver.NOTIFICATION_ID)
    }

    // The same intent every time, so booking again replaces the alarm rather than adding one.
    private fun alarmIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, ReminderAlarmReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private companion object {
        const val WINDOW_MILLIS = 10 * 60 * 1000L
    }
}
