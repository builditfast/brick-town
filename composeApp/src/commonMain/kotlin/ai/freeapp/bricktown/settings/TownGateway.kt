package ai.freeapp.bricktown.settings

import ai.freeapp.bricktown.domain.Category
import ai.freeapp.bricktown.domain.Expense
import ai.freeapp.bricktown.domain.Invite
import ai.freeapp.bricktown.domain.TownSettings
import ai.freeapp.bricktown.domain.TownSnapshot
import ai.freeapp.bricktown.town.TownRepository
import ai.freeapp.bricktown.town.TownResult
import ai.freeapp.bricktown.wire.InviteRequest
import ai.freeapp.bricktown.wire.MemberUpdate
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

/**
 * The slice of [TownRepository] the Settings, towers, invite, settle and import
 * ViewModels use. TownRepository is a concrete class bound to SQLDelight, so the
 * ViewModels talk to this interface and the tests swap in a recording fake.
 */
interface TownGateway {
    val town: Flow<TownSnapshot?>

    suspend fun putSettings(settings: TownSettings)
    suspend fun putCategory(category: Category)
    suspend fun deleteCategory(id: String, mergeInto: String?)
    suspend fun reorderCategories(ids: List<String>)
    suspend fun updateMember(userId: String, change: MemberUpdate)
    suspend fun settle(upTo: LocalDate)
    suspend fun putExpense(expense: Expense)
    suspend fun deleteExpense(id: String)
    suspend fun markGoldDay(date: LocalDate)

    suspend fun createInvite(request: InviteRequest): TownResult<Invite>
    suspend fun revokeInvite(code: String): TownResult<Unit>
    suspend fun leave(): TownResult<Unit>
    suspend fun deleteTown(): TownResult<Unit>
}

class RepositoryTownGateway(private val repository: TownRepository) : TownGateway {
    override val town: Flow<TownSnapshot?> get() = repository.town

    override suspend fun putSettings(settings: TownSettings) = repository.putSettings(settings)
    override suspend fun putCategory(category: Category) = repository.putCategory(category)
    override suspend fun deleteCategory(id: String, mergeInto: String?) = repository.deleteCategory(id, mergeInto)
    override suspend fun reorderCategories(ids: List<String>) = repository.reorderCategories(ids)
    override suspend fun updateMember(userId: String, change: MemberUpdate) = repository.updateMember(userId, change)
    override suspend fun settle(upTo: LocalDate) = repository.settle(upTo)
    override suspend fun putExpense(expense: Expense) = repository.putExpense(expense)
    override suspend fun deleteExpense(id: String) = repository.deleteExpense(id)
    override suspend fun markGoldDay(date: LocalDate) = repository.markGoldDay(date)

    override suspend fun createInvite(request: InviteRequest) = repository.createInvite(request)
    override suspend fun revokeInvite(code: String) = repository.revokeInvite(code)
    override suspend fun leave() = repository.leave()
    override suspend fun deleteTown() = repository.deleteTown()
}
