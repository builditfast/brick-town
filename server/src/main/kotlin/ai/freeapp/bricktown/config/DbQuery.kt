package ai.freeapp.bricktown.config

import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

/** Runs [block] in one transaction off the request thread, on [DatabaseFactory.database]. */
suspend fun <T> dbQuery(block: suspend () -> T): T =
    newSuspendedTransaction(Dispatchers.IO, DatabaseFactory.database) { block() }
