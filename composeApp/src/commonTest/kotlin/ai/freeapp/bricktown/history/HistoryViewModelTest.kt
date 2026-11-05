package ai.freeapp.bricktown.history

import ai.freeapp.bricktown.ViewModelTest
import ai.freeapp.bricktown.month.FakeLedger
import ai.freeapp.bricktown.month.NOV_24_CLOCK
import ai.freeapp.bricktown.month.d
import ai.freeapp.bricktown.month.expense
import ai.freeapp.bricktown.month.snapshot
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HistoryViewModelTest : ViewModelTest() {
    private val bricks = listOf(
        expense("a", "groceries", 4_200, d(2026, 11, 20), note = "Lidl"),
        expense("b", "eating", 1_400, d(2026, 11, 21), note = "Wolt"),
        expense("c", "transport", 900, d(2026, 11, 22), note = "Bolt"),
        expense("r", "shopping", 2_000, d(2026, 11, 23), note = "Returned shoes", refund = true),
    )
    private val ledger = FakeLedger(snapshot(bricks))

    private fun vm() = HistoryViewModel(ledger, NOV_24_CLOCK, dispatcher)

    @Test
    fun rowsAreNewestFirstAndRefundsAreLabelled() = runTest(dispatcher) {
        val vm = vm(); advanceUntilIdle()
        val rows = vm.state.value.rows
        assertEquals(listOf("r", "c", "b", "a"), rows.map { it.id })
        assertTrue(rows.first().refund)
        assertEquals(4, vm.state.value.count)
    }

    @Test
    fun theHeaderShowsNoCountUntilTheTownHasLoaded() = runTest(dispatcher) {
        val vm = vm()
        assertEquals("History", vm.state.value.title)
        advanceUntilIdle()
        assertEquals("History · 4", vm.state.value.title)
    }

    @Test
    fun aLoadedTownWithNoBricksSaysZero() = runTest(dispatcher) {
        val vm = HistoryViewModel(FakeLedger(snapshot(emptyList())), NOV_24_CLOCK, dispatcher)
        advanceUntilIdle()
        assertEquals("History · 0", vm.state.value.title)
    }

    @Test
    fun searchMatchesTheNoteIgnoringCase() = runTest(dispatcher) {
        val vm = vm(); advanceUntilIdle()
        vm.onQueryChange("lIdL"); advanceUntilIdle()
        assertEquals(listOf("a"), vm.state.value.rows.map { it.id })
    }

    @Test
    fun searchMatchesTheTowerNameIgnoringCase() = runTest(dispatcher) {
        val vm = vm(); advanceUntilIdle()
        vm.onQueryChange("TRANSPORT"); advanceUntilIdle()
        assertEquals(listOf("c"), vm.state.value.rows.map { it.id })
    }

    @Test
    fun clearingTheSearchBringsEveryBrickBack() = runTest(dispatcher) {
        val vm = vm(); advanceUntilIdle()
        vm.onQueryChange("wolt"); advanceUntilIdle()
        vm.onClearQuery(); advanceUntilIdle()
        assertEquals(4, vm.state.value.rows.size)
    }

    @Test
    fun recapCardIsHiddenWhenTheLastWeekHasNoBricks() = runTest(dispatcher) {
        val vm = vm(); advanceUntilIdle()
        // Nov 24 2026 is a Tuesday: the last completed week is 16-22 Nov, which has bricks.
        assertNotNull(vm.state.value.recap)
        val quiet = FakeLedger(snapshot(listOf(expense("z", "eating", 500, d(2026, 11, 3), note = "Old"))))
        val quietVm = HistoryViewModel(quiet, NOV_24_CLOCK, dispatcher); advanceUntilIdle()
        assertNull(quietVm.state.value.recap)
    }

    @Test
    fun reEmittingTheSameSnapshotKeepsTheStateAndTheOpenMonthsSheet() = runTest(dispatcher) {
        val vm = vm(); advanceUntilIdle()
        vm.onOpenMonths(); advanceUntilIdle()
        val before = vm.state.value
        ledger.putExpense(bricks[0]); advanceUntilIdle()
        assertEquals(before.recap, vm.state.value.recap)
        assertEquals(before.rows, vm.state.value.rows)
        assertTrue(vm.state.value.monthsOpen)
        vm.onCloseMonths(); advanceUntilIdle()
        assertTrue(!vm.state.value.monthsOpen)
    }
}
