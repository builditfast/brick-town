package ai.freeapp.bricktown.onboarding

import ai.freeapp.bricktown.FakeAuthRepository
import ai.freeapp.bricktown.ViewModelTest
import ai.freeapp.bricktown.ana
import ai.freeapp.bricktown.core.time.TownClock
import ai.freeapp.bricktown.designsystem.BACKSPACE
import ai.freeapp.bricktown.wire.CreateTownRequest
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OnboardingViewModelTest : ViewModelTest() {

    private class Recorder : TownCreator {
        var request: CreateTownRequest? = null
        var viewer: Pair<String, String>? = null
        var failure: Throwable? = null
        override suspend fun create(request: CreateTownRequest, viewerId: String, viewerName: String) {
            failure?.let { throw it }
            this.request = request
            viewer = viewerId to viewerName
        }
    }

    private class FixedEnv : OnboardingEnvironment {
        private var n = 0
        override fun newId() = "id-${++n}"
        override fun timeZoneId() = "Europe/Bucharest"
        override fun now(): Instant = Instant.parse("2026-11-04T10:00:00Z")
    }

    private val creator = Recorder()
    private var today = LocalDate(2026, 11, 4)
    private val auth = FakeAuthRepository(ana)
    private val viewModel = OnboardingViewModel(
        auth, creator, TownClock { today }, FixedEnv(), dispatcher,
    )

    private fun type(draft: String) = draft.forEach { viewModel.onKey(it) }

    @Test
    fun nextIsOffAtZeroAndOnceThereIsABudget() = runTest(dispatcher) {
        assertFalse(viewModel.state.value.canNext)
        viewModel.onNext()
        assertEquals(OnboardingStep.Budget, viewModel.state.value.step)
        type("0")
        assertFalse(viewModel.state.value.canNext)
        type("1800")
        assertTrue(viewModel.state.value.canNext)
        viewModel.onNext()
        assertEquals(OnboardingStep.Towers, viewModel.state.value.step)
    }

    @Test
    fun deleteBringsTheBudgetBackToZero() = runTest(dispatcher) {
        type("5")
        viewModel.onKey(BACKSPACE)
        assertFalse(viewModel.state.value.canNext)
        assertNull(viewModel.state.value.dailyLine)
    }

    @Test
    fun theDailyLineIsTheBudgetOverTheDaysOfThisMonth() = runTest(dispatcher) {
        type("1800")
        assertEquals("That's about €60 a day.", viewModel.state.value.dailyLine)
        type("0")
        assertEquals("That's about €600 a day.", viewModel.state.value.dailyLine)
        today = LocalDate(2026, 2, 10)
        viewModel.onKey(BACKSPACE)
        viewModel.onKey('0')
        assertEquals("That's about €643 a day.", viewModel.state.value.dailyLine)
    }

    @Test
    fun theDailyLineRoundsHalfUp() = runTest(dispatcher) {
        type("1815")
        assertEquals("That's about €61 a day.", viewModel.state.value.dailyLine)
    }

    @Test
    fun nextIsOffWithNoTower() = runTest(dispatcher) {
        type("1800")
        viewModel.onNext()
        assertFalse(viewModel.state.value.canNext)
        viewModel.onNext()
        assertEquals(OnboardingStep.Towers, viewModel.state.value.step)
        viewModel.onToggleTower(0)
        assertTrue(viewModel.state.value.canNext)
        viewModel.onToggleTower(0)
        assertFalse(viewModel.state.value.canNext)
    }

    @Test
    fun theRequestCarriesThePickedTowersInGridOrderWithShortNamesAndPaletteIndexes() = runTest(dispatcher) {
        type("1800")
        viewModel.onNext()
        listOf(8, 0, 5, 9).forEach(viewModel::onToggleTower)
        viewModel.onNext()
        viewModel.onNext()
        advanceUntilIdle()

        val request = creator.request!!
        assertEquals(listOf("Groceries", "Subscriptions", "Health", "Travel"), request.categories.map { it.name })
        assertEquals(listOf("Food", "Subs", "Care", "Trip"), request.categories.map { it.short })
        assertEquals(listOf(0, 5, 0, 1), request.categories.map { it.colorIndex })
        assertEquals(listOf(0, 1, 2, 3), request.categories.map { it.position })
        assertEquals(180_000L, request.settings.monthlyBudgetCents)
        assertEquals("Europe/Bucharest", request.settings.timeZone)
        assertEquals("Ana", creator.viewer?.second)
        assertEquals("u1", creator.viewer?.first)
        assertEquals(request.categories.map { it.id }.toSet().size, 4)
        assertNull(request.firstExpense)
        assertEquals(OnboardingStep.Welcome, viewModel.state.value.step)
        assertEquals("€1 800 a month · 4 towers", viewModel.state.value.summary)
    }

    @Test
    fun theFirstBrickLandsOnTheChosenTowerWithToday() = runTest(dispatcher) {
        type("1800")
        viewModel.onNext()
        viewModel.onToggleTower(0)
        viewModel.onToggleTower(1)
        viewModel.onNext()
        assertEquals(0, viewModel.state.value.firstTower)
        viewModel.onPickFirstTower(1)
        type("12")
        viewModel.onNext()
        advanceUntilIdle()

        val request = creator.request!!
        val first = request.firstExpense!!
        assertEquals(1200L, first.amountCents)
        assertEquals(request.categories[1].id, first.categoryId)
        assertEquals(today, first.date)
        assertEquals("u1", first.paidBy)
        assertEquals("€1 800 a month · 2 towers · first brick: €12 on Eating out", viewModel.state.value.summary)
    }

    @Test
    fun backGoesOneStepAndWritesNothing() = runTest(dispatcher) {
        type("1800")
        viewModel.onNext()
        viewModel.onToggleTower(0)
        viewModel.onNext()
        assertTrue(viewModel.state.value.canGoBack)
        viewModel.onBack()
        assertEquals(OnboardingStep.Towers, viewModel.state.value.step)
        viewModel.onBack()
        assertEquals(OnboardingStep.Budget, viewModel.state.value.step)
        assertTrue(viewModel.state.value.canGoBack)
        assertFalse(viewModel.state.value.systemBackStepsBack)
        advanceUntilIdle()
        assertNull(creator.request)
        assertEquals("1800", viewModel.state.value.budgetDraft)
    }

    @Test
    fun backOnStepOneSignsOutAndKeepsTheAccountOnTheServer() = runTest(dispatcher) {
        assertTrue(viewModel.state.value.canGoBack)
        viewModel.onBack()
        advanceUntilIdle()
        assertEquals(listOf("signOut"), auth.calls)
        assertFalse(auth.isSignedIn.value)
        assertEquals(OnboardingStep.Budget, viewModel.state.value.step)
        assertNull(creator.request)
    }

    @Test
    fun aFailedWriteStaysOnTheStepWithTheError() = runTest(dispatcher) {
        creator.failure = IllegalStateException("disk full")
        type("1800")
        viewModel.onNext()
        viewModel.onToggleTower(0)
        viewModel.onNext()
        viewModel.onNext()
        advanceUntilIdle()
        assertEquals(OnboardingStep.FirstBrick, viewModel.state.value.step)
        assertFalse(viewModel.state.value.isCreating)
        assertTrue(viewModel.state.value.error != null)
    }
}
