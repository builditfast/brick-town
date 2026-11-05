package ai.freeapp.bricktown.town

import ai.freeapp.bricktown.db.AppDatabase
import ai.freeapp.bricktown.db.CategoryRow
import ai.freeapp.bricktown.db.ExpenseRow
import ai.freeapp.bricktown.db.GoldMarkRow
import ai.freeapp.bricktown.db.MemberRow
import ai.freeapp.bricktown.db.TownRow
import ai.freeapp.bricktown.db.TownQueries
import ai.freeapp.bricktown.domain.BillSkip
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
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Rows to domain types and back. Booleans are stored as 0 or 1. */

private val storageJson = Json { ignoreUnknownKeys = true }

private val Boolean.stored: Long get() = if (this) 1L else 0L

internal fun TownRow.settings() = TownSettings(
    monthlyBudgetCents = monthlyBudgetCents,
    payday = payday.toInt(),
    currency = Currency.valueOf(currency),
    billsFirst = billsFirst != 0L,
    timeZone = timeZone,
)

internal fun TownRow.toDomain() = Town(id, ownerId, name, settings(), Instant.parse(createdAt))

internal fun MemberRow.toDomain() = Member(
    userId = userId,
    name = name,
    initial = initial,
    colorIndex = colorIndex.toInt(),
    role = MemberRole.valueOf(role),
    canEdit = canEdit != 0L,
    canManage = canManage != 0L,
    joinedAt = Instant.parse(joinedAt),
)

internal fun CategoryRow.toDomain() = Category(id, name, short, colorIndex.toInt(), capCents, position.toInt())

internal fun ExpenseRow.toDomain() = Expense(
    id = id,
    categoryId = categoryId,
    amountCents = amountCents,
    date = LocalDate.parse(date),
    note = note,
    recurring = recurring != 0L,
    shared = shared != 0L,
    refund = refund != 0L,
    paidBy = paidBy,
    settledAt = settledAt?.let(Instant::parse),
    createdAt = Instant.parse(createdAt),
)

internal fun GoldMarkRow.toDomain() = GoldMark(LocalDate.parse(date), markedBy)

internal fun ai.freeapp.bricktown.db.BillSkipRow.toDomain() = BillSkip(LocalDate.parse(monthStart), seriesKey)

internal fun TownRow.invites(): List<Invite> =
    storageJson.decodeFromString(ListSerializer(Invite.serializer()), invitesJson)

internal fun TownQueries.putTownPending(town: Town, viewerId: String, invites: List<Invite>, serverTime: Instant) =
    putTownPending(
        town.id, town.ownerId, town.name, town.settings.monthlyBudgetCents, town.settings.payday.toLong(),
        town.settings.currency.name, town.settings.billsFirst.stored, town.settings.timeZone,
        town.createdAt.toString(), viewerId, storageJson.encodeToString(ListSerializer(Invite.serializer()), invites),
        serverTime.toString(),
    )

internal fun TownQueries.putMemberPending(m: Member) = putMemberPending(
    m.userId, m.name, m.initial, m.colorIndex.toLong(), m.role.name, m.canEdit.stored, m.canManage.stored, m.joinedAt.toString(),
)

internal fun TownQueries.putCategoryPending(c: Category) =
    putCategoryPending(c.id, c.name, c.short, c.colorIndex.toLong(), c.capCents, c.position.toLong())

internal fun TownQueries.putExpensePending(e: Expense) = putExpensePending(
    e.id, e.categoryId, e.amountCents, e.date.toString(), e.note, e.recurring.stored, e.shared.stored, e.refund.stored,
    e.paidBy, e.settledAt?.toString(), e.createdAt.toString(),
)

internal fun TownQueries.lendExpense(e: Expense) = lendExpense(
    e.id, e.categoryId, e.amountCents, e.date.toString(), e.note, e.recurring.stored, e.shared.stored, e.refund.stored,
    e.paidBy, e.settledAt?.toString(), e.createdAt.toString(),
)

internal fun TownQueries.lendCategory(c: Category) =
    lendCategory(c.id, c.name, c.short, c.colorIndex.toLong(), c.capCents, c.position.toLong())

internal fun TownQueries.applyMember(m: Member) = applyMember(
    m.userId, m.name, m.initial, m.colorIndex.toLong(), m.role.name, m.canEdit.stored, m.canManage.stored, m.joinedAt.toString(),
)

/**
 * Lays the server's snapshot over the cache, inside the caller's transaction:
 * every row the server owns (pending = 0) is dropped and replaced, and a row
 * waiting for its send stays as the phone made it.
 */
internal fun TownQueries.lay(snapshot: TownSnapshot) {
    deleteSyncedTown()
    deleteSyncedMembers()
    deleteSyncedCategories()
    deleteSyncedExpenses()
    deleteSyncedGoldMarks()
    deleteSyncedBillSkips()
    val t = snapshot.town
    lendTown(
        t.id, t.ownerId, t.name, t.settings.monthlyBudgetCents, t.settings.payday.toLong(), t.settings.currency.name,
        t.settings.billsFirst.stored, t.settings.timeZone, t.createdAt.toString(), snapshot.viewerId,
        storageJson.encodeToString(ListSerializer(Invite.serializer()), snapshot.invites), snapshot.serverTime.toString(),
    )
    snapshot.members.forEach {
        lendMember(it.userId, it.name, it.initial, it.colorIndex.toLong(), it.role.name, it.canEdit.stored, it.canManage.stored, it.joinedAt.toString())
    }
    snapshot.categories.forEach(::lendCategory)
    snapshot.expenses.forEach(::lendExpense)
    snapshot.goldMarks.forEach { lendGold(it.date.toString(), it.markedBy) }
    snapshot.billSkips.forEach { lendBillSkip(it.monthStart.toString(), it.seriesKey) }
}

/** Empties every town table. Sign-out, leaving, a deleted town and a 404 all end here. */
internal fun TownQueries.wipe() {
    deleteAllTown()
    deleteAllMembers()
    deleteAllCategories()
    deleteAllExpenses()
    deleteAllGoldMarks()
    deleteAllBillSkips()
}

/** For `AuthRepositoryImpl.wipeLocalData`: the account's town must not outlive its session. */
internal fun AppDatabase.wipeTownData() = townQueries.wipe()
