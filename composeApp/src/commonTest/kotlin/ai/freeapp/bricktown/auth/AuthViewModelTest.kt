package ai.freeapp.bricktown.auth

import ai.freeapp.bricktown.FakeAuthRepository
import ai.freeapp.bricktown.FakeTownLoader
import ai.freeapp.bricktown.MemoryJoinCodeStore
import ai.freeapp.bricktown.ViewModelTest
import ai.freeapp.bricktown.apiError
import ai.freeapp.bricktown.core.error.ErrorState
import ai.freeapp.bricktown.rollover.RolloverGate
import ai.freeapp.bricktown.town.SyncStatus
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SignInViewModelTest : ViewModelTest() {

    private val auth = FakeAuthRepository()
    private val viewModel = SignInViewModel(auth, dispatcher)

    private fun fillIn(email: String = "ana@example.com", password: String = "password1") {
        viewModel.onEmailChange(email)
        viewModel.onPasswordChange(password)
    }

    @Test
    fun signingInStartsTheSession() = runTest(dispatcher) {
        fillIn()

        viewModel.onSubmit()

        advanceUntilIdle()

        assertEquals(listOf("signIn ana@example.com"), auth.calls)
        assertTrue(auth.isSignedIn.value)
        with(viewModel.state.value) {
            assertFalse(isSubmitting)
            assertNull(error)
            assertEquals("", password)
        }
    }

    @Test
    fun aMalformedEmailIsRefusedWithoutCallingTheServer() = runTest(dispatcher) {
        fillIn(email = "ana")

        viewModel.onSubmit()

        advanceUntilIdle()

        assertEquals(emptyList(), auth.calls)
        assertEquals(ErrorState.Validation("Enter the email address you signed up with."), viewModel.state.value.error)
    }

    @Test
    fun aRejected401ReadsAsWrongCredentialsNotAnEndedSession() = runTest(dispatcher) {
        auth.failure = apiError(HttpStatusCode.Unauthorized, "That email and password don't match.")
        fillIn()

        viewModel.onSubmit()

        advanceUntilIdle()

        assertEquals(ErrorState.Validation(SignInViewModel.WRONG_CREDENTIALS), viewModel.state.value.error)
        assertFalse(viewModel.state.value.isSubmitting)
        assertFalse(auth.isSignedIn.value)
    }

    @Test
    fun aServerErrorIsShownAndTheFormStaysUsable() = runTest(dispatcher) {
        auth.failure = apiError(HttpStatusCode.InternalServerError)
        fillIn()

        viewModel.onSubmit()

        advanceUntilIdle()

        assertEquals(ErrorState.ServerError(500), viewModel.state.value.error)
        assertFalse(viewModel.state.value.isSubmitting)
        assertEquals("password1", viewModel.state.value.password)
    }

    @Test
    fun aSecondTapWhileSubmittingSendsNothing() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        auth.gate = gate
        fillIn()

        viewModel.onSubmit()

        advanceUntilIdle()
        assertTrue(viewModel.state.value.isSubmitting)
        viewModel.onSubmit()
        advanceUntilIdle()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, auth.calls.size)
        assertFalse(viewModel.state.value.isSubmitting)
    }
}

class CreateAccountViewModelTest : ViewModelTest() {

    private val auth = FakeAuthRepository()
    private val viewModel = CreateAccountViewModel(auth, dispatcher)

    private fun fillIn(name: String = "Ana", email: String = "ana@example.com", password: String = "password1") {
        viewModel.onNameChange(name)
        viewModel.onEmailChange(email)
        viewModel.onPasswordChange(password)
    }

    @Test
    fun creatingAnAccountStartsTheSession() = runTest(dispatcher) {
        fillIn()

        viewModel.onSubmit()

        advanceUntilIdle()

        assertEquals(listOf("createAccount Ana ana@example.com"), auth.calls)
        assertTrue(auth.isSignedIn.value)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun aShortPasswordIsRefusedWithoutCallingTheServer() = runTest(dispatcher) {
        fillIn(password = "short")

        viewModel.onSubmit()

        advanceUntilIdle()

        assertEquals(emptyList(), auth.calls)
        assertEquals(ErrorState.Validation("Use at least 8 characters."), viewModel.state.value.error)
    }

    @Test
    fun aMissingNameIsRefusedFirst() = runTest(dispatcher) {
        fillIn(name = "  ", email = "bad")

        viewModel.onSubmit()

        advanceUntilIdle()

        assertEquals(ErrorState.Validation("Enter your name."), viewModel.state.value.error)
    }

    @Test
    fun aTakenEmailShowsTheServersSentence() = runTest(dispatcher) {
        auth.failure = apiError(HttpStatusCode.Conflict, "An account with this email already exists.")
        fillIn()

        viewModel.onSubmit()

        advanceUntilIdle()

        assertEquals(ErrorState.Validation("An account with this email already exists."), viewModel.state.value.error)
        assertFalse(viewModel.state.value.isSubmitting)
        assertFalse(auth.isSignedIn.value)
    }

    @Test
    fun aServerErrorIsShown() = runTest(dispatcher) {
        auth.failure = apiError(HttpStatusCode.ServiceUnavailable)
        fillIn()

        viewModel.onSubmit()

        advanceUntilIdle()

        assertEquals(ErrorState.ServerError(503), viewModel.state.value.error)
    }

    @Test
    fun typingClearsTheError() = runTest(dispatcher) {
        fillIn(password = "short")
        viewModel.onSubmit()
        advanceUntilIdle()

        viewModel.onPasswordChange("short1234")

        assertNull(viewModel.state.value.error)
    }
}

class StartViewModelTest : ViewModelTest() {

    private val week = LocalDate(2026, 10, 28)
    private val auth = FakeAuthRepository().apply { isSignedIn.value = true }
    private val town = FakeTownLoader()
    private val joinCodes = MemoryJoinCodeStore()
    private var rolloverDue: LocalDate? = null

    private fun startViewModel() = StartViewModel(auth, town, joinCodes, RolloverGate { rolloverDue }, dispatcher)

    // advanceUntilIdle() stops once only background work is left, so the
    // collector runs unconfined: it takes each effect the moment it is sent.
    private fun TestScope.effectsOf(viewModel: StartViewModel): List<StartEffect> {
        val effects = mutableListOf<StartEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effect.toList(effects) }
        advanceUntilIdle()
        return effects
    }

    @Test
    fun anAcceptedSessionWithATownShowsMonth() = runTest(dispatcher) {
        val effects = effectsOf(startViewModel())

        assertEquals(listOf<StartEffect>(StartEffect.ShowMonth), effects)
        assertEquals("Ana", auth.currentUser.value?.name)
        assertEquals(1, town.refreshes)
    }

    @Test
    fun anAccountWithNoTownShowsOnboarding() = runTest(dispatcher) {
        town.answer = SyncStatus.NoTown

        val effects = effectsOf(startViewModel())

        assertEquals(listOf<StartEffect>(StartEffect.ShowOnboarding), effects)
    }

    @Test
    fun aTownItsOwnerDeletedShowsOnboarding() = runTest(dispatcher) {
        town.answer = SyncStatus.TownDeleted

        val effects = effectsOf(startViewModel())

        assertEquals(listOf<StartEffect>(StartEffect.ShowOnboarding), effects)
    }

    @Test
    fun aJoinCodeKeptFromBeforeSignInGoesFirstAndIsForgotten() = runTest(dispatcher) {
        joinCodes.write("8F2K-P3NY")

        val effects = effectsOf(startViewModel())

        assertEquals(listOf<StartEffect>(StartEffect.ShowJoin("8F2K-P3NY")), effects)
        assertNull(joinCodes.read())
    }

    @Test
    fun aJoinCodeGoesFirstEvenWithNoTown() = runTest(dispatcher) {
        town.answer = SyncStatus.NoTown
        joinCodes.write("8F2K-P3NY")

        val effects = effectsOf(startViewModel())

        assertEquals(listOf<StartEffect>(StartEffect.ShowJoin("8F2K-P3NY")), effects)
    }

    @Test
    fun anEndedPayMonthShowsItsTrophyFirst() = runTest(dispatcher) {
        rolloverDue = week

        val effects = effectsOf(startViewModel())

        assertEquals(listOf<StartEffect>(StartEffect.ShowRollover(week)), effects)
    }

    @Test
    fun noNetworkWithACachedTownKeepsTheSessionAndShowsMonth() = runTest(dispatcher) {
        auth.failure = IOException("offline")
        town.status.value = SyncStatus.Ready
        town.failure = IOException("offline")

        val viewModel = startViewModel()
        val effects = effectsOf(viewModel)

        assertEquals(listOf<StartEffect>(StartEffect.ShowMonth), effects)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun noNetworkAndNoCachedTownShowsTheErrorAndRetryRecovers() = runTest(dispatcher) {
        town.failure = IOException("offline")
        val viewModel = startViewModel()

        val before = effectsOf(viewModel)

        assertEquals(emptyList(), before)
        assertEquals(ErrorState.NoInternet, viewModel.state.value.error)

        town.failure = null
        viewModel.onRetry()
        advanceUntilIdle()

        assertNull(viewModel.state.value.error)
        assertEquals(2, town.refreshes)
    }

    @Test
    fun anEndedSessionLeavesTheMoveToLandingToTheApp() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        auth.failure = apiError(HttpStatusCode.Unauthorized, "Sign in again.")
        auth.gate = gate
        val viewModel = startViewModel()
        advanceUntilIdle()
        // What the refresh interceptor does when the server refuses the refresh token.
        auth.isSignedIn.value = false
        gate.complete(Unit)

        val effects = effectsOf(viewModel)

        assertEquals(emptyList(), effects)
        assertEquals(listOf("refreshUser"), auth.calls)
        assertEquals(0, town.refreshes)
    }

    @Test
    fun a401ThatLeftTheSessionStoredEndsItInsteadOfSpinning() = runTest(dispatcher) {
        auth.failure = apiError(HttpStatusCode.Unauthorized, "Sign in again.")

        val effects = effectsOf(startViewModel())

        assertEquals(emptyList(), effects)
        assertEquals(listOf("refreshUser", "signOut"), auth.calls)
        assertFalse(auth.isSignedIn.value)
    }
}
