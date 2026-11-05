package ai.freeapp.bricktown.month

import ai.freeapp.bricktown.brick.BrickLedger
import ai.freeapp.bricktown.core.time.TownClock
import ai.freeapp.bricktown.domain.BillSkip
import ai.freeapp.bricktown.domain.Category
import ai.freeapp.bricktown.domain.Expense
import ai.freeapp.bricktown.domain.TownSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** An in-memory town: writes change the snapshot the way a sync would. */
class FakeLedger(initial: TownSnapshot?) : BrickLedger {
    private val state = MutableStateFlow(initial)
    override val town: StateFlow<TownSnapshot?> = state
    val skips = mutableListOf<BillSkip>()

    override suspend fun putExpense(expense: Expense) {
        state.value = state.value?.let { t -> t.copy(expenses = t.expenses.filter { it.id != expense.id } + expense) }
    }

    override suspend fun deleteExpense(id: String) {
        state.value = state.value?.let { t -> t.copy(expenses = t.expenses.filter { it.id != id }) }
    }

    override suspend fun putCategory(category: Category) {
        state.value = state.value?.let { t -> t.copy(categories = t.categories.filter { it.id != category.id } + category) }
    }

    override suspend fun skipBill(skip: BillSkip) {
        skips += skip
        state.value = state.value?.let { t -> t.copy(billSkips = t.billSkips + skip) }
    }

    val expenses: List<Expense> get() = state.value?.expenses.orEmpty()
}

val NOV_24_CLOCK = TownClock { NOV_24 }

val NOV_28_CLOCK = TownClock { d(2026, 11, 28) }
