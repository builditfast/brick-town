package ai.freeapp.bricktown.auth

import ai.freeapp.bricktown.config.dbQuery
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

class UserRepository {

    /**
     * Null when the email is already taken. ON CONFLICT DO NOTHING rather than
     * catching the unique violation: Exposed retries a transaction that threw,
     * so a taken email would cost three inserts and three logged stack traces.
     */
    suspend fun create(email: String, name: String, passwordHash: String): StoredUser? {
        val user = StoredUser(UUID.randomUUID(), email, name, passwordHash)
        val inserted = dbQuery {
            UsersTable.insertIgnore {
                it[id] = user.id
                it[UsersTable.email] = user.email
                it[UsersTable.name] = user.name
                it[UsersTable.passwordHash] = user.passwordHash
                it[createdAt] = OffsetDateTime.now(ZoneOffset.UTC)
            }.insertedCount
        }
        return user.takeIf { inserted == 1 }
    }

    suspend fun findByEmail(email: String): StoredUser? = dbQuery {
        UsersTable.selectAll().where { UsersTable.email eq email }.singleOrNull()?.toUser()
    }

    suspend fun findById(id: UUID): StoredUser? = dbQuery {
        UsersTable.selectAll().where { UsersTable.id eq id }.singleOrNull()?.toUser()
    }

    suspend fun exists(id: UUID): Boolean = dbQuery {
        UsersTable.select(UsersTable.id).where { UsersTable.id eq id }.empty().not()
    }

    suspend fun updatePassword(id: UUID, passwordHash: String) {
        dbQuery {
            UsersTable.update({ UsersTable.id eq id }) { it[UsersTable.passwordHash] = passwordHash }
        }
    }

    /**
     * Hard delete. Refresh tokens, towns and every other table that references
     * users(id) go with it through ON DELETE CASCADE, so a new table that holds
     * a user's data must declare that too.
     */
    suspend fun delete(id: UUID): Boolean = dbQuery {
        UsersTable.deleteWhere { UsersTable.id eq id } > 0
    }

    private fun ResultRow.toUser() = StoredUser(
        id = this[UsersTable.id],
        email = this[UsersTable.email],
        name = this[UsersTable.name],
        passwordHash = this[UsersTable.passwordHash],
    )
}
