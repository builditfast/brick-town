package ai.freeapp.bricktown.town

import ai.freeapp.bricktown.auth.UsersTable
import ai.freeapp.bricktown.config.dbQuery
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
import ai.freeapp.bricktown.wire.AcceptInvite
import ai.freeapp.bricktown.wire.CategoryOrder
import ai.freeapp.bricktown.wire.CreateTownRequest
import ai.freeapp.bricktown.wire.InvitePreview
import ai.freeapp.bricktown.wire.InviteRequest
import ai.freeapp.bricktown.wire.MemberUpdate
import io.ktor.http.HttpStatusCode
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaInstant
import kotlinx.datetime.toKotlinInstant
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.lowerCase
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * Every town question starts from the caller's membership row, never from an
 * id in the path: a user can only reach the town they belong to. A refusal is
 * a [TownProblem] thrown inside the transaction, so the write rolls back.
 */
class TownRepository {

    /** Who the caller is inside their town. */
    private class Caller(
        val userId: UUID,
        val townId: UUID,
        val role: MemberRole,
        val canEdit: Boolean,
        val canManage: Boolean,
    ) {
        val isOwner get() = role == MemberRole.OWNER
        val mayManage get() = isOwner || canManage
        val mayEditOthers get() = isOwner || canEdit
    }

    // ---- reading ----

    suspend fun snapshot(userId: UUID): TownSnapshot? = dbQuery {
        val caller = callerOrNull(userId) ?: return@dbQuery null
        snapshotOf(caller)
    }

    /** The town's time zone, for "today" on the summary route. */
    suspend fun zoneOf(userId: UUID): TimeZone? = dbQuery {
        val caller = callerOrNull(userId) ?: return@dbQuery null
        TimeZone.of(townRow(caller.townId)[TownsTable.timeZone])
    }

    // ---- the town ----

    suspend fun create(userId: UUID, request: CreateTownRequest): TownSnapshot = dbQuery {
        val townId = parseId(request.id)
        val owned = TownsTable.selectAll().where { TownsTable.ownerId eq userId }.singleOrNull()
        if (owned != null) {
            if (owned[TownsTable.id] == townId) return@dbQuery snapshotOf(callerOrNull(userId)!!)
            problem(HttpStatusCode.Conflict, "You already have a town.")
        }
        if (callerOrNull(userId) != null) problem(HttpStatusCode.Conflict, "You already have a town. Leave it first.")
        if (!TownsTable.selectAll().where { TownsTable.id eq townId }.empty()) {
            problem(HttpStatusCode.Conflict, "That town id is already in use.")
        }

        TownRules.checkSettings(request.settings)
        val zone = TownRules.zoneOf(request.settings.timeZone)
        if (request.categories.isEmpty()) problem(HttpStatusCode.BadRequest, "A town needs at least one tower.")
        if (request.categories.size > TownRules.MAX_TOWERS) {
            problem(HttpStatusCode.BadRequest, "A town can have at most ${TownRules.MAX_TOWERS} towers.")
        }
        val categories = request.categories.map(TownRules::cleanCategory)
        if (categories.map { it.name.lowercase() }.toSet().size != categories.size) duplicateTower()
        if (categories.map { it.id }.toSet().size != categories.size) {
            problem(HttpStatusCode.BadRequest, "Every tower needs its own id.")
        }

        val user = UsersTable.selectAll().where { UsersTable.id eq userId }.single()
        val ownerName = user[UsersTable.name]
        val now = now()
        TownsTable.insert {
            it[id] = townId
            it[ownerId] = userId
            it[name] = townNameFor(ownerName)
            it[budgetCents] = request.settings.monthlyBudgetCents
            it[payday] = request.settings.payday
            it[currency] = request.settings.currency.name
            it[billsFirst] = request.settings.billsFirst
            it[timeZone] = request.settings.timeZone
            it[createdAt] = now
        }
        TownMembersTable.insert {
            it[TownMembersTable.townId] = townId
            it[TownMembersTable.userId] = userId
            it[initial] = ownerName.trim().firstOrNull()?.takeIf { c -> c.isLetter() }?.uppercase() ?: "?"
            it[colorIndex] = 0
            it[role] = MemberRole.OWNER.name
            it[canEdit] = true
            it[canManage] = true
            it[joinedAt] = now
        }
        categories.forEach { category ->
            CategoriesTable.insert { row ->
                row[id] = parseId(category.id)
                row[CategoriesTable.townId] = townId
                row[name] = category.name
                row[short] = category.short
                row[colorIndex] = category.colorIndex
                row[capCents] = category.capCents
                row[position] = category.position
            }
        }
        request.firstExpense?.let { first ->
            val caller = callerOrNull(userId)!!
            writeExpense(caller, first, zone)
        }
        snapshotOf(callerOrNull(userId)!!)
    }

    suspend fun putSettings(userId: UUID, settings: TownSettings): TownSettings = dbQuery {
        val caller = manager(userId)
        TownRules.checkSettings(settings)
        TownsTable.update({ TownsTable.id eq caller.townId }) {
            it[budgetCents] = settings.monthlyBudgetCents
            it[payday] = settings.payday
            it[currency] = settings.currency.name
            it[billsFirst] = settings.billsFirst
            it[timeZone] = settings.timeZone
        }
        settings
    }

    suspend fun deleteTown(userId: UUID) = dbQuery {
        val caller = member(userId)
        if (!caller.isOwner) problem(HttpStatusCode.Forbidden, "Only the owner can delete the town.")
        TownsTable.deleteWhere { TownsTable.id eq caller.townId }
        Unit
    }

    suspend fun leave(userId: UUID) = dbQuery {
        val caller = member(userId)
        if (caller.isOwner) problem(HttpStatusCode.Forbidden, "The owner can't leave. Delete the town instead.")
        ExpensesTable.deleteWhere { (ExpensesTable.townId eq caller.townId) and (ExpensesTable.paidBy eq userId) }
        GoldMarksTable.deleteWhere { (GoldMarksTable.townId eq caller.townId) and (GoldMarksTable.markedBy eq userId) }
        TownMembersTable.deleteWhere { (TownMembersTable.townId eq caller.townId) and (TownMembersTable.userId eq userId) }
        Unit
    }

    // ---- towers ----

    suspend fun putCategory(userId: UUID, id: UUID, body: Category): Category = dbQuery {
        val caller = manager(userId)
        val category = TownRules.cleanCategory(body.copy(id = id.toString()))
        val existing = CategoriesTable.selectAll().where { CategoriesTable.id eq id }.singleOrNull()
        if (existing != null && existing[CategoriesTable.townId] != caller.townId) {
            problem(HttpStatusCode.Conflict, "That tower id is already in use.")
        }
        val sameName = CategoriesTable.selectAll().where {
            (CategoriesTable.townId eq caller.townId) and
                (CategoriesTable.name.lowerCase() eq category.name.lowercase()) and
                (CategoriesTable.id neq id)
        }
        if (!sameName.empty()) duplicateTower()

        if (existing == null) {
            val count = CategoriesTable.selectAll().where { CategoriesTable.townId eq caller.townId }.count()
            if (count >= TownRules.MAX_TOWERS) {
                problem(HttpStatusCode.Conflict, "A town can have at most ${TownRules.MAX_TOWERS} towers.")
            }
            CategoriesTable.insert {
                it[CategoriesTable.id] = id
                it[townId] = caller.townId
                it[name] = category.name
                it[short] = category.short
                it[colorIndex] = category.colorIndex
                it[capCents] = category.capCents
                it[position] = category.position
            }
        } else {
            CategoriesTable.update({ CategoriesTable.id eq id }) {
                it[name] = category.name
                it[short] = category.short
                it[colorIndex] = category.colorIndex
                it[capCents] = category.capCents
                it[position] = category.position
            }
        }
        category
    }

    suspend fun reorderCategories(userId: UUID, order: CategoryOrder) = dbQuery {
        val caller = manager(userId)
        val ids = order.ids.map(::parseId)
        val current = CategoriesTable.selectAll().where { CategoriesTable.townId eq caller.townId }
            .map { it[CategoriesTable.id] }.toSet()
        if (ids.size != current.size || ids.toSet() != current) {
            problem(HttpStatusCode.BadRequest, "The tower order must list every tower once.")
        }
        ids.forEachIndexed { index, id ->
            CategoriesTable.update({ CategoriesTable.id eq id }) { it[position] = index }
        }
        Unit
    }

    suspend fun deleteCategory(userId: UUID, id: UUID, mergeInto: UUID?) = dbQuery {
        val caller = manager(userId)
        val existing = CategoriesTable.selectAll()
            .where { (CategoriesTable.id eq id) and (CategoriesTable.townId eq caller.townId) }
            .singleOrNull() ?: return@dbQuery Unit
        val count = CategoriesTable.selectAll().where { CategoriesTable.townId eq caller.townId }.count()
        if (count <= 1) problem(HttpStatusCode.Conflict, "A town needs at least one tower.")
        val hasBricks = !ExpensesTable.selectAll().where { ExpensesTable.categoryId eq id }.empty()
        if (hasBricks) {
            if (mergeInto == null) problem(HttpStatusCode.Conflict, "Choose where its bricks go.")
            val target = CategoriesTable.selectAll()
                .where { (CategoriesTable.id eq mergeInto) and (CategoriesTable.townId eq caller.townId) }
                .singleOrNull()
            if (target == null || mergeInto == existing[CategoriesTable.id]) {
                problem(HttpStatusCode.BadRequest, "Choose another tower to move the bricks to.")
            }
            ExpensesTable.update({ ExpensesTable.categoryId eq id }) { it[categoryId] = mergeInto }
        }
        CategoriesTable.deleteWhere { CategoriesTable.id eq id }
        Unit
    }

    // ---- bricks ----

    suspend fun putExpense(userId: UUID, id: UUID, body: Expense): Expense = dbQuery {
        val caller = member(userId)
        val zone = TimeZone.of(townRow(caller.townId)[TownsTable.timeZone])
        writeExpense(caller, body.copy(id = id.toString()), zone)
    }

    suspend fun deleteExpense(userId: UUID, id: UUID) = dbQuery {
        val caller = member(userId)
        val row = ExpensesTable.selectAll()
            .where { (ExpensesTable.id eq id) and (ExpensesTable.townId eq caller.townId) }
            .singleOrNull() ?: return@dbQuery Unit
        if (row[ExpensesTable.paidBy] != userId && !caller.mayEditOthers) {
            problem(HttpStatusCode.Forbidden, "You can't change bricks that someone else added.")
        }
        ExpensesTable.deleteWhere { ExpensesTable.id eq id }
        Unit
    }

    suspend fun settle(userId: UUID, upTo: LocalDate): TownSnapshot = dbQuery {
        val caller = member(userId)
        val now = now()
        ExpensesTable.update({
            (ExpensesTable.townId eq caller.townId) and
                (ExpensesTable.shared eq true) and
                ExpensesTable.settledAt.isNull() and
                (ExpensesTable.date lessEq upTo)
        }) { it[settledAt] = now }
        snapshotOf(caller)
    }

    // ---- gold days and skipped bills ----

    suspend fun putGold(userId: UUID, date: LocalDate) = dbQuery {
        val caller = member(userId)
        TownRules.checkDate(date, TimeZone.of(townRow(caller.townId)[TownsTable.timeZone]))
        GoldMarksTable.insertIgnore {
            it[townId] = caller.townId
            it[GoldMarksTable.date] = date
            it[markedBy] = userId
        }
        Unit
    }

    suspend fun deleteGold(userId: UUID, date: LocalDate) = dbQuery {
        val caller = member(userId)
        GoldMarksTable.deleteWhere { (GoldMarksTable.townId eq caller.townId) and (GoldMarksTable.date eq date) }
        Unit
    }

    suspend fun putBillSkip(userId: UUID, skip: BillSkip) = dbQuery {
        val caller = member(userId)
        val key = skip.seriesKey.trim()
        if (key.isEmpty() || key.length > TownRules.MAX_SERIES_KEY) {
            problem(HttpStatusCode.BadRequest, "That bill cannot be skipped.")
        }
        BillSkipsTable.insertIgnore {
            it[townId] = caller.townId
            it[monthStart] = skip.monthStart
            it[seriesKey] = key
        }
        Unit
    }

    // ---- members and invites ----

    suspend fun updateMember(userId: UUID, targetId: UUID, change: MemberUpdate): Member = dbQuery {
        val caller = member(userId)
        val target = TownMembersTable.selectAll()
            .where { (TownMembersTable.townId eq caller.townId) and (TownMembersTable.userId eq targetId) }
            .singleOrNull() ?: problem(HttpStatusCode.NotFound, "That person is not in your town.")
        val self = targetId == userId
        if ((change.canEdit != null || change.canManage != null) && !caller.isOwner) {
            problem(HttpStatusCode.Forbidden, "Only the owner can change what people may do.")
        }
        if ((change.colorIndex != null || change.initial != null) && !self) {
            problem(HttpStatusCode.Forbidden, "You can only change your own colour and initial.")
        }
        change.colorIndex?.let { color ->
            TownRules.checkColor(color)
            val taken = TownMembersTable.selectAll().where {
                (TownMembersTable.townId eq caller.townId) and (TownMembersTable.colorIndex eq color) and
                    (TownMembersTable.userId neq targetId)
            }
            if (!taken.empty()) problem(HttpStatusCode.Conflict, "That colour is taken.")
        }
        val initial = change.initial?.let(TownRules::cleanInitial)
        TownMembersTable.update({
            (TownMembersTable.townId eq caller.townId) and (TownMembersTable.userId eq targetId)
        }) {
            change.canEdit?.let { v -> it[canEdit] = v }
            change.canManage?.let { v -> it[canManage] = v }
            change.colorIndex?.let { v -> it[colorIndex] = v }
            initial?.let { v -> it[TownMembersTable.initial] = v }
        }
        val row = TownMembersTable.selectAll()
            .where { (TownMembersTable.townId eq caller.townId) and (TownMembersTable.userId eq targetId) }
            .single()
        memberOf(row, userNames(setOf(targetId)))
    }

    suspend fun createInvite(userId: UUID, request: InviteRequest): Invite = dbQuery {
        val caller = member(userId)
        if (!caller.isOwner) problem(HttpStatusCode.Forbidden, "Only the owner can invite people.")
        if (memberCount(caller.townId) >= TownRules.MAX_MEMBERS) {
            problem(HttpStatusCode.Conflict, "Your town is full.")
        }
        val now = now()
        val expires = now.plusSeconds(InviteCodes.EXPIRY.inWholeSeconds)
        var code = InviteCodes.generate()
        while (!InvitesTable.selectAll().where { InvitesTable.code eq code }.empty()) code = InviteCodes.generate()
        InvitesTable.insert {
            it[InvitesTable.code] = code
            it[townId] = caller.townId
            it[canEdit] = request.canEdit
            it[canManage] = request.canManage
            it[createdAt] = now
            it[expiresAt] = expires
        }
        Invite(code, now.toKotlin(), expires.toKotlin(), request.canEdit, request.canManage)
    }

    suspend fun revokeInvite(userId: UUID, code: String) = dbQuery {
        val caller = member(userId)
        if (!caller.isOwner) problem(HttpStatusCode.Forbidden, "Only the owner can cancel an invite.")
        InvitesTable.update({
            (InvitesTable.code eq code) and (InvitesTable.townId eq caller.townId) and InvitesTable.revokedAt.isNull()
        }) { it[revokedAt] = now() }
        Unit
    }

    suspend fun previewInvite(code: String): InvitePreview = dbQuery {
        val invite = liveInvite(code)
        val townId = invite[InvitesTable.townId]
        val town = townRow(townId)
        val owner = UsersTable.selectAll().where { UsersTable.id eq town[TownsTable.ownerId] }.single()
        InvitePreview(
            code = code,
            townName = town[TownsTable.name],
            ownerName = owner[UsersTable.name],
            categories = categoriesOf(townId),
            takenColors = TownMembersTable.selectAll().where { TownMembersTable.townId eq townId }
                .map { it[TownMembersTable.colorIndex] }.sorted(),
            expiresAt = invite[InvitesTable.expiresAt].toKotlin(),
        )
    }

    suspend fun acceptInvite(userId: UUID, code: String, body: AcceptInvite): TownSnapshot = dbQuery {
        val raw = InvitesTable.selectAll().where { InvitesTable.code eq code }.singleOrNull()
        val current = callerOrNull(userId)
        // A repeated accept (the answer was lost) lands on the town already joined.
        if (raw != null && current != null && current.townId == raw[InvitesTable.townId]) {
            return@dbQuery snapshotOf(current)
        }
        val invite = liveInvite(code)
        if (current != null) problem(HttpStatusCode.Conflict, "You already have a town. Leave it first.")
        val townId = invite[InvitesTable.townId]
        if (memberCount(townId) >= TownRules.MAX_MEMBERS) {
            problem(HttpStatusCode.Conflict, "This town already has two people.")
        }
        TownRules.checkColor(body.colorIndex)
        val initial = TownRules.cleanInitial(body.initial)
        val taken = TownMembersTable.selectAll()
            .where { (TownMembersTable.townId eq townId) and (TownMembersTable.colorIndex eq body.colorIndex) }
        if (!taken.empty()) problem(HttpStatusCode.Conflict, "That colour is taken.")

        val now = now()
        TownMembersTable.insert {
            it[TownMembersTable.townId] = townId
            it[TownMembersTable.userId] = userId
            it[TownMembersTable.initial] = initial
            it[colorIndex] = body.colorIndex
            it[role] = MemberRole.MEMBER.name
            it[canEdit] = invite[InvitesTable.canEdit]
            it[canManage] = invite[InvitesTable.canManage]
            it[joinedAt] = now
        }
        // One code, one person: it stops working once used.
        InvitesTable.update({ InvitesTable.code eq code }) { it[revokedAt] = now }
        snapshotOf(callerOrNull(userId)!!)
    }

    /** The town name and code for the public join page; null when the code is not live. */
    suspend fun joinPageTown(code: String): String? = dbQuery {
        val invite = InvitesTable.selectAll().where { InvitesTable.code eq code }.singleOrNull()
            ?.takeIf { it[InvitesTable.revokedAt] == null && it[InvitesTable.expiresAt].isAfter(now()) }
            ?: return@dbQuery null
        townRow(invite[InvitesTable.townId])[TownsTable.name]
    }

    // ---- internals ----

    private fun callerOrNull(userId: UUID): Caller? =
        TownMembersTable.selectAll().where { TownMembersTable.userId eq userId }.singleOrNull()?.let {
            Caller(
                userId = userId,
                townId = it[TownMembersTable.townId],
                role = MemberRole.valueOf(it[TownMembersTable.role]),
                canEdit = it[TownMembersTable.canEdit],
                canManage = it[TownMembersTable.canManage],
            )
        }

    private fun member(userId: UUID): Caller =
        callerOrNull(userId) ?: problem(HttpStatusCode.NotFound, NOT_IN_A_TOWN)

    private fun manager(userId: UUID): Caller {
        val caller = member(userId)
        if (!caller.mayManage) {
            problem(HttpStatusCode.Forbidden, "You don't have permission to change the town.")
        }
        return caller
    }

    private fun townRow(townId: UUID): ResultRow =
        TownsTable.selectAll().where { TownsTable.id eq townId }.single()

    private fun memberCount(townId: UUID): Long =
        TownMembersTable.selectAll().where { TownMembersTable.townId eq townId }.count()

    private fun liveInvite(code: String): ResultRow =
        InvitesTable.selectAll().where { InvitesTable.code eq code }.singleOrNull()
            ?.takeIf { it[InvitesTable.revokedAt] == null && it[InvitesTable.expiresAt].isAfter(now()) }
            ?: problem(HttpStatusCode.NotFound, "That invite has expired or was revoked.")

    /** Inserts or updates one brick. On update paidBy, createdAt and settledAt stay as stored. */
    private fun writeExpense(caller: Caller, body: Expense, zone: TimeZone): Expense {
        val id = parseId(body.id)
        TownRules.checkAmount(body.amountCents)
        TownRules.checkNote(body.note)
        TownRules.checkDate(body.date, zone)
        val categoryId = parseId(body.categoryId)
        val tower = CategoriesTable.selectAll()
            .where { (CategoriesTable.id eq categoryId) and (CategoriesTable.townId eq caller.townId) }
        if (tower.empty()) problem(HttpStatusCode.BadRequest, "That tower is not in your town.")

        val existing = ExpensesTable.selectAll().where { ExpensesTable.id eq id }.singleOrNull()
        if (existing == null) {
            val now = now()
            ExpensesTable.insert {
                it[ExpensesTable.id] = id
                it[townId] = caller.townId
                it[ExpensesTable.categoryId] = categoryId
                it[amountCents] = body.amountCents
                it[date] = body.date
                it[note] = body.note
                it[recurring] = body.recurring
                it[shared] = body.shared
                it[refund] = body.refund
                it[paidBy] = caller.userId
                it[createdAt] = now
            }
        } else {
            if (existing[ExpensesTable.townId] != caller.townId) {
                problem(HttpStatusCode.Conflict, "That brick id is already in use.")
            }
            if (existing[ExpensesTable.paidBy] != caller.userId && !caller.mayEditOthers) {
                problem(HttpStatusCode.Forbidden, "You can't change bricks that someone else added.")
            }
            ExpensesTable.update({ ExpensesTable.id eq id }) {
                it[ExpensesTable.categoryId] = categoryId
                it[amountCents] = body.amountCents
                it[date] = body.date
                it[note] = body.note
                it[recurring] = body.recurring
                it[shared] = body.shared
                it[refund] = body.refund
            }
        }
        return expenseOf(ExpensesTable.selectAll().where { ExpensesTable.id eq id }.single())
    }

    private fun snapshotOf(caller: Caller): TownSnapshot {
        val townId = caller.townId
        val town = townRow(townId)
        val memberRows = TownMembersTable.selectAll().where { TownMembersTable.townId eq townId }
            .orderBy(TownMembersTable.joinedAt).toList()
        val names = userNames(memberRows.map { it[TownMembersTable.userId] }.toSet())
        return TownSnapshot(
            town = Town(
                id = townId.toString(),
                ownerId = town[TownsTable.ownerId].toString(),
                name = town[TownsTable.name],
                settings = TownSettings(
                    monthlyBudgetCents = town[TownsTable.budgetCents],
                    payday = town[TownsTable.payday],
                    currency = Currency.valueOf(town[TownsTable.currency]),
                    billsFirst = town[TownsTable.billsFirst],
                    timeZone = town[TownsTable.timeZone],
                ),
                createdAt = town[TownsTable.createdAt].toKotlin(),
            ),
            viewerId = caller.userId.toString(),
            members = memberRows.map { memberOf(it, names) },
            categories = categoriesOf(townId),
            expenses = ExpensesTable.selectAll().where { ExpensesTable.townId eq townId }
                .orderBy(ExpensesTable.date to SortOrder.ASC, ExpensesTable.createdAt to SortOrder.ASC)
                .map(::expenseOf),
            goldMarks = GoldMarksTable.selectAll().where { GoldMarksTable.townId eq townId }
                .orderBy(GoldMarksTable.date)
                .map { GoldMark(it[GoldMarksTable.date], it[GoldMarksTable.markedBy].toString()) },
            billSkips = BillSkipsTable.selectAll().where { BillSkipsTable.townId eq townId }
                .map { BillSkip(it[BillSkipsTable.monthStart], it[BillSkipsTable.seriesKey]) },
            invites = if (caller.isOwner) invitesOf(townId) else emptyList(),
            serverTime = now().toKotlin(),
        )
    }

    private fun categoriesOf(townId: UUID): List<Category> =
        CategoriesTable.selectAll().where { CategoriesTable.townId eq townId }
            .orderBy(CategoriesTable.position).map {
                Category(
                    id = it[CategoriesTable.id].toString(),
                    name = it[CategoriesTable.name],
                    short = it[CategoriesTable.short],
                    colorIndex = it[CategoriesTable.colorIndex],
                    capCents = it[CategoriesTable.capCents],
                    position = it[CategoriesTable.position],
                )
            }

    private fun invitesOf(townId: UUID): List<Invite> {
        val now = now()
        return InvitesTable.selectAll().where {
            (InvitesTable.townId eq townId) and InvitesTable.revokedAt.isNull() and (InvitesTable.expiresAt greater now)
        }.orderBy(InvitesTable.createdAt).map {
            Invite(
                code = it[InvitesTable.code],
                createdAt = it[InvitesTable.createdAt].toKotlin(),
                expiresAt = it[InvitesTable.expiresAt].toKotlin(),
                canEdit = it[InvitesTable.canEdit],
                canManage = it[InvitesTable.canManage],
            )
        }
    }

    private fun memberOf(row: ResultRow, names: Map<UUID, String>): Member {
        val userId = row[TownMembersTable.userId]
        return Member(
            userId = userId.toString(),
            name = names[userId].orEmpty(),
            initial = row[TownMembersTable.initial],
            colorIndex = row[TownMembersTable.colorIndex],
            role = MemberRole.valueOf(row[TownMembersTable.role]),
            canEdit = row[TownMembersTable.canEdit],
            canManage = row[TownMembersTable.canManage],
            joinedAt = row[TownMembersTable.joinedAt].toKotlin(),
        )
    }

    private fun expenseOf(row: ResultRow) = Expense(
        id = row[ExpensesTable.id].toString(),
        categoryId = row[ExpensesTable.categoryId].toString(),
        amountCents = row[ExpensesTable.amountCents],
        date = row[ExpensesTable.date],
        note = row[ExpensesTable.note],
        recurring = row[ExpensesTable.recurring],
        shared = row[ExpensesTable.shared],
        refund = row[ExpensesTable.refund],
        paidBy = row[ExpensesTable.paidBy].toString(),
        settledAt = row[ExpensesTable.settledAt]?.toKotlin(),
        createdAt = row[ExpensesTable.createdAt].toKotlin(),
    )

    /** Names only: the snapshot never carries an email. */
    private fun userNames(ids: Set<UUID>): Map<UUID, String> =
        UsersTable.select(UsersTable.id, UsersTable.name).where { UsersTable.id inList ids }
            .associate { it[UsersTable.id] to it[UsersTable.name] }

    private fun duplicateTower(): Nothing =
        problem(HttpStatusCode.Conflict, "You already have a tower with that name.")

    private fun parseId(raw: String): UUID =
        runCatching { UUID.fromString(raw) }.getOrNull()
            ?: problem(HttpStatusCode.BadRequest, "That id is not valid.")

    private fun townNameFor(ownerName: String): String {
        val first = ownerName.trim().substringBefore(' ').ifEmpty { "My" }
        return "$first's town"
    }

    private fun now(): OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC)

    private fun OffsetDateTime.toKotlin(): Instant = toInstant().toKotlinInstant()

    companion object {
        const val NOT_IN_A_TOWN = "You are not in a town yet."
    }
}
