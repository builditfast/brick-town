package ai.freeapp.bricktown.auth

import ai.freeapp.bricktown.config.DatabaseFactory
import ai.freeapp.bricktown.config.dbQuery
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNotNull
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import java.sql.Connection
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/** A refresh token row: [id] is the session id, [userId] its owner. */
data class Session(val id: UUID, val userId: UUID)

class RefreshTokenRepository {

    /** Stores a new session and returns its id. */
    suspend fun create(userId: UUID, tokenHash: String, expiresAt: OffsetDateTime): UUID = dbQuery {
        insertSession(userId, tokenHash, expiresAt)
    }

    /**
     * Spends the live token [oldHash] and stores [newHash] in its place, in one
     * transaction. Null when the old token is unknown, expired, revoked, or was
     * spent by a concurrent refresh a moment earlier.
     *
     * Read committed, not the pool's repeatable read: the second of two
     * concurrent refreshes must see the first one's revoke and update zero
     * rows, where repeatable read would fail it with a serialization error.
     */
    suspend fun rotate(oldHash: String, newHash: String, expiresAt: OffsetDateTime): Session? =
        newSuspendedTransaction(
            Dispatchers.IO,
            DatabaseFactory.database,
            transactionIsolation = Connection.TRANSACTION_READ_COMMITTED,
        ) {
            val now = now()
            val old = RefreshTokensTable.selectAll()
                .where { liveToken(oldHash, now) }
                .singleOrNull()
                ?: return@newSuspendedTransaction null
            val claimed = RefreshTokensTable.update({
                (RefreshTokensTable.id eq old[RefreshTokensTable.id]) and liveToken(oldHash, now)
            }) { it[revokedAt] = now }
            if (claimed == 0) return@newSuspendedTransaction null

            val userId = old[RefreshTokensTable.userId]
            Session(insertSession(userId, newHash, expiresAt), userId)
        }

    suspend fun revoke(tokenHash: String) {
        dbQuery {
            RefreshTokensTable.update({
                (RefreshTokensTable.tokenHash eq tokenHash) and RefreshTokensTable.revokedAt.isNull()
            }) { it[revokedAt] = now() }
        }
    }

    /** Ends every session of [userId] except [keep], the caller's own. */
    suspend fun revokeAllExcept(userId: UUID, keep: UUID?) {
        dbQuery {
            RefreshTokensTable.update({
                var condition = (RefreshTokensTable.userId eq userId) and RefreshTokensTable.revokedAt.isNull()
                if (keep != null) condition = condition and (RefreshTokensTable.id neq keep)
                condition
            }) { it[revokedAt] = now() }
        }
    }

    private fun insertSession(userId: UUID, tokenHash: String, expiresAt: OffsetDateTime): UUID {
        val now = now()
        // Spent rows are kept only until the user's next sign-in or refresh, so
        // the table stays one live row per device. Expired rows go whoever they
        // belong to: a user who signed out and never came back would otherwise
        // keep a row past the 30 days the privacy page promises.
        RefreshTokensTable.deleteWhere {
            (RefreshTokensTable.expiresAt lessEq now) or
                ((RefreshTokensTable.userId eq userId) and RefreshTokensTable.revokedAt.isNotNull())
        }
        val id = UUID.randomUUID()
        RefreshTokensTable.insert {
            it[RefreshTokensTable.id] = id
            it[RefreshTokensTable.userId] = userId
            it[RefreshTokensTable.tokenHash] = tokenHash
            it[RefreshTokensTable.expiresAt] = expiresAt
        }
        return id
    }

    private fun liveToken(hash: String, now: OffsetDateTime) =
        (RefreshTokensTable.tokenHash eq hash) and
            RefreshTokensTable.revokedAt.isNull() and
            (RefreshTokensTable.expiresAt greater now)

    private fun now(): OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC)
}
