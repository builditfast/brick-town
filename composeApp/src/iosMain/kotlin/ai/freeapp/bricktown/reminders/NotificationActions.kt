package ai.freeapp.bricktown.reminders

import ai.freeapp.bricktown.DeepLinkInbox
import ai.freeapp.bricktown.core.time.TownClock
import ai.freeapp.bricktown.settings.TownGateway
import ai.freeapp.bricktown.settings.markZeroDay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform

import platform.UserNotifications.UNNotification
import platform.UserNotifications.UNNotificationDefaultActionIdentifier
import platform.UserNotifications.UNNotificationPresentationOptionBanner
import platform.UserNotifications.UNNotificationPresentationOptionSound
import platform.UserNotifications.UNNotificationPresentationOptions
import platform.UserNotifications.UNNotificationResponse
import platform.UserNotifications.UNUserNotificationCenter
import platform.UserNotifications.UNUserNotificationCenterDelegateProtocol
import platform.darwin.NSObject

/**
 * Answers taps on Brick Town notifications (4b and 5d): "Add a brick" opens
 * `bricktown://add`, "€0 day" marks today gold the way Android's
 * ZeroDayReceiver does, and a tap on a recap opens its `link`.
 */
internal class NotificationActions : NSObject(), UNUserNotificationCenterDelegateProtocol {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun userNotificationCenter(
        center: UNUserNotificationCenter,
        didReceiveNotificationResponse: UNNotificationResponse,
        withCompletionHandler: () -> Unit,
    ) {
        val koin = KoinPlatform.getKoin()
        when (didReceiveNotificationResponse.actionIdentifier) {
            ACTION_ADD_BRICK -> {
                koin.get<DeepLinkInbox>().deliver("bricktown://add")
                withCompletionHandler()
            }
            ACTION_ZERO_DAY -> scope.launch {
                runCatching { markZeroDay(koin.get<TownGateway>(), koin.get<TownClock>()) }
                withCompletionHandler()
            }
            UNNotificationDefaultActionIdentifier -> {
                val link = didReceiveNotificationResponse.notification.request.content.userInfo[LINK_KEY] as? String
                if (link != null) koin.get<DeepLinkInbox>().deliver(link)
                withCompletionHandler()
            }
            else -> withCompletionHandler()
        }
    }

    // Without this iOS drops a notification that arrives while the app is open.
    override fun userNotificationCenter(
        center: UNUserNotificationCenter,
        willPresentNotification: UNNotification,
        withCompletionHandler: (UNNotificationPresentationOptions) -> Unit,
    ) {
        withCompletionHandler(UNNotificationPresentationOptionBanner or UNNotificationPresentationOptionSound)
    }
}

// The center holds its delegate weakly, so this top-level value keeps it alive.
private val delegate = NotificationActions()

/** Called once at launch, before the first notification can be answered. */
internal fun registerNotificationActions() {
    val center = UNUserNotificationCenter.currentNotificationCenter()
    center.setDelegate(delegate)
    center.setNotificationCategories(setOf(IosReminders.category()))
}
