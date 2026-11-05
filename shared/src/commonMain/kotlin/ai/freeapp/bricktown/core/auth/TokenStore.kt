package ai.freeapp.bricktown.core.auth

import com.russhwolf.settings.Settings
import kotlinx.serialization.Serializable

/**
 * The pair the server hands back at login and at refresh.
 *
 * Field names must match the auth endpoint's JSON byte for byte, exactly like
 * every other DTO in this template.
 */
@Serializable
data class AuthTokens(
    val accessToken: String,
    val refreshToken: String,
)

/** Where the tokens live between app launches. */
interface TokenStore {
    fun read(): AuthTokens?
    fun save(tokens: AuthTokens)
    fun clear()
}

/**
 * Stores the pair in whatever [Settings] it is given and adds no encryption of
 * its own. The iOS actual of [SecureTokenStoreFactory] hands it the Keychain,
 * which the system encrypts. The no-arg default is plain SharedPreferences /
 * NSUserDefaults, for tests only: the app's session always goes through
 * [SecureTokenStoreFactory].
 */
class SettingsTokenStore(
    private val settings: Settings = Settings(),
) : TokenStore {

    override fun read(): AuthTokens? {
        val access = settings.getStringOrNull(KEY_ACCESS) ?: return null
        val refresh = settings.getStringOrNull(KEY_REFRESH) ?: return null
        return AuthTokens(access, refresh)
    }

    override fun save(tokens: AuthTokens) {
        settings.putString(KEY_ACCESS, tokens.accessToken)
        settings.putString(KEY_REFRESH, tokens.refreshToken)
    }

    override fun clear() {
        settings.remove(KEY_ACCESS)
        settings.remove(KEY_REFRESH)
    }

    private companion object {
        const val KEY_ACCESS = "auth.access_token"
        const val KEY_REFRESH = "auth.refresh_token"
    }
}
