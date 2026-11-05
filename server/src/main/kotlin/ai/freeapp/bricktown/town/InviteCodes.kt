package ai.freeapp.bricktown.town

import java.security.SecureRandom
import kotlin.time.Duration.Companion.days

/** Codes people read aloud or type by hand: no 0, 1, I or O. */
object InviteCodes {
    const val ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
    val EXPIRY = 7.days

    private val random = SecureRandom()
    private val shape = Regex("^[$ALPHABET]{4}-[$ALPHABET]{4}$")

    /** `XXXX-XXXX`. */
    fun generate(): String {
        val letters = CharArray(8) { ALPHABET[random.nextInt(ALPHABET.length)] }
        return String(letters, 0, 4) + "-" + String(letters, 4, 4)
    }

    /** What a person typed, brought to the stored shape; null when it cannot be a code. */
    fun normalize(raw: String): String? {
        val letters = raw.trim().uppercase().filter { it.isLetterOrDigit() }
        if (letters.length != 8) return null
        val code = letters.substring(0, 4) + "-" + letters.substring(4)
        return code.takeIf { shape.matches(it) }
    }
}
