package ai.freeapp.bricktown.reminders

import ai.freeapp.bricktown.MainActivity
import ai.freeapp.bricktown.R
import ai.freeapp.bricktown.core.time.TownClock
import ai.freeapp.bricktown.settings.RecapScheduler
import ai.freeapp.bricktown.settings.TownGateway
import ai.freeapp.bricktown.settings.WeeklyRecapReminder
import ai.freeapp.bricktown.settings.recapNoteFor
import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * The Sunday 18:00 recap alarm, shaped like the evening nudge: one inexact
 * 10-minute window, booked again by [WeeklyRecapReceiver] when it fires, after
 * a reboot and at app start. Its request code, notification id and channel
 * differ from the nudge's, so neither replaces the other.
 */
class AndroidRecapScheduler(private val context: Context) : RecapScheduler {

    private val alarms = context.getSystemService(AlarmManager::class.java)

    override fun book(sundays: List<LocalDate>) {
        val first = sundays.firstOrNull() ?: return cancel()
        val at = LocalDateTime(first, LocalTime(RecapTime.hour, RecapTime.minute)).toInstant(TimeZone.currentSystemDefault())
        alarms.setWindow(AlarmManager.RTC_WAKEUP, at.toEpochMilliseconds(), WINDOW_MILLIS, alarmIntent())
    }

    override fun cancel() {
        alarms.cancel(alarmIntent())
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    // The same intent every time, so booking again replaces the alarm rather than adding one.
    private fun alarmIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, WeeklyRecapReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        /** The nudge uses alarm request code 0 and notification id 1. */
        const val REQUEST_CODE = 100
        const val NOTIFICATION_ID = 100
        private const val WINDOW_MILLIS = 10 * 60 * 1000L
    }
}

/** The recap alarm rang: post the note for the week that just ended, then book next Sunday. */
class WeeklyRecapReceiver : BroadcastReceiver(), KoinComponent {

    private val recap: WeeklyRecapReminder by inject()
    private val gateway: TownGateway by inject()
    private val clock: TownClock by inject()

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                // Switch, flag and session are read now, not when the alarm was booked.
                if (recap.allowed()) WeeklyRecapNotifier.show(context, gateway, clock)
            } finally {
                recap.restore()
                pending.finish()
            }
        }
    }
}

internal object WeeklyRecapNotifier {
    private const val CHANNEL_ID = "weekly_recap"

    suspend fun show(context: Context, gateway: TownGateway, clock: TownClock) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val town = gateway.town.first() ?: return
        val weekStart = recapWeekStartFor(clock.today(town.town.settings))
        val note = recapNoteFor(town, weekStart) ?: return
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Weekly recap", NotificationManager.IMPORTANCE_DEFAULT),
        )
        // DeepLinks.parse reads bricktown://recap/<Monday>.
        val open = PendingIntent.getActivity(
            context,
            AndroidRecapScheduler.REQUEST_CODE,
            Intent(Intent.ACTION_VIEW, Uri.parse("bricktown://recap/$weekStart"), context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(note.title)
            .setContentText(note.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(note.text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(AndroidRecapScheduler.NOTIFICATION_ID, notification)
    }
}
