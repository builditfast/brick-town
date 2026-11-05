package ai.freeapp.bricktown.town

import ai.freeapp.bricktown.api.ApiClient
import ai.freeapp.bricktown.api.ApiException
import ai.freeapp.bricktown.core.auth.ObservableTokenStore
import ai.freeapp.bricktown.core.sync.Outbox
import ai.freeapp.bricktown.core.sync.OutboxSender
import ai.freeapp.bricktown.db.AppDatabase
import ai.freeapp.bricktown.domain.BillSkip
import ai.freeapp.bricktown.domain.Category
import ai.freeapp.bricktown.domain.Expense
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
import ai.freeapp.bricktown.wire.SettleRequest
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * The phone's town: the cache the screens read, the writes they make, and the
 * refresh that keeps it equal to the server's.
 *
 * Storage rule (the same in every table, see Town.sq):
 *  - a local write lands with pending = 1, shows at once and goes to the outbox;
 *  - a delete keeps the row with deleted = 1 and hides it;
 *  - a refresh replaces, in one transaction, every pending = 0 row with the
 *    snapshot and keeps pending rows over the server's version;
 *  - an acknowledged send ends the pending state (a deleted row is removed);
 *  - [undo] drops the pending row and refreshes, which brings the server's row back.
 *
 * It is an [OutboxSender] for 12 kinds, bound in Koin with `bind OutboxSender::class`.
 * Every send tolerates a repeat: PUTs carry the row's own id, DELETE of a
 * missing id is a 204, POST /town is idempotent by id, and a settle repeated
 * finds nothing left to settle.
 */
class TownRepository(
    private val api: ApiClient,
    private val db: AppDatabase,
    private val outbox: Outbox,
    tokens: ObservableTokenStore,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : OutboxSender, TownLoader {
    private val queries = db.townQueries
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val json = Json { ignoreUnknownKeys = true }
    private val refreshing = Mutex()

    /**
     * Bumped by every local write and every acknowledgement. A snapshot asked
     * for before the bump is older than the cache, so a refresh that sees it
     * move while the request was out throws the snapshot away; the next
     * refresh, which an acknowledgement always triggers, lays a fresh one.
     */
    private val localWrites = MutableStateFlow(0)

    private val _status = MutableStateFlow(
        if (queries.countTownRows().executeAsOne() > 0) SyncStatus.Ready else SyncStatus.Loading,
    )
    override val status: StateFlow<SyncStatus> = _status.asStateFlow()

    init {
        // The rows go with AuthRepository's wipe; the status and any refresh
        // still out belong to the account that just left.
        tokens.doOnClear {
            bump()
            _status.value = SyncStatus.Loading
        }
    }

    private val _events = MutableSharedFlow<TownEvent>(extraBufferCapacity = 8)
    val events: Flow<TownEvent> = _events.asSharedFlow()

    override val outboxKinds: Set<String> = setOf(
        KIND_TOWN_CREATE, KIND_SETTINGS_PUT, KIND_CATEGORY_PUT, KIND_CATEGORY_DELETE, KIND_CATEGORIES_REORDER,
        KIND_EXPENSE_PUT, KIND_EXPENSE_DELETE, KIND_GOLD_PUT, KIND_GOLD_DELETE, KIND_BILLSKIP_PUT, KIND_SETTLE,
        KIND_MEMBER_UPDATE,
    )

    /** The town as the screens read it, or null with no town cached. Hidden (deleted) rows are not in it. */
    val town: Flow<TownSnapshot?> = run {
        val townRow = queries.selectTown().asFlow().mapToOneOrNull(dispatcher)
        val members = queries.selectMembers().asFlow().mapToList(dispatcher)
        val categories = queries.selectCategories().asFlow().mapToList(dispatcher)
        val bricks = combine(
            queries.selectExpenses().asFlow().mapToList(dispatcher),
            queries.selectGoldMarks().asFlow().mapToList(dispatcher),
            queries.selectBillSkips().asFlow().mapToList(dispatcher),
        ) { expenses, gold, skips -> Triple(expenses, gold, skips) }
        combine(townRow, members, categories, bricks) { row, memberRows, categoryRows, (expenseRows, goldRows, skipRows) ->
            row?.let {
                TownSnapshot(
                    town = it.toDomain(),
                    viewerId = it.viewerId,
                    members = memberRows.map { m -> m.toDomain() },
                    categories = categoryRows.map { c -> c.toDomain() },
                    expenses = expenseRows.map { e -> e.toDomain() },
                    goldMarks = goldRows.map { g -> g.toDomain() },
                    billSkips = skipRows.map { b -> b.toDomain() },
                    invites = it.invites(),
                    serverTime = kotlinx.datetime.Instant.parse(it.serverTime),
                )
            }
        }
    }

    // Refresh

    /**
     * Asks the server for the town and lays it over the cache. A 404 means
     * this account has no town (or it was deleted): see [handleNoTown].
     * Other failures throw, and the cache stays as it was.
     */
    override suspend fun refresh(): Unit = refreshing.withLock {
        if (_status.value == SyncStatus.Ready && queries.countTownRows().executeAsOne() == 0L) _status.value = SyncStatus.Loading
        val before = localWrites.value
        val snapshot = try {
            api.getTown()
        } catch (refused: ApiException) {
            if (refused.response.status != HttpStatusCode.NotFound) throw refused
            if (localWrites.value == before) handleNoTown()
            return@withLock
        }
        if (localWrites.value != before) return@withLock
        db.transaction { queries.lay(snapshot) }
        _status.value = SyncStatus.Ready
    }

    /**
     * The server has no town for this account.
     *  - A pending town row (a `town.create` waiting, or an edit waiting):
     *    the phone made it, so keep it. Its send decides.
     *  - A town row the server once confirmed: the owner deleted it. Wipe and say so.
     *  - No town cached: the account simply has none.
     */
    private fun handleNoTown() {
        val local = queries.selectTown().executeAsOneOrNull()
        when {
            local != null && local.pending != 0L -> Unit
            local != null -> {
                db.transaction { queries.wipe() }
                _status.value = SyncStatus.TownDeleted
            }
            else -> _status.value = SyncStatus.NoTown
        }
    }

    internal suspend fun refreshQuietly() {
        try {
            refresh()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            // The send already succeeded; the next refresh catches up.
        }
    }

    // Writes. Each shows at once, then goes through the outbox. A refusal has
    // already been undone and announced as a TownEvent by the time it returns.

    /** Onboarding's three steps in one write. [viewerId] and [viewerName] are the signed-in user. */
    suspend fun createTown(request: CreateTownRequest, viewerId: String, viewerName: String) {
        val now = Clock.System.now()
        val first = viewerName.trim().substringBefore(' ').ifEmpty { viewerName }
        bump()
        db.transaction {
            queries.wipe()
            queries.putTownPending(
                Town(request.id, viewerId, "$first's town", request.settings, now), viewerId, emptyList(), now,
            )
            queries.putMemberPending(
                Member(viewerId, viewerName, first.take(1).uppercase().ifEmpty { "A" }, 0, MemberRole.OWNER, true, true, now),
            )
            request.categories.forEach(queries::putCategoryPending)
            request.firstExpense?.let { queries.putExpensePending(it.copy(paidBy = viewerId, createdAt = now)) }
        }
        _status.value = SyncStatus.Ready
        queue(KIND_TOWN_CREATE, CreateTownRequest.serializer(), request)
    }

    suspend fun putSettings(settings: TownSettings) {
        val row = queries.selectTown().executeAsOneOrNull() ?: return
        bump()
        queries.putTownPending(
            row.toDomain().copy(settings = settings), row.viewerId, row.invites(), kotlinx.datetime.Instant.parse(row.serverTime),
        )
        queue(KIND_SETTINGS_PUT, TownSettings.serializer(), settings)
    }

    suspend fun putCategory(category: Category) {
        bump()
        queries.putCategoryPending(category)
        queue(KIND_CATEGORY_PUT, Category.serializer(), category)
    }

    /**
     * Deletes a tower. Its bricks move to [mergeInto] first (the server does
     * the same), so none is left under a hidden tower. The server refuses a
     * tower with bricks and no [mergeInto], and the last tower.
     */
    suspend fun deleteCategory(id: String, mergeInto: String? = null) {
        bump()
        val moved = db.transactionWithResult {
            val ids = if (mergeInto == null) emptyList() else
                queries.selectExpenses().executeAsList().filter { it.categoryId == id }.map { it.id }
            if (mergeInto != null) queries.moveExpensesPending(mergeInto, id)
            queries.hideCategory(id)
            ids
        }
        queue(KIND_CATEGORY_DELETE, CategoryDelete.serializer(), CategoryDelete(id, mergeInto, moved))
    }

    suspend fun reorderCategories(ids: List<String>) {
        bump()
        db.transaction { ids.forEachIndexed { index, id -> queries.setCategoryPosition(index.toLong(), id) } }
        queue(KIND_CATEGORIES_REORDER, CategoryOrder.serializer(), CategoryOrder(ids))
    }

    /** Adds or edits a brick. The server sets `paidBy` and `createdAt` on insert and keeps them after; the local row mirrors that. */
    suspend fun putExpense(expense: Expense) {
        val existing = queries.selectExpenseById(expense.id).executeAsOneOrNull()?.toDomain()
        val viewer = queries.selectTown().executeAsOneOrNull()?.viewerId ?: expense.paidBy
        val local = if (existing != null) {
            expense.copy(paidBy = existing.paidBy, createdAt = existing.createdAt, settledAt = existing.settledAt)
        } else {
            expense.copy(paidBy = viewer, createdAt = Clock.System.now(), settledAt = null)
        }
        bump()
        queries.putExpensePending(local)
        queue(KIND_EXPENSE_PUT, Expense.serializer(), local)
    }

    suspend fun deleteExpense(id: String) {
        bump()
        queries.hideExpense(id)
        queue(KIND_EXPENSE_DELETE, IdPayload.serializer(), IdPayload(id))
    }

    suspend fun markGoldDay(date: LocalDate) {
        val viewer = queries.selectTown().executeAsOneOrNull()?.viewerId ?: return
        bump()
        queries.putGoldPending(date.toString(), viewer)
        queue(KIND_GOLD_PUT, DatePayload.serializer(), DatePayload(date))
    }

    suspend fun unmarkGoldDay(date: LocalDate) {
        bump()
        queries.hideGold(date.toString())
        queue(KIND_GOLD_DELETE, DatePayload.serializer(), DatePayload(date))
    }

    suspend fun skipBill(skip: BillSkip) {
        bump()
        queries.putBillSkipPending(skip.monthStart.toString(), skip.seriesKey)
        queue(KIND_BILLSKIP_PUT, BillSkip.serializer(), skip)
    }

    /** Settles every unsettled shared brick dated up to [upTo]. */
    suspend fun settle(upTo: LocalDate) {
        bump()
        queries.settlePending(Clock.System.now().toString(), upTo.toString())
        queue(KIND_SETTLE, SettleRequest.serializer(), SettleRequest(upTo))
    }

    /** A member's permissions (owner only) or her own colour and initial. */
    suspend fun updateMember(userId: String, change: MemberUpdate) {
        val row = queries.selectMembers().executeAsList().firstOrNull { it.userId == userId } ?: return
        val member = row.toDomain()
        bump()
        queries.putMemberPending(
            member.copy(
                canEdit = change.canEdit ?: member.canEdit,
                canManage = change.canManage ?: member.canManage,
                colorIndex = change.colorIndex ?: member.colorIndex,
                initial = change.initial ?: member.initial,
            ),
        )
        queue(
            KIND_MEMBER_UPDATE, MemberChange.serializer(),
            MemberChange(userId, change.canEdit, change.canManage, change.colorIndex, change.initial),
        )
    }

    // Calls that need the server. No outbox: the answer is the point.

    suspend fun createInvite(request: InviteRequest = InviteRequest()): TownResult<Invite> =
        online { api.createInvite(request).also { refreshQuietly() } }

    suspend fun revokeInvite(code: String): TownResult<Unit> =
        online { api.revokeInvite(code).also { refreshQuietly() } }

    suspend fun previewInvite(code: String): TownResult<InvitePreview> = online { api.previewInvite(code) }

    /** Joins the town and caches it. The server's refusals ("This town already has two people.") come back as the message. */
    suspend fun acceptInvite(code: String, colorIndex: Int, initial: String): TownResult<TownSnapshot> = online {
        val snapshot = api.acceptInvite(code, AcceptInvite(colorIndex, initial))
        bump()
        db.transaction {
            queries.wipe()
            queries.lay(snapshot)
        }
        _status.value = SyncStatus.Ready
        snapshot
    }

    /** Leaves the town: her bricks go with her, so the cache empties. */
    suspend fun leave(): TownResult<Unit> = online { api.leaveTown().also { endTown() } }

    suspend fun deleteTown(): TownResult<Unit> = online { api.deleteTown().also { endTown() } }

    private fun endTown() {
        bump()
        outbox.clear()
        db.transaction { queries.wipe() }
        _status.value = SyncStatus.NoTown
    }

    // OutboxSender

    override suspend fun send(kind: String, payload: String) {
        when (kind) {
            KIND_TOWN_CREATE -> {
                val request = decode(CreateTownRequest.serializer(), payload)
                val snapshot = api.createTown(request)
                acknowledge {
                    if (queries.selectTown().executeAsOneOrNull()?.id == request.id) queries.lay(snapshot.settled())
                }
            }
            KIND_SETTINGS_PUT -> {
                val settings = decode(TownSettings.serializer(), payload)
                api.putSettings(settings)
                acknowledge {
                    val row = queries.selectTown().executeAsOneOrNull()
                    if (row != null && row.settings() == settings) queries.settleTownPending(row.id)
                }
            }
            KIND_CATEGORY_PUT -> {
                val category = decode(Category.serializer(), payload)
                val saved = api.putCategory(category)
                acknowledge {
                    if (queries.selectCategoryById(category.id).executeAsOneOrNull()?.toDomain() == category) {
                        queries.dropCategory(category.id)
                        queries.lendCategory(saved)
                    }
                }
            }
            KIND_CATEGORY_DELETE -> {
                val delete = decode(CategoryDelete.serializer(), payload)
                api.deleteCategory(delete.id, delete.mergeInto)
                acknowledge {
                    queries.dropCategory(delete.id)
                    delete.movedIds.forEach { moved ->
                        if (queries.selectExpenseById(moved).executeAsOneOrNull()?.categoryId == delete.mergeInto) {
                            queries.settleExpensePending(moved)
                        }
                    }
                }
            }
            KIND_CATEGORIES_REORDER -> {
                val order = decode(CategoryOrder.serializer(), payload)
                api.reorderCategories(order)
                acknowledge {
                    order.ids.forEachIndexed { index, id ->
                        if (queries.selectCategoryById(id).executeAsOneOrNull()?.position == index.toLong()) {
                            queries.settleCategoryPending(id)
                        }
                    }
                }
            }
            KIND_EXPENSE_PUT -> {
                val expense = decode(Expense.serializer(), payload)
                val saved = api.putExpense(expense)
                acknowledge {
                    if (queries.selectExpenseById(expense.id).executeAsOneOrNull()?.toDomain() == expense) {
                        queries.dropExpense(expense.id)
                        queries.lendExpense(saved)
                    }
                }
            }
            KIND_EXPENSE_DELETE -> {
                val id = decode(IdPayload.serializer(), payload).id
                api.deleteExpense(id)
                acknowledge { queries.dropExpense(id) }
            }
            KIND_GOLD_PUT -> {
                val date = decode(DatePayload.serializer(), payload).date
                api.putGoldMark(date)
                acknowledge { queries.settleGoldPending(date.toString()) }
            }
            KIND_GOLD_DELETE -> {
                val date = decode(DatePayload.serializer(), payload).date
                api.deleteGoldMark(date)
                acknowledge { queries.dropGold(date.toString()) }
            }
            KIND_BILLSKIP_PUT -> {
                val skip = decode(BillSkip.serializer(), payload)
                api.putBillSkip(skip)
                acknowledge { queries.settleBillSkipPending(skip.monthStart.toString(), skip.seriesKey) }
            }
            KIND_SETTLE -> {
                val request = decode(SettleRequest.serializer(), payload)
                val snapshot = api.settle(request)
                acknowledge {
                    queries.settleExpensesSettledUpTo(request.upTo.toString())
                    queries.lay(snapshot)
                }
            }
            KIND_MEMBER_UPDATE -> {
                val change = decode(MemberChange.serializer(), payload)
                val saved = api.updateMember(change.userId, MemberUpdate(change.canEdit, change.canManage, change.colorIndex, change.initial))
                acknowledge {
                    val local = queries.selectMembers().executeAsList().firstOrNull { it.userId == change.userId }?.toDomain()
                    if (local != null && change.matches(local)) queries.applyMember(saved)
                }
            }
            else -> error("TownRepository does not send \"$kind\".")
        }
        refreshQuietly()
    }

    /**
     * The server refused the write for good (not a network failure). The
     * pending state goes, the server's version comes back through a refresh,
     * and the screen hears about it once.
     */
    override fun undo(kind: String, payload: String) {
        db.transaction {
            when (kind) {
                KIND_TOWN_CREATE -> {
                    val request = decode(CreateTownRequest.serializer(), payload)
                    val row = queries.selectTown().executeAsOneOrNull()
                    if (row != null && row.id == request.id && row.pending != 0L) queries.wipe()
                }
                KIND_SETTINGS_PUT -> queries.selectTown().executeAsOneOrNull()?.let { queries.settleTownPending(it.id) }
                KIND_CATEGORY_PUT -> queries.dropCategory(decode(Category.serializer(), payload).id)
                KIND_CATEGORY_DELETE -> {
                    val delete = decode(CategoryDelete.serializer(), payload)
                    queries.dropCategory(delete.id)
                    delete.movedIds.forEach { queries.dropExpense(it) }
                }
                KIND_CATEGORIES_REORDER ->
                    decode(CategoryOrder.serializer(), payload).ids.forEach { queries.settleCategoryPending(it) }
                KIND_EXPENSE_PUT -> queries.dropExpense(decode(Expense.serializer(), payload).id)
                KIND_EXPENSE_DELETE -> queries.dropExpense(decode(IdPayload.serializer(), payload).id)
                KIND_GOLD_PUT, KIND_GOLD_DELETE -> queries.dropGold(decode(DatePayload.serializer(), payload).date.toString())
                KIND_BILLSKIP_PUT -> decode(BillSkip.serializer(), payload).let {
                    queries.settleBillSkipPending(it.monthStart.toString(), it.seriesKey)
                    queries.deleteBillSkip(it.monthStart.toString(), it.seriesKey)
                }
                KIND_SETTLE -> queries.settleExpensesSettledUpTo(decode(SettleRequest.serializer(), payload).upTo.toString())
                KIND_MEMBER_UPDATE -> queries.settleMemberPending(decode(MemberChange.serializer(), payload).userId)
            }
        }
        bump()
        _events.tryEmit(TownEvent.WriteNotSaved(kind, "${otherMemberName()} changed the town, so that change was not saved."))
        scope.launch { refreshQuietly() }
    }

    // Helpers

    private fun bump() = localWrites.update { it + 1 }

    private fun otherMemberName(): String {
        val viewer = queries.selectTown().executeAsOneOrNull()?.viewerId
        val other = queries.selectMembers().executeAsList().firstOrNull { it.userId != viewer }
        return other?.name?.substringBefore(' ')?.ifBlank { null } ?: "Someone"
    }

    private fun <T> decode(serializer: KSerializer<T>, payload: String): T = json.decodeFromString(serializer, payload)

    /** Sends or queues. A refusal was already undone and announced, so it ends here. */
    private suspend fun <T> queue(kind: String, serializer: KSerializer<T>, value: T) {
        try {
            outbox.sendOrQueue(kind, json.encodeToString(serializer, value))
        } catch (_: ApiException) {
            // Refused: undo() ran, the screen heard the event.
        }
    }

    /** Ends a send's pending state in one transaction, so a refresh never sees half of it. */
    private fun acknowledge(block: () -> Unit) {
        bump()
        db.transaction { block() }
    }

    private suspend fun <T> online(block: suspend () -> T): TownResult<T> = try {
        TownResult.Ok(block())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (refused: ApiException) {
        TownResult.Failed(refused.serverMessage ?: GENERIC_FAILURE, refused.response.status.value)
    } catch (_: Exception) {
        TownResult.Failed(NO_CONNECTION)
    }

    private fun MemberChange.matches(m: Member) =
        (canEdit == null || canEdit == m.canEdit) && (canManage == null || canManage == m.canManage) &&
            (colorIndex == null || colorIndex == m.colorIndex) && (initial == null || initial == m.initial)

    /** The create's own rows stop being pending first, so the snapshot replaces them like any server row. */
    private fun TownSnapshot.settled(): TownSnapshot {
        queries.settleTownPending(town.id)
        members.forEach { queries.settleMemberPending(it.userId) }
        categories.forEach { queries.settleCategoryPending(it.id) }
        expenses.forEach { queries.settleExpensePending(it.id) }
        return this
    }

    companion object {
        const val KIND_TOWN_CREATE = "town.create"
        const val KIND_SETTINGS_PUT = "settings.put"
        const val KIND_CATEGORY_PUT = "category.put"
        const val KIND_CATEGORY_DELETE = "category.delete"
        const val KIND_CATEGORIES_REORDER = "categories.reorder"
        const val KIND_EXPENSE_PUT = "expense.put"
        const val KIND_EXPENSE_DELETE = "expense.delete"
        const val KIND_GOLD_PUT = "goldmark.put"
        const val KIND_GOLD_DELETE = "goldmark.delete"
        const val KIND_BILLSKIP_PUT = "billskip.put"
        const val KIND_SETTLE = "settle"
        const val KIND_MEMBER_UPDATE = "member.update"

        const val NO_CONNECTION = "Can't reach the server. Check your connection and try again."
        const val GENERIC_FAILURE = "Something went wrong. Please try again."
    }
}
