package ai.freeapp.bricktown.town

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.date
import org.jetbrains.exposed.sql.kotlin.datetime.timestampWithTimeZone

/** Mirrors V2__town.sql. Every table cascades from users or towns there. */
object TownsTable : Table("towns") {
    val id = uuid("id")
    val ownerId = uuid("owner_id")
    val name = text("name")
    val budgetCents = long("budget_cents")
    val payday = integer("payday")
    val currency = text("currency")
    val billsFirst = bool("bills_first")
    val timeZone = text("time_zone")
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(id)
}

object TownMembersTable : Table("town_members") {
    val townId = uuid("town_id")
    val userId = uuid("user_id")
    val initial = text("initial")
    val colorIndex = integer("color_index")
    val role = text("role")
    val canEdit = bool("can_edit")
    val canManage = bool("can_manage")
    val joinedAt = timestampWithTimeZone("joined_at")

    override val primaryKey = PrimaryKey(townId, userId)
}

object InvitesTable : Table("invites") {
    val code = text("code")
    val townId = uuid("town_id")
    val canEdit = bool("can_edit")
    val canManage = bool("can_manage")
    val createdAt = timestampWithTimeZone("created_at")
    val expiresAt = timestampWithTimeZone("expires_at")
    val revokedAt = timestampWithTimeZone("revoked_at").nullable()

    override val primaryKey = PrimaryKey(code)
}

object CategoriesTable : Table("categories") {
    val id = uuid("id")
    val townId = uuid("town_id")
    val name = text("name")
    val short = text("short")
    val colorIndex = integer("color_index")
    val capCents = long("cap_cents").nullable()
    val position = integer("position")

    override val primaryKey = PrimaryKey(id)
}

object ExpensesTable : Table("expenses") {
    val id = uuid("id")
    val townId = uuid("town_id")
    val categoryId = uuid("category_id")
    val amountCents = long("amount_cents")
    val date = date("date")
    val note = text("note")
    val recurring = bool("recurring")
    val shared = bool("shared")
    val refund = bool("refund")
    val paidBy = uuid("paid_by")
    val settledAt = timestampWithTimeZone("settled_at").nullable()
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(id)
}

object GoldMarksTable : Table("gold_marks") {
    val townId = uuid("town_id")
    val date = date("date")
    val markedBy = uuid("marked_by")

    override val primaryKey = PrimaryKey(townId, date)
}

object BillSkipsTable : Table("bill_skips") {
    val townId = uuid("town_id")
    val monthStart = date("month_start")
    val seriesKey = text("series_key")

    override val primaryKey = PrimaryKey(townId, monthStart, seriesKey)
}
