package ai.freeapp.bricktown.reminders

import ai.freeapp.bricktown.MainActivity
import ai.freeapp.bricktown.R
import ai.freeapp.bricktown.core.time.TownClock
import ai.freeapp.bricktown.settings.NUDGE_ACTION_ADD_BRICK
import ai.freeapp.bricktown.settings.NUDGE_ACTION_ZERO_DAY
import ai.freeapp.bricktown.settings.TownGateway
import ai.freeapp.bricktown.settings.eveningNudgeFor
import ai.freeapp.bricktown.settings.markZeroDay
import android.Manifest
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
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** The evening nudge (4b): decides at the moment the alarm rings whether today still needs one. */
internal object EveningNudgeNotifier {
    private const val CHANNEL_ID = "daily_reminder"

    suspend fun show(context: Context, gateway: TownGateway, clock: TownClock) {
        // Refused or revoked in system settings: posting would be dropped anyway.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val town = gateway.town.first()
        val nudge = eveningNudgeFor(town, clock.today(town?.town?.settings ?: return)) ?: return
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Daily reminder", NotificationManager.IMPORTANCE_DEFAULT),
        )
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        // The numpad: DeepLinks.parse reads bricktown://add.
        val addBrick = PendingIntent.getActivity(
            context,
            2,
            Intent(Intent.ACTION_VIEW, Uri.parse("bricktown://add"), context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // A broadcast, not an activity: the gold mark is written without the app coming to the front.
        val zeroDay = PendingIntent.getBroadcast(
            context,
            1,
            Intent(context, ZeroDayReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(nudge.title)
            .setContentText(nudge.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(nudge.body))
            .setContentIntent(open)
            .addAction(0, NUDGE_ACTION_ZERO_DAY, zeroDay)
            .addAction(0, NUDGE_ACTION_ADD_BRICK, addBrick)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(ReminderAlarmReceiver.NOTIFICATION_ID, notification)
    }
}

/**
 * The nudge's "€0 day" button: writes today's gold mark with no screen opening.
 * Not exported; only the notification's own PendingIntent reaches it.
 */
class ZeroDayReceiver : BroadcastReceiver(), KoinComponent {

    private val gateway: TownGateway by inject()
    private val clock: TownClock by inject()

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            val done = runCatching { markZeroDay(gateway, clock) }.isSuccess
            // A failed write leaves the notification up so the button can be tapped again.
            if (done) NotificationManagerCompat.from(context).cancel(ReminderAlarmReceiver.NOTIFICATION_ID)
            pending.finish()
        }
    }
}
