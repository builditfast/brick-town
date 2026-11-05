package ai.freeapp.bricktown.join

import ai.freeapp.bricktown.FakeAuthRepository
import ai.freeapp.bricktown.ViewModelTest
import ai.freeapp.bricktown.ana
import ai.freeapp.bricktown.domain.Category
import ai.freeapp.bricktown.town.TownResult
import ai.freeapp.bricktown.wire.InvitePreview
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JoinViewModelTest : ViewModelTest() {

    private val preview = InvitePreview(
        code = "8F2K-P3NY",
        townName = "Emanuel's town",
        ownerName = "Emanuel",
        categories = listOf(Category("c1", "Groceries", "Food", 0, position = 0), Category("c2", "Home", "Home", 2, position = 1)),
        takenColors = listOf(0),
        expiresAt = Instant.parse("2026-11-11T10:00:00Z"),
    )

    private class Gateway(var previewAnswer: TownResult<InvitePreview>, var acceptAnswer: TownResult<Unit> = TownResult.Ok(Unit)) : InviteGateway {
        val previews = mutableListOf<String>()
        val accepts = mutableListOf<Triple<String, Int, String>>()
        override suspend fun preview(code: String): TownResult<InvitePreview> {
            previews += code
            return previewAnswer
        }

        override suspend fun accept(code: String, colorIndex: Int, initial: String): TownResult<Unit> {
            accepts += Triple(code, colorIndex, initial)
            return acceptAnswer
        }
    }

    private val gateway = Gateway(TownResult.Ok(preview))
    private val auth = FakeAuthRepository(ana)

    private fun viewModel(code: String) = JoinViewModel(code, gateway, auth, dispatcher)

    @Test
    fun aLinkCodeOpensThePreviewAtOnce() = runTest(dispatcher) {
        val vm = viewModel("8F2K-P3NY")
        advanceUntilIdle()

        assertEquals(listOf("8F2K-P3NY"), gateway.previews)
        assertEquals(JoinStep.Preview, vm.state.value.step)
        assertEquals("Emanuel's town", vm.state.value.preview?.townName)
    }

    @Test
    fun aTypedCodeIsNormalisedBeforeTheLookUp() = runTest(dispatcher) {
        val vm = viewModel("")
        assertEquals(JoinStep.EnterCode, vm.state.value.step)
        vm.onCodeChange("8f2k p3ny")
        assertTrue(vm.state.value.canLookUp)
        vm.onLookUp()
        advanceUntilIdle()

        assertEquals(listOf("8F2K-P3NY"), gateway.previews)
    }

    @Test
    fun aCodeThatCannotBeACodeNeverReachesTheServer() = runTest(dispatcher) {
        val vm = viewModel("")
        vm.onCodeChange("8F2K-PENNY")
        assertFalse(vm.state.value.canLookUp)
        vm.onLookUp()
        advanceUntilIdle()

        assertTrue(gateway.previews.isEmpty())
        assertEquals(JoinViewModel.BAD_CODE, vm.state.value.error)
    }

    @Test
    fun anExpiredInviteShowsTheSpecCopy() = runTest(dispatcher) {
        gateway.previewAnswer = TownResult.Failed("That invite has expired or was revoked.", 404)
        val vm = viewModel("8F2K-P3NY")
        advanceUntilIdle()

        assertEquals("That invite has expired. Ask for a new link.", vm.state.value.error)
        assertEquals(JoinStep.EnterCode, vm.state.value.step)
    }

    @Test
    fun noConnectionShowsTheRepositorySentence() = runTest(dispatcher) {
        gateway.previewAnswer = TownResult.Failed("Can't reach the server. Check your connection and try again.")
        val vm = viewModel("8F2K-P3NY")
        advanceUntilIdle()

        assertEquals("Can't reach the server. Check your connection and try again.", vm.state.value.error)
    }

    @Test
    fun theColourDefaultsToTheFirstFreeOneAndTheInitialToTheFirstLetter() = runTest(dispatcher) {
        val vm = viewModel("8F2K-P3NY")
        advanceUntilIdle()
        vm.onContinue()

        assertEquals(JoinStep.Pick, vm.state.value.step)
        assertEquals(1, vm.state.value.colorIndex)
        assertEquals("A", vm.state.value.initial)
    }

    @Test
    fun aTakenColourCannotBePicked() = runTest(dispatcher) {
        val vm = viewModel("8F2K-P3NY")
        advanceUntilIdle()
        vm.onContinue()
        vm.onPickColor(0)
        assertEquals(1, vm.state.value.colorIndex)
        vm.onPickColor(4)
        assertEquals(4, vm.state.value.colorIndex)
    }

    @Test
    fun theInitialIsOneUpperCaseCharacter() = runTest(dispatcher) {
        val vm = viewModel("8F2K-P3NY")
        advanceUntilIdle()
        vm.onInitialChange("bo")
        assertEquals("B", vm.state.value.initial)
        vm.onInitialChange("")
        assertFalse(vm.state.value.canJoin)
    }

    @Test
    fun joiningAcceptsTheInviteAndOpensMonthWithTheTownName() = runTest(dispatcher) {
        val vm = viewModel("8F2K-P3NY")
        val effects = mutableListOf<JoinEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.effect.toList(effects) }
        advanceUntilIdle()
        vm.onContinue()
        vm.onPickColor(3)
        vm.onJoin()
        advanceUntilIdle()

        assertEquals(listOf(Triple("8F2K-P3NY", 3, "A")), gateway.accepts)
        assertEquals(listOf<JoinEffect>(JoinEffect.Joined("Emanuel's town")), effects)
    }

    @Test
    fun aFullTownShowsTheServerSentenceAndStaysOnTheStep() = runTest(dispatcher) {
        gateway.acceptAnswer = TownResult.Failed("This town already has two people.", 409)
        val vm = viewModel("8F2K-P3NY")
        advanceUntilIdle()
        vm.onContinue()
        vm.onJoin()
        advanceUntilIdle()

        assertEquals("This town already has two people.", vm.state.value.error)
        assertEquals(JoinStep.Pick, vm.state.value.step)
        assertFalse(vm.state.value.isJoining)
    }

    @Test
    fun backOnTheColourStepReturnsToThePreview() = runTest(dispatcher) {
        val vm = viewModel("8F2K-P3NY")
        advanceUntilIdle()
        vm.onContinue()
        assertTrue(vm.onStepBack())
        assertEquals(JoinStep.Preview, vm.state.value.step)
        assertFalse(vm.onStepBack())
        assertNull(vm.state.value.error)
    }
}
