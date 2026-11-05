package ai.freeapp.bricktown.domain

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/**
 * The entities of one town. They are also the wire format: the server sends
 * them in [TownSnapshot] and takes them as PUT bodies, so the app and the
 * server cannot drift apart. Money is integer cents everywhere.
 *
 * Ids are UUID strings made by the device that creates the row, so a write
 * replayed by the outbox lands on the same row instead of making a second one.
 */

@Serializable
enum class MemberRole { OWNER, MEMBER }

@Serializable
enum class Currency(val symbol: String, val symbolAfter: Boolean) {
    EUR("€", false),
    USD("$", false),
    GBP("£", false),
    RON("lei", true),
}

@Serializable
data class TownSettings(
    val monthlyBudgetCents: Long,
    /** 1 to 31. The pay month starts on this day (clamped to short months); 1 is the calendar month. */
    val payday: Int = 1,
    val currency: Currency = Currency.EUR,
    /** Recurring bills come off the budget before the daily allowance is worked out. */
    val billsFirst: Boolean = true,
    /** IANA zone of the device that created the town. "Today" on the server means today here. */
    val timeZone: String,
)

@Serializable
data class Town(
    val id: String,
    val ownerId: String,
    /** "Emanuel's town", from the owner's first name. */
    val name: String,
    val settings: TownSettings,
    val createdAt: Instant,
)

@Serializable
data class Member(
    val userId: String,
    val name: String,
    /** One uppercase letter, picked on joining. */
    val initial: String,
    /** Index into the member colours; unique within a town. */
    val colorIndex: Int,
    val role: MemberRole,
    /** Edit and delete bricks other members added. The owner always can. */
    val canEdit: Boolean,
    /** Change the budget, the towers and their caps. The owner always can. */
    val canManage: Boolean,
    val joinedAt: Instant,
)

/** A tower. */
@Serializable
data class Category(
    val id: String,
    val name: String,
    /** At most 5 characters: the label under the tower. */
    val short: String,
    /** 0 to 7, into the category palette. */
    val colorIndex: Int,
    val capCents: Long? = null,
    val position: Int,
)

/** A brick (or several: one expense of any size). */
@Serializable
data class Expense(
    val id: String,
    val categoryId: String,
    /** Always positive. [refund] makes it count negative. */
    val amountCents: Long,
    val date: LocalDate,
    val note: String = "",
    /** A monthly bill: it repeats as a ghost brick next pay month. */
    val recurring: Boolean = false,
    /** Split 50/50 with the other member. */
    val shared: Boolean = false,
    val refund: Boolean = false,
    /** The member who added it. Set by the server on insert, never changed after. */
    val paidBy: String,
    /** When a settle-up covered it. Only shared bricks are ever settled. */
    val settledAt: Instant? = null,
    val createdAt: Instant,
)

val Expense.signedCents: Long get() = if (refund) -amountCents else amountCents

/** A day someone marked "€0 day". It is a gold day while it holds no moving brick. */
@Serializable
data class GoldMark(val date: LocalDate, val markedBy: String)

/** An expected monthly bill someone chose to skip for one pay month. */
@Serializable
data class BillSkip(val monthStart: LocalDate, val seriesKey: String)

@Serializable
data class Invite(
    /** XXXX-XXXX from 23456789ABCDEFGHJKLMNPQRSTUVWXYZ. */
    val code: String,
    val createdAt: Instant,
    val expiresAt: Instant,
    val canEdit: Boolean,
    val canManage: Boolean,
)

/** Everything about one town, as `GET /town` sends it. */
@Serializable
data class TownSnapshot(
    val town: Town,
    /** The member asking. Every "you" on a screen is this id. */
    val viewerId: String,
    val members: List<Member>,
    val categories: List<Category>,
    val expenses: List<Expense>,
    val goldMarks: List<GoldMark>,
    val billSkips: List<BillSkip>,
    /** Pending invites. Empty unless the viewer is the owner. */
    val invites: List<Invite>,
    val serverTime: Instant,
)
