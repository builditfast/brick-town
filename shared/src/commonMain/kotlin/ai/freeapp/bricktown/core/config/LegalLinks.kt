package ai.freeapp.bricktown.core.config

/**
 * The store pages the server publishes (`server/.../legal/LegalRoutes.kt`).
 * Screens open them in the browser; Play and the App Store ask for both URLs.
 */
object LegalLinks {
    val privacyPolicy: String get() = "${NetworkConfig.BASE_URL}/privacy"
    val termsOfUse: String get() = "${NetworkConfig.BASE_URL}/terms"
}
