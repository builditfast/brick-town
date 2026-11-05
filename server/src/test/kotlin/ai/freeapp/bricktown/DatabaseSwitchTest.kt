package ai.freeapp.bricktown

import ai.freeapp.bricktown.auth.RefreshTokenRepository
import ai.freeapp.bricktown.config.DatabaseFactory
import ai.freeapp.bricktown.config.dbQuery
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.time.OffsetDateTime
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Every test boots its own application, so one JVM connects a new database
 * and closes the old pool many times. Exposed remembers a database per
 * thread, and a thread that once ran a query keeps the old one unless the
 * call names its database. Under load, a later test's request then lands on
 * a thread like that and fails with "HikariDataSource (...) has been closed".
 */
class DatabaseSwitchTest {

    @Test
    fun `a query on a reused thread after a new connect uses the new database, not the closed one`() = runBlocking {
        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { reusedThread ->
            TestDatabase.reset()
            DatabaseFactory.init(testConfig())
            withContext(reusedThread) {
                assertEquals(1, selectOne())
                assertNull(RefreshTokenRepository().rotate("unknown", "next", OffsetDateTime.now()))
            }
            DatabaseFactory.close()

            DatabaseFactory.init(testConfig())
            try {
                withContext(reusedThread) {
                    assertEquals(1, selectOne())
                    assertNull(RefreshTokenRepository().rotate("unknown", "next", OffsetDateTime.now()))
                }
            } finally {
                DatabaseFactory.close()
            }
        }
    }

    private suspend fun selectOne(): Int? = dbQuery {
        TransactionManager.current().exec("select 1") { rows -> if (rows.next()) rows.getInt(1) else null }
    }
}
