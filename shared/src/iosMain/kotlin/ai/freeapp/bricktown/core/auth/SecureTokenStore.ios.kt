package ai.freeapp.bricktown.core.auth

import com.russhwolf.settings.ExperimentalSettingsImplementation
import com.russhwolf.settings.KeychainSettings
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.CFBridgingRetain
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrService

/**
 * The Keychain is encrypted at rest by the system, so the plain store over it is enough.
 *
 * ThisDeviceOnly keeps the refresh token out of iCloud and Finder backups, as
 * the backup rules do on Android. AfterFirstUnlock, not the default
 * WhenUnlocked: a refresh that answers after the user locks the phone must
 * still save the rotated pair, or the server's revoke of the old one signs
 * the user out.
 */
@OptIn(ExperimentalSettingsImplementation::class, ExperimentalForeignApi::class)
actual class SecureTokenStoreFactory {
    actual fun create(): TokenStore = SettingsTokenStore(
        KeychainSettings(
            kSecAttrService to CFBridgingRetain("ai.freeapp.bricktown.tokens"),
            kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
        ),
    )
}
