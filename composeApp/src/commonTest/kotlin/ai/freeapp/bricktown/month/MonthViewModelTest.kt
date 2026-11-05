package ai.freeapp.bricktown.month

import ai.freeapp.bricktown.ViewModelTest
import ai.freeapp.bricktown.home.HomeFeedback
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MonthViewModelTest : ViewModelTest() {
    private val ledger = FakeLedger(fixture5b())
    private val feedback = HomeFeedback(ledger) { true }

    private fun vm(joined: String? = null) = MonthViewModel(joined, ledger, NOV_24_CLOCK, feedback, dispatcher)

    @Test
    fun monthShowsTheSpentCardAndPennysLine() = runTest(dispatcher) {
        val vm = vm(); advanceUntilIdle()
        val s = vm.state.value
        assertEquals("November · day 24", s.monthLine)
        assertFalse(s.fresh)
        assertEquals(6, s.towers.size)
        assertEquals("Ana added Wolt · €14", s.activity)
    }

    @Test
    fun theBounceInIsMarkedPlayedOnceAndSurvivesASync() = runTest(dispatcher) {
        val vm = vm(); advanceUntilIdle()
        assertFalse(vm.state.value.towersPlayed)
        vm.onTowersPlayed(); advanceUntilIdle()
        assertTrue(vm.state.value.towersPlayed)
        vm.onCapChosen("groceries", 40_000L); advanceUntilIdle()
        assertTrue(vm.state.value.towersPlayed)
    }

    @Test
    fun welcomeLineClearsAfterAboutFourSeconds() = runTest(dispatcher) {
        val vm = vm("Home"); advanceTimeBy(100)
        assertEquals("Welcome to Home.", vm.state.value.pennyLine)
        advanceTimeBy(4_500); advanceUntilIdle()
        assertTrue("Welcome" !in vm.state.value.pennyLine)
    }

    @Test
    fun swipeDeleteRemovesTheRowAndUndoRestoresIt() = runTest(dispatcher) {
        val vm = vm(); advanceUntilIdle()
        vm.onOpenTower(vm.state.value.towers.indexOfFirst { it.id == "eating" }); advanceUntilIdle()
        val before = vm.state.value.openTower!!.rows.size
        val id = vm.state.value.openTower!!.rows.first().id
        vm.onDeleteBrick(id); advanceUntilIdle()
        assertEquals(before - 1, vm.state.value.openTower!!.rows.size)
        assertEquals("Brick deleted", assertNotNull(feedback.state.value.toast).message)
        feedback.undo(); advanceUntilIdle()
        assertEquals(before, vm.state.value.openTower!!.rows.size)
        assertTrue(vm.state.value.openTower!!.rows.any { it.id == id })
    }

    @Test
    fun capChipWritesTheCategory() = runTest(dispatcher) {
        val vm = vm(); advanceUntilIdle()
        vm.onCapChosen("groceries", 40_000L); advanceUntilIdle()
        assertEquals(40_000L, vm.state.value.towers.first { it.id == "groceries" }.capCents)
        vm.onCapChosen("groceries", null); advanceUntilIdle()
        assertNull(vm.state.value.towers.first { it.id == "groceries" }.capCents)
    }

    @Test
    fun freshMonthShowsTheGhostExplainer() = runTest(dispatcher) {
        val fresh = FakeLedger(fixture6c())
        val vm = MonthViewModel(null, fresh, NOV_28_CLOCK, feedback, dispatcher); advanceUntilIdle()
        assertTrue(vm.state.value.fresh)
        assertEquals("Fresh town. €60 a day to play with.", vm.state.value.pennyLine)
    }

    @Test
    fun skippingAGhostRecordsTheSkipForThisMonth() = runTest(dispatcher) {
        val fresh = FakeLedger(fixture6c())
        val vm = MonthViewModel(null, fresh, NOV_28_CLOCK, feedback, dispatcher); advanceUntilIdle()
        val tower = vm.state.value.towers.first { it.ghosts.isNotEmpty() }
        vm.onOpenTower(vm.state.value.towers.indexOf(tower))
        vm.onOpenGhost(tower.ghosts.first().seriesKey); advanceUntilIdle()
        vm.onSkipGhost(); advanceUntilIdle()
        assertEquals(1, fresh.skips.size)
        assertNull(vm.state.value.openGhostKey)
    }
}
