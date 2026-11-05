package ai.freeapp.bricktown

import ai.freeapp.bricktown.auth.AuthResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.testApplication
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import kotlinx.serialization.json.Json
import java.util.UUID
import kotlin.test.assertEquals

/**
 * One real Postgres per test JVM. Each test drops the schema, so the app under
 * test runs the Flyway migrations from scratch exactly as a fresh deploy does.
 */
object TestDatabase {
    // initdb otherwise takes its locale from LANG, and a macOS region override
    // such as en_US@rg=rozzzz.UTF-8 is not one it accepts.
    private val postgres: EmbeddedPostgres by lazy {
        EmbeddedPostgres.builder().setLocaleConfig("locale", "C").start()
    }

    val jdbcUrl: String get() = postgres.getJdbcUrl("postgres", "postgres")

    fun reset() {
        postgres.postgresDatabase.connection.use {
            it.createStatement().execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public;")
        }
    }

    /** An `update ...` with one UUID parameter, for state a test cannot wait for. */
    fun update(sql: String, id: UUID) {
        postgres.postgresDatabase.connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setObject(1, id)
                statement.executeUpdate()
            }
        }
    }

    /** A statement with no parameters, for bulk seeding a test cannot do over HTTP. */
    fun execute(sql: String) {
        postgres.postgresDatabase.connection.use { it.createStatement().execute(sql) }
    }

    /** `select count(*) ...` with one UUID parameter. */
    fun count(sql: String, id: UUID): Int = postgres.postgresDatabase.connection.use { connection ->
        connection.prepareStatement(sql).use { statement ->
            statement.setObject(1, id)
            statement.executeQuery().use { rows ->
                rows.next()
                rows.getInt(1)
            }
        }
    }
}

/** A state file of the test's own, empty until a test writes switches into it. */
object TestFlags {
    val path: java.nio.file.Path =
        java.nio.file.Files.createTempDirectory("flags").resolve("kmp-ktor-template.json")

    fun write(json: String) {
        java.nio.file.Files.writeString(path, json)
    }

    fun clear() {
        java.nio.file.Files.deleteIfExists(path)
    }
}

fun testConfig(vararg overrides: Pair<String, String>) = MapApplicationConfig(
    "database.url" to TestDatabase.jdbcUrl,
    "database.user" to "postgres",
    "database.password" to "postgres",
    // Every testApplication builds its own pool; keep each one small.
    "database.maxPoolSize" to "2",
    "app.env" to "development",
    "app.name" to "",
    "app.contactEmail" to "",
    "app.companyName" to "",
    "jwt.secret" to "test-secret-that-is-long-enough-for-hmac",
    // Never the owner's real switches under /root/.freeapp.
    "flags.statePath" to TestFlags.path.toString(),
    *overrides,
)

/** Boots the real `module()` against a freshly emptied database. */
fun serverTest(vararg config: Pair<String, String>, block: suspend ServerScope.() -> Unit) = testApplication {
    TestDatabase.reset()
    environment { this.config = testConfig(*config) }
    application { module() }
    val client = createClient {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }
    ServerScope(client).block()
}

class ServerScope(val client: HttpClient) {

    suspend fun register(
        email: String = "ada@example.org",
        password: String = "correct-horse",
        name: String = "Ada",
    ): AuthResponse {
        val response = client.post("/auth/register") {
            json("""{"email":"$email","password":"$password","name":"$name"}""")
        }
        assertEquals(HttpStatusCode.Created, response.status)
        return response.body()
    }

    suspend fun login(email: String, password: String) = client.post("/auth/login") {
        json("""{"email":"$email","password":"$password"}""")
    }

    suspend fun refresh(refreshToken: String) = client.post("/auth/refresh") {
        json("""{"refreshToken":"$refreshToken"}""")
    }
}

fun HttpRequestBuilder.json(body: String) {
    contentType(ContentType.Application.Json)
    setBody(body)
}
