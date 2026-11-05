package ai.freeapp.bricktown

import ai.freeapp.bricktown.config.AppConfig
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AppConfigTest {

    private val strongSecret = "x".repeat(AppConfig.MIN_SECRET_LENGTH)

    @Test
    fun `production refuses a missing or short secret`() {
        for (secret in listOf("", "too-short")) {
            assertFailsWith<IllegalArgumentException>(secret) { AppConfig.jwtSecret(production = true, configured = secret) }
        }
        assertEquals(strongSecret, AppConfig.jwtSecret(production = true, configured = strongSecret))
    }

    @Test
    fun `a short secret is refused even when APP_ENV was forgotten`() {
        assertFailsWith<IllegalArgumentException> { AppConfig.jwtSecret(production = false, configured = "secret123") }
    }

    @Test
    fun `without a secret outside production each process signs with its own random one`() {
        val first = AppConfig.jwtSecret(production = false, configured = "")
        val second = AppConfig.jwtSecret(production = false, configured = "")
        assertTrue(first.length >= AppConfig.MIN_SECRET_LENGTH, first)
        assertNotEquals(first, second)
    }

    @Test
    fun `the server refuses to boot in production without a secret, before touching the database`() {
        val failure = assertFailsWith<IllegalArgumentException> {
            testApplication {
                environment {
                    config = testConfig(
                        "app.env" to "production",
                        "jwt.secret" to "",
                        "database.url" to "jdbc:postgresql://127.0.0.1:1/unreachable",
                    )
                }
                application { module() }
                startApplication()
            }
        }
        assertTrue(failure.message.orEmpty().contains("JWT_SECRET"), failure.message)
    }

    @Test
    fun `production refuses to boot while the legal pages would show template values`() {
        for (missing in listOf("app.name", "app.contactEmail")) {
            val failure = assertFailsWith<IllegalArgumentException>(missing) {
                testApplication {
                    environment {
                        config = testConfig(
                            "app.env" to "production",
                            "app.name" to "Streak",
                            "app.contactEmail" to "privacy@streak.app",
                            missing to "",
                            "database.url" to "jdbc:postgresql://127.0.0.1:1/unreachable",
                        )
                    }
                    application { module() }
                    startApplication()
                }
            }
            assertTrue(failure.message.orEmpty().contains("APP_NAME and CONTACT_EMAIL"), failure.message)
        }
    }

    @Test
    fun `an APP_ENV that is neither development nor production refuses to boot`() {
        val failure = assertFailsWith<IllegalArgumentException> {
            testApplication {
                environment {
                    config = testConfig(
                        "app.env" to "prod",
                        "database.url" to "jdbc:postgresql://127.0.0.1:1/unreachable",
                    )
                }
                application { module() }
                startApplication()
            }
        }
        assertTrue(failure.message.orEmpty().contains("APP_ENV"), failure.message)
    }
}
