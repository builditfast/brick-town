package ai.freeapp.bricktown.core.ui

import kotlinx.coroutines.suspendCancellableCoroutine
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusEphemeral
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume

/** Not yet asked counts as not allowed: nothing can ring until the user says yes. */
class IosNotificationAccess : NotificationAccess {
    override suspend fun allowed(): Boolean = suspendCancellableCoroutine { answer ->
        UNUserNotificationCenter.currentNotificationCenter().getNotificationSettingsWithCompletionHandler { settings ->
            val status = settings?.authorizationStatus
            answer.resume(
                status == UNAuthorizationStatusAuthorized ||
                    status == UNAuthorizationStatusProvisional ||
                    status == UNAuthorizationStatusEphemeral,
            )
        }
    }
}
