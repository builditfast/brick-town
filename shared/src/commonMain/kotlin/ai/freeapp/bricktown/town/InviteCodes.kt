package ai.freeapp.bricktown.town

/**
 * The server's invite code shape (`server/.../town/InviteCodes.kt`), repeated
 * so a deep link or a typed code is checked before a round trip. Change both
 * together.
 */
object InviteCodes {
    /** No 0, 1, I or O: codes are read aloud and typed by hand. */
    const val ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"

    private val shape = Regex("^[$ALPHABET]{4}-[$ALPHABET]{4}$")

    /** What a person typed or a link carried, brought to `XXXX-XXXX`; null when it cannot be a code. */
    fun normalize(raw: String): String? {
        val letters = raw.trim().uppercase().filter { it.isLetterOrDigit() }
        if (letters.length != 8) return null
        val code = letters.substring(0, 4) + "-" + letters.substring(4)
        return code.takeIf { shape.matches(it) }
    }
}
