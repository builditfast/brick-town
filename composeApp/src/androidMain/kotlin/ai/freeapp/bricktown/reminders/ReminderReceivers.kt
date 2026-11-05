package ai.freeapp.bricktown.reminders

import ai.freeapp.bricktown.core.flags.AppFlags
import ai.freeapp.bricktown.core.flags.Flag
import ai.freeapp.bricktown.core.time.TownClock
import ai.freeapp.bricktown.settings.TownGateway
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * The reminder's alarm went off: show the evening nudge when today is not logged, then book tomorrow.
 * `TemplateApp.onCreate` has started Koin before any receiver runs, also in a
 * process the system started only for this broadcast.
 */
class ReminderAlarmReceiver : BroadcastReceiver(), KoinComponent {

    private val reminder: DailyReminder by inject()
    private val gateway: TownGateway by inject()
    private val clock: TownClock by inject()
    private val flags: AppFlags by inject()

    override fun onReceive(context: Context, intent: Intent) {
        // The nudge reads the town from the local database, which can outlast the 10 s a receiver gets.
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                // Turned off, signed out, or the owner's penny.nudge flag switched off since the alarm was booked: nothing to show.
                if (reminder.time.value != null && flags.isOn(Flag.PENNY_NUDGE)) EveningNudgeNotifier.show(context, gateway, clock)
            } finally {
                reminder.restore()
                pending.finish()
            }
        }
    }

    companion object {
        const val NOTIFICATION_ID = 1
    }
}

/**
 * Android forgets every alarm on reboot and when the app is updated. This
 * books the reminders again with nobody opening the app.
 */
class ReminderBootReceiver : BroadcastReceiver(), KoinComponent {

    private val restorers: List<ReminderRestorer> by lazy { getKoin().getAll<ReminderRestorer>() }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            restorers.forEach { it.restore() }
        }
    }
}
