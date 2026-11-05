package ai.freeapp.bricktown.auth

import ai.freeapp.bricktown.FakeAuthRepository
import ai.freeapp.bricktown.FakeTownLoader
import ai.freeapp.bricktown.MemoryJoinCodeStore
import ai.freeapp.bricktown.ViewModelTest
import ai.freeapp.bricktown.rollover.RolloverGate
import ai.freeapp.bricktown.town.SyncStatus
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The path a signed-out invite takes: kept, left alone by sign-in and account creation, then opened by Start. */
class KeptJoinCodeTest : ViewModelTest() {

    private val auth = FakeAuthRepository()
    private val joinCodes = MemoryJoinCodeStore("8F2K-P3NY")

    private fun kotlinx.coroutines.test.TestScope.startEffects(): List<StartEffect> {
        val start = StartViewModel(auth, FakeTownLoader(answer = SyncStatus.NoTown), joinCodes, RolloverGate { null }, dispatcher)
        val effects = mutableListOf<StartEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { start.effect.toList(effects) }
        advanceUntilIdle()
        return effects
    }

    @Test
    fun signingInKeepsTheCodeAndStartOpensTheJoin() = runTest(dispatcher) {
        val signIn = SignInViewModel(auth, dispatcher)
        signIn.onEmailChange("ana@example.com")
        signIn.onPasswordChange("password1")
        signIn.onSubmit()
        advanceUntilIdle()

        assertEquals("8F2K-P3NY", joinCodes.read())
        assertEquals(listOf<StartEffect>(StartEffect.ShowJoin("8F2K-P3NY")), startEffects())
        assertEquals(null, joinCodes.read())
    }

    @Test
    fun creatingAnAccountKeepsTheCodeToo() = runTest(dispatcher) {
        val create = CreateAccountViewModel(auth, dispatcher)
        create.onNameChange("Ana")
        create.onEmailChange("ana@example.com")
        create.onPasswordChange("password1")
        create.onSubmit()
        advanceUntilIdle()

        assertEquals("8F2K-P3NY", joinCodes.read())
        assertEquals(listOf<StartEffect>(StartEffect.ShowJoin("8F2K-P3NY")), startEffects())
    }
}
