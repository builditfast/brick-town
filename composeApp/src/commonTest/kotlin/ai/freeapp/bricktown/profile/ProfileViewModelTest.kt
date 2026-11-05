package ai.freeapp.bricktown.profile

import ai.freeapp.bricktown.FakeAuthRepository
import ai.freeapp.bricktown.ViewModelTest
import ai.freeapp.bricktown.ana
import ai.freeapp.bricktown.apiError
import ai.freeapp.bricktown.core.error.ErrorState
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProfileViewModelTest : ViewModelTest() {

    private val auth = FakeAuthRepository(user = ana)

    @Test
    fun showsTheSignedInAccount() = runTest(dispatcher) {
        val viewModel = ProfileViewModel(auth, dispatcher)
        advanceUntilIdle()

        assertEquals(ana, viewModel.state.value.user)
        assertEquals(emptyList(), auth.calls)
    }

    @Test
    fun loadsTheAccountWhenTheStartCheckCouldNot() = runTest(dispatcher) {
        val auth = FakeAuthRepository(user = null).apply { isSignedIn.value = true }

        val viewModel = ProfileViewModel(auth, dispatcher)

        advanceUntilIdle()

        assertEquals(listOf("refreshUser"), auth.calls)
        assertEquals(ana, viewModel.state.value.user)
        assertFalse(viewModel.state.value.isLoadingUser)
    }

    @Test
    fun signingOutEndsTheSession() = runTest(dispatcher) {
        val viewModel = ProfileViewModel(auth, dispatcher)
        advanceUntilIdle()

        viewModel.onSignOut()

        advanceUntilIdle()

        assertEquals(listOf("signOut"), auth.calls)
        assertFalse(auth.isSignedIn.value)
        assertNull(viewModel.state.value.user)
    }

    @Test
    fun deleteOnlyAsksUntilConfirmed() = runTest(dispatcher) {
        val viewModel = ProfileViewModel(auth, dispatcher)
        advanceUntilIdle()

        viewModel.onDeleteAccount()

        advanceUntilIdle()

        assertTrue(viewModel.state.value.showDeleteConfirm)
        assertEquals(emptyList(), auth.calls)
        assertTrue(auth.isSignedIn.value)
    }

    @Test
    fun cancellingTheConfirmDeletesNothing() = runTest(dispatcher) {
        val viewModel = ProfileViewModel(auth, dispatcher)
        advanceUntilIdle()
        viewModel.onDeleteAccount()
        advanceUntilIdle()

        viewModel.onDismissDelete()

        advanceUntilIdle()

        assertFalse(viewModel.state.value.showDeleteConfirm)
        assertEquals(emptyList(), auth.calls)
    }

    @Test
    fun confirmingDeletesTheAccountAndEndsTheSession() = runTest(dispatcher) {
        val viewModel = ProfileViewModel(auth, dispatcher)
        advanceUntilIdle()
        viewModel.onDeleteAccount()
        advanceUntilIdle()

        viewModel.onConfirmDelete()

        advanceUntilIdle()

        assertEquals(listOf("deleteAccount"), auth.calls)
        assertFalse(auth.isSignedIn.value)
        with(viewModel.state.value) {
            assertFalse(isDeleting)
            assertFalse(showDeleteConfirm)
            assertNull(error)
        }
    }

    @Test
    fun aFailedDeleteKeepsTheAccountAndSaysWhy() = runTest(dispatcher) {
        auth.failure = apiError(HttpStatusCode.InternalServerError)
        val viewModel = ProfileViewModel(auth, dispatcher)
        advanceUntilIdle()
        viewModel.onDeleteAccount()
        advanceUntilIdle()

        viewModel.onConfirmDelete()

        advanceUntilIdle()

        assertTrue(auth.isSignedIn.value)
        with(viewModel.state.value) {
            assertEquals(ErrorState.ServerError(500), error)
            assertFalse(isDeleting)
            assertFalse(showDeleteConfirm)
        }
    }

    @Test
    fun deleteCannotStartWhileASignOutIsInFlight() = runTest(dispatcher) {
        val viewModel = ProfileViewModel(auth, dispatcher)
        advanceUntilIdle()
        val gate = CompletableDeferred<Unit>()
        auth.gate = gate

        viewModel.onSignOut()
        advanceUntilIdle()
        viewModel.onDeleteAccount()
        viewModel.onConfirmDelete()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.showDeleteConfirm)
        assertEquals(listOf("signOut"), auth.calls)
        gate.complete(Unit)
        advanceUntilIdle()
    }
}


class ChangePasswordViewModelTest : ViewModelTest() {

    private val auth = FakeAuthRepository(user = ana)
    private val viewModel = ChangePasswordViewModel(auth, dispatcher)

    private fun fillIn(current: String = "password1", new: String = "password2") {
        viewModel.onCurrentPasswordChange(current)
        viewModel.onNewPasswordChange(new)
    }

    @Test
    fun changingThePasswordShowsTheConfirmation() = runTest(dispatcher) {
        fillIn()

        viewModel.onSubmit()

        advanceUntilIdle()

        assertEquals(listOf("changePassword"), auth.calls)
        with(viewModel.state.value) {
            assertTrue(isChanged)
            assertEquals("", currentPassword)
            assertEquals("", newPassword)
        }
    }

    @Test
    fun theSamePasswordIsRefusedWithoutCallingTheServer() = runTest(dispatcher) {
        fillIn(new = "password1")

        viewModel.onSubmit()

        advanceUntilIdle()

        assertEquals(emptyList(), auth.calls)
        assertEquals(
            ErrorState.Validation("Choose a password different from the current one."),
            viewModel.state.value.error,
        )
    }

    @Test
    fun aWrongCurrentPasswordStaysOnTheFormAndKeepsTheSession() = runTest(dispatcher) {
        auth.failure = apiError(HttpStatusCode.BadRequest, "Your current password is wrong.")
        fillIn()

        viewModel.onSubmit()

        advanceUntilIdle()

        assertEquals(ErrorState.Validation("Your current password is wrong."), viewModel.state.value.error)
        assertFalse(viewModel.state.value.isChanged)
        assertTrue(auth.isSignedIn.value)
    }
}
