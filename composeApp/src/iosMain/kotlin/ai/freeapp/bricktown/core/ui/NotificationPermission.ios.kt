package ai.freeapp.bricktown.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.launch
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNUserNotificationCenter

@Composable
actual fun rememberNotificationPermissionRequest(onResult: (granted: Boolean) -> Unit): () -> Unit {
    val answer by rememberUpdatedState(onResult)
    // The completion handler runs on a background queue; the answer goes back
    // to the composition's own (main) thread.
    val scope = rememberCoroutineScope()
    return remember(scope) {
        {
            UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(
                UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge,
            ) { granted, _ ->
                scope.launch { answer(granted) }
            }
        }
    }
}
