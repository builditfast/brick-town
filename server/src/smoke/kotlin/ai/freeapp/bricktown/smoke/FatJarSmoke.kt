package ai.freeapp.bricktown.smoke

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.SecureRandom
import java.time.Duration
import java.util.Base64
import java.util.UUID
import kotlin.system.exitProcess

/*
 * Run by the shadowJarSmoke Gradle task after every :server:shadowJar. It
 * starts the real server-all.jar as its own process, the way a host runs it,
 * against an empty throwaway Postgres, and walks the account API once. A JAR
 * that cannot migrate, boot, register or delete fails the build here instead
 * of on a phone.
 */

private const val START_TIMEOUT_SECONDS = 60L

fun main(args: Array<String>) {
    val jar = File(args.single())
    val started = System.nanoTime()
    try {
        FatJarSmoke(jar).run()
    } catch (failure: SmokeFailure) {
        System.err.println("smoke: FAILED: ${failure.message}")
        exitProcess(1)
    }
    println("smoke: PASS, ${jar.name} migrated, registered, read and deleted an account in ${secondsSince(started)}")
    exitProcess(0)
}

private class SmokeFailure(message: String) : Exception(message)

private class FatJarSmoke(private val jar: File) {
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()
    private val postgresLog = File.createTempFile("smoke-postgres", ".log").apply { deleteOnExit() }
    private val serverLog = File.createTempFile("smoke-server", ".log").apply { deleteOnExit() }

    fun run() {
        if (!jar.isFile) throw SmokeFailure("no JAR at $jar")
        startPostgres().use { postgres ->
            println("smoke: throwaway Postgres on port ${postgres.port}")
            val port = freePort()
            val server = startServer(postgres, port)
            try {
                walkAccountApi(postgres, server, "http://127.0.0.1:$port")
            } catch (failure: SmokeFailure) {
                throw SmokeFailure(
                    "${failure.message}\n${schemaReport(postgres)}\n" +
                        "--- last lines of the server's output ---\n${serverLog.tail(40)}",
                )
            } finally {
                stop(server)
            }
        }
    }

    private fun startPostgres(): EmbeddedPostgres = try {
        EmbeddedPostgres.builder()
            // initdb otherwise takes its locale from LANG, and a macOS region
            // override such as en_US@rg=rozzzz.UTF-8 is not one it accepts.
            .setLocaleConfig("locale", "C")
            .setOutputRedirector(ProcessBuilder.Redirect.appendTo(postgresLog))
            .setErrorRedirector(ProcessBuilder.Redirect.appendTo(postgresLog))
            .start()
    } catch (e: Exception) {
        throw SmokeFailure(
            "could not start the throwaway Postgres: $e\n" +
                "--- last lines of its output ---\n${postgresLog.tail(20)}",
        )
    }

    private fun startServer(postgres: EmbeddedPostgres, port: Int): Process {
        val java = File(System.getProperty("java.home"), "bin/java").path
        val builder = ProcessBuilder(java, "-jar", jar.absolutePath)
            .redirectErrorStream(true)
            .redirectOutput(serverLog)
        // Every variable the server reads is set, so nothing leaks in from the
        // shell or the Gradle daemon. Production mode with a real secret is the
        // configuration a host runs.
        builder.environment().apply {
            put("PORT", port.toString())
            put("DATABASE_URL", "jdbc:postgresql://127.0.0.1:${postgres.port}/postgres")
            put("DATABASE_USER", "postgres")
            put("DATABASE_PASSWORD", "postgres")
            put("DATABASE_MAX_POOL_SIZE", "2")
            put("APP_ENV", "production")
            put("JWT_SECRET", randomSecret())
            put("APP_NAME", "Smoke")
            put("CONTACT_EMAIL", "smoke@localhost")
            put("COMPANY_NAME", "Smoke")
        }
        val process = builder.start()
        println("smoke: started ${jar.name} on port $port")
        return process
    }

    private fun walkAccountApi(postgres: EmbeddedPostgres, server: Process, base: String) {
        awaitHealthy(server, base)

        val email = "smoke-${UUID.randomUUID()}@example.com"
        val registered = send(
            "POST /auth/register",
            HttpRequest.newBuilder(URI("$base/auth/register"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""{"email":"$email","password":"smoke-password-1","name":"Smoke"}""")),
            expected = 201,
        )
        val body = Json.parseToJsonElement(registered.body()).jsonObject
        val accessToken = body["accessToken"]?.jsonPrimitive?.content
            ?: throw SmokeFailure("POST /auth/register answered 201 without an accessToken: ${registered.body()}")
        val userId = body["user"]?.jsonObject?.get("id")?.jsonPrimitive?.content?.let(UUID::fromString)
            ?: throw SmokeFailure("POST /auth/register answered 201 without user.id: ${registered.body()}")

        val me = send("GET /me", authorized(base, "/me", accessToken).GET(), expected = 200)
        if (email !in me.body()) throw SmokeFailure("GET /me did not return the registered email: ${me.body()}")

        send("DELETE /me", authorized(base, "/me", accessToken).DELETE(), expected = 204)
        send("GET /me after DELETE /me", authorized(base, "/me", accessToken).GET(), expected = 401)

        val left = postgres.postgresDatabase.connection.use { connection ->
            listOf("users" to "id", "refresh_tokens" to "user_id", "towns" to "owner_id").sumOf { (table, column) ->
                connection.prepareStatement("select count(*) from $table where $column = ?").use { statement ->
                    statement.setObject(1, userId)
                    statement.executeQuery().use { rows -> rows.next(); rows.getInt(1) }
                }
            }
        }
        if (left != 0) throw SmokeFailure("DELETE /me answered 204 but $left row(s) of user $userId are still in the database")
        println("smoke: register 201, GET /me 200, DELETE /me 204, GET /me 401, no rows left")
    }

    private fun awaitHealthy(server: Process, base: String) {
        val started = System.nanoTime()
        val deadline = started + Duration.ofSeconds(START_TIMEOUT_SECONDS).toNanos()
        while (System.nanoTime() < deadline) {
            if (!server.isAlive) {
                throw SmokeFailure("the server exited with code ${server.exitValue()} before /health answered")
            }
            val status = runCatching {
                http.send(
                    HttpRequest.newBuilder(URI("$base/health")).timeout(Duration.ofSeconds(2)).GET().build(),
                    HttpResponse.BodyHandlers.discarding(),
                ).statusCode()
            }.getOrNull()
            if (status == 200) {
                println("smoke: /health answered 200 after ${secondsSince(started)}")
                return
            }
            Thread.sleep(250)
        }
        throw SmokeFailure("/health did not answer 200 within $START_TIMEOUT_SECONDS s")
    }

    private fun send(step: String, request: HttpRequest.Builder, expected: Int): HttpResponse<String> {
        val response = try {
            http.send(request.timeout(Duration.ofSeconds(15)).build(), HttpResponse.BodyHandlers.ofString())
        } catch (e: Exception) {
            throw SmokeFailure("$step failed: $e")
        }
        if (response.statusCode() != expected) {
            throw SmokeFailure("$step answered ${response.statusCode()}, expected $expected. Body: ${response.body()}")
        }
        return response
    }

    private fun authorized(base: String, path: String, token: String): HttpRequest.Builder =
        HttpRequest.newBuilder(URI("$base$path")).header("Authorization", "Bearer $token")

    /** The tables Flyway created, which separates "no migrations ran" from a code bug. */
    private fun schemaReport(postgres: EmbeddedPostgres): String = runCatching {
        postgres.postgresDatabase.connection.use { connection ->
            connection.createStatement().executeQuery(
                "select table_name from information_schema.tables where table_schema = 'public' order by 1",
            ).use { rows ->
                val tables = generateSequence { if (rows.next()) rows.getString(1) else null }
                    .filter { it != "flyway_schema_history" }
                    .toList()
                when {
                    tables.isEmpty() ->
                        "The database has no application tables: Flyway applied no migrations inside the JAR. " +
                            "Check mergeServiceFiles() in server/build.gradle.kts and the files in db/migration."
                    "users" !in tables -> "The database has tables $tables but no users table."
                    else -> "Tables present: $tables."
                }
            }
        }
    }.getOrElse { "Could not read the schema: $it" }

    private fun stop(server: Process) {
        server.destroy()
        if (!server.waitFor(15, java.util.concurrent.TimeUnit.SECONDS)) {
            server.destroyForcibly().waitFor()
        }
    }
}

private fun freePort(): Int = ServerSocket(0).use { it.localPort }

private fun randomSecret(): String =
    ByteArray(48).also(SecureRandom()::nextBytes).let(Base64.getEncoder()::encodeToString)

private fun File.tail(lines: Int): String =
    if (exists()) readLines().takeLast(lines).joinToString("\n") else "(no output)"

private fun secondsSince(startNanos: Long): String =
    "%.1f s".format((System.nanoTime() - startNanos) / 1_000_000_000.0)
