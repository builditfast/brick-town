package ai.freeapp.bricktown.town

import com.russhwolf.settings.Settings

/**
 * The invite code someone opened a join link with while signed out, kept on
 * the phone until they sign in or create an account, so the join still
 * happens after (spec 5.5).
 */
interface JoinCodeStore {
    fun read(): String?

    /** Null forgets the code. */
    fun write(code: String?)
}

class SettingsJoinCodeStore(private val settings: Settings = Settings()) : JoinCodeStore {
    override fun read(): String? = settings.getStringOrNull(KEY)

    override fun write(code: String?) {
        if (code == null) settings.remove(KEY) else settings.putString(KEY, code)
    }

    private companion object {
        const val KEY = "join.code"
    }
}
