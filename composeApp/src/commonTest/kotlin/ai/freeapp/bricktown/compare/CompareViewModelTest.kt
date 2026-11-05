package ai.freeapp.bricktown.compare

import ai.freeapp.bricktown.ViewModelTest
import ai.freeapp.bricktown.month.FakeLedger
import ai.freeapp.bricktown.month.NOV_24_CLOCK
import ai.freeapp.bricktown.month.d
import ai.freeapp.bricktown.month.expense
import ai.freeapp.bricktown.month.settings
import ai.freeapp.bricktown.month.snapshot
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompareViewModelTest : ViewModelTest() {
    private fun vm(vararg bricks: ai.freeapp.bricktown.domain.Expense, budget: Long = 100_000L) =
        CompareViewModel(FakeLedger(snapshot(bricks.toList(), settings(budget = budget))), NOV_24_CLOCK, dispatcher)

    @Test
    fun ringFractionsCapAtOneWhenSpendPassesTheBudget() = runTest(dispatcher) {
        val vm = vm(
            expense("a", "groceries", 150_000, d(2026, 11, 10), note = "Big"),
            expense("b", "groceries", 300_000, d(2026, 10, 3), note = "Old"),
        )
        advanceUntilIdle()
        assertEquals(1f, vm.state.value.outer)
        assertEquals(1f, vm.state.value.inner)
    }

    @Test
    fun ringFractionsAreSpendOverBudget() = runTest(dispatcher) {
        val vm = vm(
            expense("a", "groceries", 25_000, d(2026, 11, 10)),
            expense("b", "groceries", 50_000, d(2026, 10, 3)),
        )
        advanceUntilIdle()
        assertEquals(0.25f, vm.state.value.outer)
        assertEquals(0.5f, vm.state.value.inner)
        assertTrue(vm.state.value.ahead == true)
    }

    @Test
    fun largestTowerFillsItsBarAndOthersAreRelative() = runTest(dispatcher) {
        val vm = vm(
            expense("a", "groceries", 40_000, d(2026, 11, 10)),
            expense("b", "eating", 10_000, d(2026, 11, 11)),
        )
        advanceUntilIdle()
        val rows = vm.state.value.rows
        assertEquals("groceries", rows.first().id)
        assertEquals(1f, rows.first().barFraction)
        assertEquals(0.25f, rows.first { it.id == "eating" }.barFraction)
        assertTrue(rows.first().deltaText.endsWith("vs Oct"))
        assertTrue(rows.first().deltaUp)
    }

    @Test
    fun anEmptyTownDrawsZeroRingsAndNoRows() = runTest(dispatcher) {
        val vm = vm()
        advanceUntilIdle()
        assertEquals(0f, vm.state.value.outer)
        assertTrue(vm.state.value.rows.isEmpty())
        assertFalse(vm.state.value.title.isEmpty())
    }
}
