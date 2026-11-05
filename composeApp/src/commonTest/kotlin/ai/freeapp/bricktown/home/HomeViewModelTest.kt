package ai.freeapp.bricktown.home

import ai.freeapp.bricktown.ViewModelTest
import ai.freeapp.bricktown.month.FakeLedger
import ai.freeapp.bricktown.month.NOV_24_CLOCK
import ai.freeapp.bricktown.month.NOV_28_CLOCK
import ai.freeapp.bricktown.month.d
import ai.freeapp.bricktown.month.expense
import ai.freeapp.bricktown.month.fixture5b
import ai.freeapp.bricktown.month.fixture6c
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeViewModelTest : ViewModelTest() {
    @Test
    fun theAddButtonPulsesOnPaydayBeforeTheFirstBrick() = runTest(dispatcher) {
        val vm = HomeViewModel(FakeLedger(fixture6c()), NOV_28_CLOCK, dispatcher); advanceUntilIdle()
        assertTrue(vm.state.value.pulseAdd)
    }

    @Test
    fun theAddButtonStaysStillOnceTheMonthHasBricks() = runTest(dispatcher) {
        val vm = HomeViewModel(FakeLedger(fixture5b()), NOV_24_CLOCK, dispatcher); advanceUntilIdle()
        assertFalse(vm.state.value.pulseAdd)
    }

    @Test
    fun theFirstBrickOfTheMonthStopsThePulse() = runTest(dispatcher) {
        val ledger = FakeLedger(fixture6c())
        val vm = HomeViewModel(ledger, NOV_28_CLOCK, dispatcher); advanceUntilIdle()
        ledger.putExpense(expense("n1", "groceries", 1_200, d(2026, 11, 28), "Lidl")); advanceUntilIdle()
        assertFalse(vm.state.value.pulseAdd)
    }

    @Test
    fun noTownYetMeansNoPulse() = runTest(dispatcher) {
        val vm = HomeViewModel(FakeLedger(null), NOV_28_CLOCK, dispatcher); advanceUntilIdle()
        assertFalse(vm.state.value.pulseAdd)
    }
}
