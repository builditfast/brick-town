package ai.freeapp.bricktown.core.ui

import androidx.compose.runtime.Composable

/**
 * Returns a function that asks to show notifications and passes the answer
 * to [onResult]. Call it from the tap that turns a reminder on, never on
 * screen open: Android 13+ and iOS show their prompt once per install, and an
 * unexplained prompt at launch is the one people refuse.
 *
 * Android 13+ shows the system prompt (or answers at once when the user has
 * already decided); below 13 there is no prompt and the answer is whether
 * notifications are on in settings. iOS asks `UNUserNotificationCenter`.
 */
@Composable
expect fun rememberNotificationPermissionRequest(onResult: (granted: Boolean) -> Unit): () -> Unit
