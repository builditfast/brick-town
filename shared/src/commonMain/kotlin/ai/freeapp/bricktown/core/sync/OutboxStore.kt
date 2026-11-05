package ai.freeapp.bricktown.core.sync

import ai.freeapp.bricktown.db.AppDatabase
import ai.freeapp.bricktown.db.OutboxRow
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOne
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow

/** Where queued writes wait. [SqlOutboxStore] in the app, an in-memory fake in tests. */
interface OutboxStore {
    fun observeCount(): Flow<Long>
    fun count(): Long

    /** Appends a write and returns its id. */
    fun add(kind: String, payload: String): Long
    fun oldest(): OutboxRow?

    /** False when the row was already gone: the session ended and its wipe emptied the table. */
    fun remove(id: Long): Boolean
    fun clear()
}

class SqlOutboxStore(private val db: AppDatabase) : OutboxStore {
    private val queries = db.outboxQueries

    override fun observeCount(): Flow<Long> = queries.countAll().asFlow().mapToOne(Dispatchers.Default)

    override fun count(): Long = queries.countAll().executeAsOne()

    // One transaction, so last_insert_rowid() reads this insert's row on the same connection.
    override fun add(kind: String, payload: String): Long = db.transactionWithResult {
        queries.insert(kind, payload)
        queries.lastInsertedId().executeAsOne()
    }

    override fun oldest(): OutboxRow? = queries.selectOldest().executeAsOneOrNull()

    override fun remove(id: Long): Boolean = db.transactionWithResult {
        val present = queries.countById(id).executeAsOne() > 0
        if (present) queries.deleteById(id)
        present
    }

    override fun clear() {
        queries.deleteAll()
    }
}
