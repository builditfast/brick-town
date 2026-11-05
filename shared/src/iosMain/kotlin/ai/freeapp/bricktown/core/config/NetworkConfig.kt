package ai.freeapp.bricktown.core.config

/**
 * The simulator's view of the dev machine. iOS has no build-type switch yet:
 * an App Store build needs its https:// server here, ideally read from an
 * Info.plist key set per Xcode configuration. Plain http works only because
 * `iosApp/iosApp/Info.plist` exempts localhost from App Transport Security.
 */
actual object NetworkConfig {
    actual val BASE_URL: String = "http://localhost:8080"
}
