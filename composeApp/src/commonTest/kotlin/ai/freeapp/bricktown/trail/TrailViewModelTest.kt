package ai.freeapp.bricktown.trail

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
import kotlin.test.assertTrue

class TrailViewModelTest : ViewModelTest() {
    @Test
    fun eightNodesWithExactlyOneNextAndEverythingBeforeItEarned() = runTest(dispatcher) {
        val vm = TrailViewModel(FakeLedger(snapshot(listOf(expense("a", "groceries", 500, d(2026, 11, 3))))), NOV_24_CLOCK, dispatcher)
        advanceUntilIdle()
        val nodes = vm.state.value.nodes
        assertEquals(8, nodes.size)
        assertEquals(1, nodes.count { it.kind == TrailNodeKind.Next })
        val nextAt = nodes.indexOfFirst { it.kind == TrailNodeKind.Next }
        assertTrue(nodes.drop(nextAt + 1).none { it.kind == TrailNodeKind.Next })
        assertTrue(nodes.take(nextAt).all { it.kind == TrailNodeKind.Earned })
        val earned = nodes.count { it.kind == TrailNodeKind.Earned }
        assertTrue(vm.state.value.penny.startsWith("Your trail · $earned of 8"))
    }
}
