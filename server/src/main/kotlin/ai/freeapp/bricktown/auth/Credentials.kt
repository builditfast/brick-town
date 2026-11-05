package ai.freeapp.bricktown.auth

import at.favre.lib.crypto.bcrypt.BCrypt
import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.Date
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

class PasswordHasher(private val cost: Int = COST) {

    fun hash(password: String): String =
        BCrypt.withDefaults().hashToString(cost, password.toCharArray())

    /**
     * bcrypt throws past 72 bytes rather than answer, and no stored hash can
     * match a password [AccountRules] would not let anyone set.
     */
    fun verify(password: String, hash: String): Boolean =
        password.encodeToByteArray().size <= AccountRules.MAX_PASSWORD_BYTES &&
            BCrypt.verifyer().verify(password.toCharArray(), hash).verified

    companion object {
        const val COST = 12
    }
}

/**
 * Signs and checks short-lived access tokens. They are not stored, so they
 * cannot be revoked: a stolen one works until it expires, which is why the
 * lifetime is minutes and the revocable part is the refresh token.
 */
class AccessTokens(secret: String, private val ttl: Duration = TTL) {
    private val algorithm = Algorithm.HMAC256(secret)

    val verifier: JWTVerifier = JWT.require(algorithm)
        .withIssuer(ISSUER)
        .withAudience(AUDIENCE)
        .build()

    fun issue(userId: UUID, sessionId: UUID): String {
        val now = System.currentTimeMillis()
        return JWT.create()
            .withIssuer(ISSUER)
            .withAudience(AUDIENCE)
            .withSubject(userId.toString())
            .withClaim(CLAIM_SESSION, sessionId.toString())
            .withIssuedAt(Date(now))
            .withExpiresAt(Date(now + ttl.inWholeMilliseconds))
            .sign(algorithm)
    }

    companion object {
        val TTL = 15.minutes
        const val REALM = "ai.freeapp.bricktown"
        const val CLAIM_SESSION = "sid"
        private const val ISSUER = "ai.freeapp.bricktown.server"
        private const val AUDIENCE = "ai.freeapp.bricktown.app"
    }
}

/**
 * Refresh tokens are random strings, not JWTs: the server has to look them up
 * anyway to honour revocation. Only [hash] of a token is ever stored, so a
 * database leak does not hand out working sessions.
 */
object RefreshTokens {
    val TTL = 30.days

    private val random = SecureRandom()

    fun generate(): String =
        ByteArray(32).also(random::nextBytes).let(Base64.getUrlEncoder().withoutPadding()::encodeToString)

    fun hash(token: String): String =
        MessageDigest.getInstance("SHA-256").digest(token.toByteArray()).joinToString("") { "%02x".format(it) }
}
