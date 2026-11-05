package ai.freeapp.bricktown.config

import ai.freeapp.bricktown.legal.LegalInfo
import io.ktor.server.config.ApplicationConfig
import org.slf4j.LoggerFactory
import java.security.SecureRandom
import java.util.Base64

/**
 * Everything the server reads from the environment besides the database.
 *
 * Built first in `module()`, before Postgres is touched, so a production box
 * with a missing or short JWT secret refuses to start instead of handing out
 * tokens someone could forge.
 */
data class AppConfig(
    val jwtSecret: String,
    val legal: LegalInfo,
    val flagsStatePath: String,
) {
    companion object {
        const val MIN_SECRET_LENGTH = 32

        private val log = LoggerFactory.getLogger(AppConfig::class.java)

        fun from(config: ApplicationConfig): AppConfig {
            val env = config.string("app.env").lowercase().ifEmpty { "development" }
            require(env == "development" || env == "production") {
                "APP_ENV is \"$env\"; use production on a real server, or development (the default)"
            }
            val production = env == "production"
            return AppConfig(
                jwtSecret = jwtSecret(production, config.string("jwt.secret")),
                legal = LegalInfo.from(
                    appName = config.string("app.name"),
                    contactEmail = config.string("app.contactEmail"),
                    companyName = config.string("app.companyName"),
                ),
                flagsStatePath = config.string("flags.statePath"),
            ).also {
                if (it.legal.usesTemplateValues) {
                    require(!production) {
                        "APP_ENV=production needs APP_NAME and CONTACT_EMAIL: the store's privacy link would name this template"
                    }
                    log.warn("APP_NAME or CONTACT_EMAIL is not set: /privacy and /terms show template values")
                }
            }
        }

        /**
         * A missing secret outside production gets a random one for this process,
         * never a constant: a JAR started without APP_ENV on a real server must not
         * sign with a key printed in this template. Access tokens then die with the
         * process, and the app's 401 refresh replaces them from the stored session.
         */
        fun jwtSecret(production: Boolean, configured: String): String {
            if (configured.isBlank()) {
                require(!production) {
                    "APP_ENV=production needs JWT_SECRET set to a random value of at least " +
                        "$MIN_SECRET_LENGTH characters (for example: openssl rand -base64 48)"
                }
                log.warn("JWT_SECRET is not set: signing with a random secret that lasts until this process stops")
                return ByteArray(48).also { SecureRandom().nextBytes(it) }.let(Base64.getEncoder()::encodeToString)
            }
            require(configured.length >= MIN_SECRET_LENGTH) {
                "JWT_SECRET is ${configured.length} characters; it needs at least $MIN_SECRET_LENGTH " +
                    "(for example: openssl rand -base64 48)"
            }
            return configured
        }

        private fun ApplicationConfig.string(key: String): String =
            propertyOrNull(key)?.getString()?.trim().orEmpty()
    }
}
