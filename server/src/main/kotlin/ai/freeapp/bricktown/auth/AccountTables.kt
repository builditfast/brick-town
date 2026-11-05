package ai.freeapp.bricktown.auth

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestampWithTimeZone

object UsersTable : Table("users") {
    val id = uuid("id")
    val email = text("email")
    val name = text("name")
    val passwordHash = text("password_hash")
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(id)
}

/** One row per signed-in session. The id is the access token's "sid" claim. */
object RefreshTokensTable : Table("refresh_tokens") {
    val id = uuid("id")
    val userId = uuid("user_id")
    val tokenHash = text("token_hash")
    val expiresAt = timestampWithTimeZone("expires_at")
    val revokedAt = timestampWithTimeZone("revoked_at").nullable()

    override val primaryKey = PrimaryKey(id)
}
