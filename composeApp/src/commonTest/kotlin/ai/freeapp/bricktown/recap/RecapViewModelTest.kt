package ai.freeapp.bricktown.recap

import ai.freeapp.bricktown.ViewModelTest
import ai.freeapp.bricktown.month.FakeLedger
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

class RecapViewModelTest : ViewModelTest() {
    private val week47 = d(2026, 11, 16)

    private fun vm(vararg bricks: ai.freeapp.bricktown.domain.Expense) =
        RecapViewModel(week47, FakeLedger(snapshot(bricks.toList(), settings())), dispatcher)

    @Test
    fun headerNamesTheIsoWeekAndItsDateRange() = runTest(dispatcher) {
        val vm = vm(expense("a", "groceries", 5_000, d(2026, 11, 17)))
        advanceUntilIdle()
        assertEquals("Week 47 · 16–22 Nov", vm.state.value.header)
    }

    @Test
    fun shareTextCarriesWeekSpendAndGoldDays() = runTest(dispatcher) {
        val vm = vm(expense("a", "groceries", 31_200, d(2026, 11, 17)))
        advanceUntilIdle()
        assertTrue(vm.state.value.shareText.startsWith("Week 47 in Brick Town: "))
        assertTrue(vm.state.value.shareText.endsWith(" gold days.") || vm.state.value.shareText.endsWith(" gold day."))
    }

    @Test
    fun deltaIsGoodWhenThisWeekSpentLessThanLast() = runTest(dispatcher) {
        val vm = vm(
            expense("a", "groceries", 10_000, d(2026, 11, 17)),
            expense("b", "groceries", 30_000, d(2026, 11, 10)),
        )
        advanceUntilIdle()
        assertTrue(vm.state.value.deltaGood)
        assertTrue(vm.state.value.pennyHappy)
    }

    @Test
    fun deltaIsBadWhenThisWeekSpentMore() = runTest(dispatcher) {
        val vm = vm(
            expense("a", "groceries", 40_000, d(2026, 11, 17)),
            expense("b", "groceries", 10_000, d(2026, 11, 10)),
        )
        advanceUntilIdle()
        assertFalse(vm.state.value.deltaGood)
        assertEquals("vs week 46", vm.state.value.deltaLabel)
    }

    @Test
    fun sevenBarsAreDrawnAndTheTallestIsFull() = runTest(dispatcher) {
        val vm = vm(
            expense("a", "groceries", 10_000, d(2026, 11, 17)),
            expense("b", "groceries", 5_000, d(2026, 11, 19)),
        )
        advanceUntilIdle()
        val days = vm.state.value.days
        assertEquals(7, days.size)
        assertEquals(1f, days.maxOf { it.fraction })
    }
}
