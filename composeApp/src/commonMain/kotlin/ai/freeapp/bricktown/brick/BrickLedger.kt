package ai.freeapp.bricktown.brick

import ai.freeapp.bricktown.domain.BillSkip
import ai.freeapp.bricktown.domain.Category
import ai.freeapp.bricktown.domain.Expense
import ai.freeapp.bricktown.domain.TownSnapshot
import ai.freeapp.bricktown.town.TownRepository
import kotlinx.coroutines.flow.Flow

/**
 * The part of the town's data that the Month and brick screens use. The screens
 * depend on this seam, not on [TownRepository], so a ViewModel test can run on a
 * plain in-memory ledger.
 */
interface BrickLedger {
    val town: Flow<TownSnapshot?>
    suspend fun putExpense(expense: Expense)
    suspend fun deleteExpense(id: String)
    suspend fun putCategory(category: Category)
    suspend fun skipBill(skip: BillSkip)
}

class RepositoryBrickLedger(private val repository: TownRepository) : BrickLedger {
    override val town: Flow<TownSnapshot?> get() = repository.town
    override suspend fun putExpense(expense: Expense) = repository.putExpense(expense)
    override suspend fun deleteExpense(id: String) = repository.deleteExpense(id)
    override suspend fun putCategory(category: Category) = repository.putCategory(category)
    override suspend fun skipBill(skip: BillSkip) = repository.skipBill(skip)
}
