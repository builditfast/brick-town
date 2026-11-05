package ai.freeapp.bricktown.rollover

import ai.freeapp.bricktown.ViewModelTest
import ai.freeapp.bricktown.core.time.TownClock
import ai.freeapp.bricktown.month.FakeLedger
import ai.freeapp.bricktown.month.d
import ai.freeapp.bricktown.month.expense
import ai.freeapp.bricktown.month.settings
import ai.freeapp.bricktown.month.snapshot
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RolloverViewModelTest : ViewModelTest() {
    private fun vm(monthStart: LocalDate) = RolloverViewModel(
        monthStart,
        FakeLedger(
            snapshot(
                listOf(
                    expense("a", "groceries", 20_000, d(2026, 10, 5)),
                    expense("b", "eating", 5_000, d(2026, 10, 9)),
                ),
                settings(budget = 100_000L),
            ),
        ),
        TownClock { d(2026, 11, 2) },
        dispatcher,
    )

    @Test
    fun trophyNamesTheEndedMonthAndTheNextOne() = runTest(dispatcher) {
        val vm = vm(d(2026, 10, 1))
        advanceUntilIdle()
        val s = vm.state.value
        assertTrue(s.found)
        assertEquals("October is done!", s.title)
        assertEquals("Start November", s.startLabel)
        assertEquals("1 November", s.dateLine)
        assertEquals("vs September", s.vsLabel)
        assertTrue(s.underBudget)
        assertTrue(s.note.contains("pencilled into November"))
    }

    @Test
    fun anUnknownMonthFallsBackToTheBareWayOut() = runTest(dispatcher) {
        val vm = vm(d(2025, 3, 1))
        advanceUntilIdle()
        val s = vm.state.value
        assertTrue(s.ready)
        assertFalse(s.found)
        assertEquals("Start the new month", s.startLabel)
    }

    @Test
    fun barsAreRelativeToTheTallestTower() = runTest(dispatcher) {
        val vm = vm(d(2026, 10, 1))
        advanceUntilIdle()
        assertEquals(1f, vm.state.value.bars.maxOf { it.fraction })
        assertEquals(2, vm.state.value.bars.size)
    }
}
