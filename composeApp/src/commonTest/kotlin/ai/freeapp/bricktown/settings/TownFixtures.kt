package ai.freeapp.bricktown.settings

import ai.freeapp.bricktown.core.time.TownClock
import ai.freeapp.bricktown.domain.Category
import ai.freeapp.bricktown.domain.Currency
import ai.freeapp.bricktown.domain.Expense
import ai.freeapp.bricktown.domain.GoldMark
import ai.freeapp.bricktown.domain.Invite
import ai.freeapp.bricktown.domain.Member
import ai.freeapp.bricktown.domain.MemberRole
import ai.freeapp.bricktown.domain.Town
import ai.freeapp.bricktown.domain.TownSettings
import ai.freeapp.bricktown.domain.TownSnapshot
import ai.freeapp.bricktown.town.TownResult
import ai.freeapp.bricktown.wire.InviteRequest
import ai.freeapp.bricktown.wire.MemberUpdate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

val fixtureToday = LocalDate(2026, 11, 24)
val fixtureNow = Instant.parse("2026-11-24T10:00:00Z")

fun fixtureClock(day: LocalDate = fixtureToday) = TownClock { day }

fun member(id: String, name: String, owner: Boolean, color: Int, canEdit: Boolean = true, canManage: Boolean = false) =
    Member(id, name, name.take(1), color, if (owner) MemberRole.OWNER else MemberRole.MEMBER, canEdit, canManage, Instant.parse("2026-09-03T08:00:00Z"))

fun tower(id: String, name: String, position: Int, capCents: Long? = null) =
    Category(id, name, name.take(5), position, capCents, position)

fun brick(
    id: String,
    towerId: String,
    cents: Long,
    day: LocalDate = fixtureToday,
    paidBy: String = "you",
    shared: Boolean = false,
    settledAt: Instant? = null,
    recurring: Boolean = false,
    refund: Boolean = false,
) = Expense(
    id = id, categoryId = towerId, amountCents = cents, date = day, recurring = recurring, refund = refund,
    paidBy = paidBy, shared = shared, settledAt = settledAt, createdAt = fixtureNow,
)

fun townSnapshot(
    viewer: String = "you",
    members: List<Member> = listOf(member("you", "You", true, 0), member("ana", "Ana", false, 1)),
    categories: List<Category> = listOf(tower("food", "Food", 0, 25000), tower("fun", "Fun", 1), tower("home", "Home", 2)),
    expenses: List<Expense> = emptyList(),
    goldMarks: List<GoldMark> = emptyList(),
    invites: List<Invite> = emptyList(),
    settings: TownSettings = TownSettings(monthlyBudgetCents = 180000, payday = 1, currency = Currency.EUR, billsFirst = true, timeZone = "UTC"),
) = TownSnapshot(
    town = Town("town", "you", "Your town", settings, Instant.parse("2026-09-03T08:00:00Z")),
    viewerId = viewer,
    members = members,
    categories = categories,
    expenses = expenses,
    goldMarks = goldMarks,
    billSkips = emptyList(),
    invites = invites,
    serverTime = fixtureNow,
)

/** Applies every write to an in-memory town the way the repository does, and records the calls. */
class FakeTownGateway(initial: TownSnapshot? = townSnapshot()) : TownGateway {
    private val current = MutableStateFlow(initial)
    val calls = mutableListOf<String>()
    var inviteResult: TownResult<Invite> = TownResult.Ok(
        Invite("ABCD-2345", fixtureNow, Instant.parse("2026-12-01T10:00:00Z"), canEdit = true, canManage = false),
    )
    var endResult: TownResult<Unit> = TownResult.Ok(Unit)
    var lastInviteRequest: InviteRequest? = null

    override val town: StateFlow<TownSnapshot?> get() = current
    val snapshot: TownSnapshot get() = current.value!!

    /** What a refresh from the server would lay over the cache. */
    fun serverSends(next: TownSnapshot) {
        current.value = next
    }

    private fun edit(change: (TownSnapshot) -> TownSnapshot) = current.update { it?.let(change) }

    override suspend fun putSettings(settings: TownSettings) {
        calls += "putSettings"
        edit { it.copy(town = it.town.copy(settings = settings)) }
    }

    override suspend fun putCategory(category: Category) {
        calls += "putCategory:${category.id}"
        edit { s -> s.copy(categories = s.categories.filter { it.id != category.id } + category) }
    }

    override suspend fun deleteCategory(id: String, mergeInto: String?) {
        calls += "deleteCategory:$id->$mergeInto"
        edit { s ->
            s.copy(
                categories = s.categories.filter { it.id != id },
                expenses = if (mergeInto == null) s.expenses.filter { it.categoryId != id } else s.expenses.map { if (it.categoryId == id) it.copy(categoryId = mergeInto) else it },
            )
        }
    }

    override suspend fun reorderCategories(ids: List<String>) {
        calls += "reorder:${ids.joinToString(",")}"
        edit { s -> s.copy(categories = s.categories.map { it.copy(position = ids.indexOf(it.id)) }.sortedBy { it.position }) }
    }

    override suspend fun updateMember(userId: String, change: MemberUpdate) {
        calls += "updateMember:$userId"
        edit { s -> s.copy(members = s.members.map { if (it.userId == userId) it.copy(canEdit = change.canEdit ?: it.canEdit, canManage = change.canManage ?: it.canManage) else it }) }
    }

    override suspend fun settle(upTo: LocalDate) {
        calls += "settle:$upTo"
        edit { s -> s.copy(expenses = s.expenses.map { if (it.shared && it.settledAt == null && it.date <= upTo) it.copy(settledAt = fixtureNow) else it }) }
    }

    override suspend fun putExpense(expense: Expense) {
        calls += "putExpense:${expense.id}"
        edit { s -> s.copy(expenses = s.expenses.filter { it.id != expense.id } + expense) }
    }

    override suspend fun deleteExpense(id: String) {
        calls += "deleteExpense:$id"
        edit { s -> s.copy(expenses = s.expenses.filter { it.id != id }) }
    }

    override suspend fun markGoldDay(date: LocalDate) {
        calls += "markGoldDay:$date"
        edit { s -> s.copy(goldMarks = s.goldMarks + GoldMark(date, s.viewerId)) }
    }

    override suspend fun createInvite(request: InviteRequest): TownResult<Invite> {
        calls += "createInvite"
        lastInviteRequest = request
        val result = inviteResult
        if (result !is TownResult.Ok) return result
        // Like the server: the stored invite carries the permissions asked for, with a fresh code per ask.
        val stored = result.value.copy(code = "ABCD-${2345 + snapshot.invites.size}", canEdit = request.canEdit, canManage = request.canManage)
        edit { s -> s.copy(invites = s.invites + stored) }
        return TownResult.Ok(stored)
    }

    override suspend fun revokeInvite(code: String): TownResult<Unit> {
        calls += "revokeInvite:$code"
        edit { s -> s.copy(invites = s.invites.filter { it.code != code }) }
        return TownResult.Ok(Unit)
    }

    override suspend fun leave(): TownResult<Unit> {
        calls += "leave"
        return endResult.also { if (it is TownResult.Ok) current.value = null }
    }

    override suspend fun deleteTown(): TownResult<Unit> {
        calls += "deleteTown"
        return endResult.also { if (it is TownResult.Ok) current.value = null }
    }
}
