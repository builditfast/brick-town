package ai.freeapp.bricktown.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.config.ApplicationConfig
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.URLDecoder
import javax.sql.DataSource

object DatabaseFactory {
    private val log = LoggerFactory.getLogger(DatabaseFactory::class.java)

    lateinit var dataSource: DataSource
        private set

    /**
     * Every transaction names this database. Left out, Exposed takes the one
     * the current thread last used, which after a reconnect (each server test
     * boots a new application) can be a closed pool.
     */
    lateinit var database: Database
        private set

    fun init(config: ApplicationConfig) {
        val rawUrl = config.property("database.url").getString()
        val defaultUser = config.property("database.user").getString()
        val defaultPassword = config.property("database.password").getString()
        val maxPoolSize = config.property("database.maxPoolSize").getString().toInt()

        val (jdbcUrl, user, password) = resolveDbUrl(rawUrl, defaultUser, defaultPassword)
        dataSource = hikari(jdbcUrl, user, password, maxPoolSize)
        migrate(dataSource)
        database = Database.connect(dataSource)
    }

    /** Releases the pool; called when the application stops. */
    fun close() {
        TransactionManager.closeAndUnregister(database)
        (dataSource as? HikariDataSource)?.close()
    }

    /**
     * Accepts both URL flavors so the same template works on any host:
     *   - jdbc:postgresql://host:port/db        → use env DATABASE_USER / DATABASE_PASSWORD
     *   - postgres://user:pass@host:port/db     → parse credentials out of the URI
     *   - postgresql://user:pass@host:port/db   → same
     *
     * Managed platforms (Fly.io, Render, Railway, Heroku) inject the postgres:// form
     * via DATABASE_URL automatically; self-hosted Postgres usually wants the jdbc: form.
     */
    private fun resolveDbUrl(
        url: String,
        defaultUser: String,
        defaultPassword: String,
    ): Triple<String, String, String> {
        if (url.startsWith("jdbc:")) {
            return Triple(url, defaultUser, defaultPassword)
        }
        if (url.startsWith("postgres://") || url.startsWith("postgresql://")) {
            val uri = URI(url)
            val userInfo = uri.userInfo?.split(":", limit = 2).orEmpty()
            val user = userInfo.getOrNull(0)?.let { URLDecoder.decode(it, "UTF-8") } ?: defaultUser
            val password = userInfo.getOrNull(1)?.let { URLDecoder.decode(it, "UTF-8") } ?: defaultPassword
            val host = uri.host ?: error("DATABASE_URL is missing a host")
            val port = if (uri.port == -1) 5432 else uri.port
            val db = uri.path.removePrefix("/")
            val query = uri.query?.let { "?$it" }.orEmpty()
            return Triple("jdbc:postgresql://$host:$port/$db$query", user, password)
        }
        error("Unsupported DATABASE_URL '$url' — must start with jdbc:, postgres://, or postgresql://")
    }

    private fun hikari(url: String, user: String, password: String, maxPoolSize: Int): HikariDataSource {
        val cfg = HikariConfig().apply {
            driverClassName = "org.postgresql.Driver"
            jdbcUrl = url
            username = user
            this.password = password
            maximumPoolSize = maxPoolSize
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            validate()
        }
        return HikariDataSource(cfg)
    }

    private fun migrate(ds: DataSource) {
        // Inside the fat JAR the scan depends on shadowJar merging
        // META-INF/services (server/build.gradle.kts): without the merge Flyway
        // resolves zero migrations and the server runs on an empty schema.
        // This class's loader rather than the thread context one keeps the scan
        // working under any launcher, and validateMigrationNaming turns a
        // migration that is present but unresolvable into a boot failure
        // instead of an INFO line (streak-app task 265).
        val result = Flyway.configure(DatabaseFactory::class.java.classLoader)
            .dataSource(ds)
            .locations("classpath:db/migration")
            .validateMigrationNaming(true)
            .load()
            .migrate()
        log.info(
            "flyway: schema at version {} ({} migration(s) applied this boot)",
            result.targetSchemaVersion ?: "none",
            result.migrationsExecuted,
        )
    }
}
