package ai.freeapp.bricktown.core.ui

import android.content.Context
import androidx.core.app.NotificationManagerCompat

/** False below Android 13 when notifications are off in settings, and on 13+ until POST_NOTIFICATIONS is granted. */
class AndroidNotificationAccess(private val context: Context) : NotificationAccess {
    override suspend fun allowed(): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()
}
