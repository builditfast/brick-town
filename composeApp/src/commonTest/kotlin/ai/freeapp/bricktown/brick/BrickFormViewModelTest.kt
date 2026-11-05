package ai.freeapp.bricktown.brick

import ai.freeapp.bricktown.ViewModelTest
import ai.freeapp.bricktown.home.CelebrationSetting
import ai.freeapp.bricktown.home.HomeFeedback
import ai.freeapp.bricktown.month.FakeLedger
import ai.freeapp.bricktown.month.NOV_24_CLOCK
import ai.freeapp.bricktown.month.fixture5b
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BrickFormViewModelTest : ViewModelTest() {
    private val ledger = FakeLedger(fixture5b())
    private val feedback = HomeFeedback(ledger) { true }
    private var ids = 0

    private fun vm(args: BrickFormArgs = BrickFormArgs(categoryId = "eating")) =
        BrickFormViewModel(args, ledger, NOV_24_CLOCK, feedback, { "new-${ids++}" }, dispatcher)

    private fun BrickFormViewModel.type(text: String) = text.forEach { onKey(it) }

    @Test
    fun submitIsOffAtZeroAndOnceAnAmountIsTyped() = runTest(dispatcher) {
        val vm = vm(); advanceUntilIdle()
        assertFalse(vm.state.value.canSubmit)
        vm.type("0"); assertFalse(vm.state.value.canSubmit)
        vm.onKey('\b'); vm.type("12")
        assertTrue(vm.state.value.canSubmit)
    }

    @Test
    fun numpadFollowsTheDomainRules() = runTest(dispatcher) {
        val vm = vm(); advanceUntilIdle()
        vm.type("1.2.5"); assertEquals("1.25", vm.state.value.draft)
        vm.type("9"); assertEquals("1.25", vm.state.value.draft)
        vm.onKey('\b'); assertEquals("1.2", vm.state.value.draft)
    }

    @Test
    fun addingShowsTheToastAndUndoRemovesTheBrick() = runTest(dispatcher) {
        val vm = vm(); advanceUntilIdle()
        vm.type("12")
        assertEquals("Add €12 to Eating out", vm.state.value.submitLabel)
        vm.onSubmit(); advanceUntilIdle()
        val toast = assertNotNull(feedback.state.value.toast)
        assertEquals("Added €12 to Eating out", toast.message)
        val added = ledger.expenses.single { it.id == "new-0" }
        assertEquals(1_200L, added.amountCents)
        feedback.undo(); advanceUntilIdle()
        assertTrue(ledger.expenses.none { it.id == "new-0" })
        assertNull(feedback.state.value.toast)
    }

    @Test
    fun refundExplainerCountsTheBricksItRemoves() = runTest(dispatcher) {
        val vm = vm(BrickFormArgs(expenseId = "4")); advanceUntilIdle()
        repeat(5) { vm.onKey('\b') }; vm.type("89")
        vm.onRefundChange(true)
        assertEquals(4, vm.state.value.refundBricks)
        assertEquals("−€89", vm.state.value.heroText)
        assertEquals("Save −€89", vm.state.value.submitLabel)
    }

    @Test
    fun unknownEditIdDoesNotThrow() = runTest(dispatcher) {
        val vm = vm(BrickFormArgs(expenseId = "nope")); advanceUntilIdle()
        assertTrue(vm.state.value.missing)
        assertFalse(vm.state.value.canSubmit)
    }
}
