package ai.freeapp.bricktown.core.ui

/**
 * Whether the phone lets Brick Town show a notification right now. The user can
 * change it in the phone's settings while the app is away, so a screen asks
 * again on every resume instead of keeping the first answer.
 */
fun interface NotificationAccess {
    suspend fun allowed(): Boolean
}
