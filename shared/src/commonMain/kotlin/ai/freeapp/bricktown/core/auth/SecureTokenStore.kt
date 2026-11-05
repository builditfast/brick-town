package ai.freeapp.bricktown.core.auth

/**
 * Builds the [TokenStore] the app keeps its session in, encrypted at rest: an
 * AES-GCM key held in the Android Keystore over SharedPreferences on Android,
 * the Keychain on iOS.
 *
 * An `expect class` like `DatabaseDriverFactory`, for the same reason: the
 * Android actual needs a `Context`, so each platform registers it in Koin
 * itself (`TemplateApp` on Android, `initKoinIos` on iOS).
 */
expect class SecureTokenStoreFactory {
    fun create(): TokenStore
}
