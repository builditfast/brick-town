package ai.freeapp.bricktown.auth

/**
 * The server's input rules (`server/.../auth/AccountModels.kt`, `AccountRules`),
 * repeated here so a form can say what is wrong before a round trip. The
 * server still checks everything; change both together.
 */
object AccountRules {
    const val MIN_PASSWORD_LENGTH = 8

    /** bcrypt ignores everything past 72 bytes; the server refuses rather than truncate. */
    const val MAX_PASSWORD_BYTES = 72
    const val MAX_NAME_LENGTH = 80

    fun normalizeEmail(email: String): String = email.trim().lowercase()

    fun isValidEmail(email: String): Boolean {
        val at = email.indexOf('@')
        return at > 0 && email.indexOf('.', at) > at + 1 && !email.endsWith('.') && ' ' !in email
    }

    /** The message to show, or null when the password is acceptable. */
    fun passwordProblem(password: String): String? = when {
        password.length < MIN_PASSWORD_LENGTH -> "Use at least $MIN_PASSWORD_LENGTH characters."
        password.encodeToByteArray().size > MAX_PASSWORD_BYTES -> "Use at most $MAX_PASSWORD_BYTES characters."
        else -> null
    }

    /** The message to show, or null when the name is acceptable. */
    fun nameProblem(name: String): String? = when {
        name.isBlank() -> "Enter your name."
        name.trim().length > MAX_NAME_LENGTH -> "Use at most $MAX_NAME_LENGTH characters."
        else -> null
    }
}
