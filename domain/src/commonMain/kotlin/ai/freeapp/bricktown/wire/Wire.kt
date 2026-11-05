package ai.freeapp.bricktown.wire

import ai.freeapp.bricktown.domain.Category
import ai.freeapp.bricktown.domain.Expense
import ai.freeapp.bricktown.domain.TownSettings
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/**
 * Request and response bodies that are not plain entities. The full endpoint
 * table, with who may call each and which flag gates it, is section 5 of
 * docs/superpowers/specs/2026-11-04-brick-town-design.md.
 *
 * A PUT body that is an entity ([Category], [Expense]) is taken as the whole
 * new row. On an [Expense] the server ignores `paidBy`, `createdAt` and
 * `settledAt` from the body: it sets the first two on insert and keeps them.
 */

/** POST /town: onboarding's three steps in one write. Idempotent by [id]. */
@Serializable
data class CreateTownRequest(
    val id: String,
    val settings: TownSettings,
    val categories: List<Category>,
    val firstExpense: Expense? = null,
)

/** PUT /town/categories/order. */
@Serializable
data class CategoryOrder(val ids: List<String>)

/** POST /town/settle: settles every unsettled shared brick dated up to [upTo]. */
@Serializable
data class SettleRequest(val upTo: LocalDate)

/** PUT /town/members/{userId}. Null fields stay as they are. Permissions: owner only. Colour and initial: that member only. */
@Serializable
data class MemberUpdate(
    val canEdit: Boolean? = null,
    val canManage: Boolean? = null,
    val colorIndex: Int? = null,
    val initial: String? = null,
)

/** POST /town/invites. */
@Serializable
data class InviteRequest(val canEdit: Boolean = true, val canManage: Boolean = false)

/** GET /invites/{code}: what the join screen shows before accepting. */
@Serializable
data class InvitePreview(
    val code: String,
    val townName: String,
    val ownerName: String,
    val categories: List<Category>,
    /** Member colours already taken in that town. */
    val takenColors: List<Int>,
    val expiresAt: Instant,
)

/** POST /invites/{code}/accept. Answers the joined town's snapshot. */
@Serializable
data class AcceptInvite(val colorIndex: Int, val initial: String)

/** GET /town/summary?date=YYYY-MM-DD: what the voice agent reads out. Computed by the domain's summarize(). */
@Serializable
data class AgentSummary(
    val date: LocalDate,
    val monthName: String,
    val dayNumber: Int,
    val currency: String,
    val budgetCents: Long,
    val spentCents: Long,
    val billsCents: Long,
    val dailyBudgetCents: Long,
    val todaySpentCents: Long,
    val todayLeftCents: Long,
    val monthLeftCents: Long,
    val aheadCents: Long,
    val streak: Int,
    val towers: List<AgentTower>,
    /** One spoken sentence, no symbols beyond the currency: "You have 57 euros left today and 488 for the month." */
    val sentence: String,
)

@Serializable
data class AgentTower(
    val id: String,
    val name: String,
    val totalCents: Long,
    val bricks: Int,
    val capCents: Long? = null,
)
