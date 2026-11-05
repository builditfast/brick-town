package ai.freeapp.bricktown

import ai.freeapp.bricktown.core.navigation.BackStack
import ai.freeapp.bricktown.core.navigation.BackStackEntry
import ai.freeapp.bricktown.town.SyncStatus
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The rule App applies whenever the session, the town or the screen changes. */
class SessionRedirectTest {

    private val week = LocalDate(2026, 11, 16)
    private val townScreens = listOf(
        Destination.Month(), Destination.Month(joinedTown = "Ana's town"), Destination.History, Destination.Compare,
        Destination.Trail, Destination.AddBrick(), Destination.EditBrick("e1"), Destination.Recap(week),
        Destination.Rollover(LocalDate(2026, 10, 28)), Destination.Settings, Destination.ManageTowers,
        Destination.Invite, Destination.SettleUp, Destination.Import,
    )
    private val otherSignedInScreens = listOf(
        Destination.Start, Destination.Onboarding, Destination.Join(), Destination.Join("8F2K-P3NY"), Destination.ChangePassword,
    )
    private val signedOutScreens = listOf(Destination.Landing, Destination.SignIn, Destination.CreateAccount)

    @Test
    fun anEndedSessionSendsEverySignedInScreenToLanding() {
        (townScreens + otherSignedInScreens).forEach { screen ->
            SyncStatus.entries.forEach { town ->
                assertEquals(Destination.Landing, screen.redirectFor(signedIn = false, town = town), "$screen $town")
            }
        }
    }

    @Test
    fun aStartedSessionSendsTheSignedOutScreensToStart() {
        signedOutScreens.forEach { screen ->
            SyncStatus.entries.forEach { town ->
                assertEquals(Destination.Start, screen.redirectFor(signedIn = true, town = town), "$screen $town")
            }
        }
    }

    @Test
    fun aTownScreenWithNoTownGoesToOnboarding() {
        townScreens.forEach { screen ->
            assertEquals(Destination.Onboarding, screen.redirectFor(signedIn = true, town = SyncStatus.NoTown), "$screen")
            assertEquals(Destination.Onboarding, screen.redirectFor(signedIn = true, town = SyncStatus.TownDeleted), "$screen")
        }
    }

    @Test
    fun aTownScreenStaysWhileTheTownLoadsOrIsReady() {
        townScreens.forEach { screen ->
            assertNull(screen.redirectFor(signedIn = true, town = SyncStatus.Loading), "$screen")
            assertNull(screen.redirectFor(signedIn = true, town = SyncStatus.Ready), "$screen")
        }
    }

    @Test
    fun theScreensThatNeedNoTownStayWithoutOne() {
        otherSignedInScreens.forEach { screen ->
            SyncStatus.entries.forEach { town -> assertNull(screen.redirectFor(signedIn = true, town = town), "$screen $town") }
        }
        signedOutScreens.forEach { screen ->
            SyncStatus.entries.forEach { town -> assertNull(screen.redirectFor(signedIn = false, town = town), "$screen $town") }
        }
    }

    private class TrackedViewModel : ViewModel() {
        var cleared = false
            private set

        override fun onCleared() {
            cleared = true
        }
    }

    private fun BackStackEntry<Destination>.viewModel(): TrackedViewModel =
        ViewModelProvider.create(this, viewModelFactory { initializer { TrackedViewModel() } })[TrackedViewModel::class]

    @Test
    fun signingOutFromChangePasswordClearsTheWholeStack() {
        val backStack = BackStack<Destination>(root = Destination.Month(), onRemoved = {})
        val month = backStack.current.viewModel()
        backStack.push(Destination.Settings)
        val settings = backStack.current.viewModel()
        backStack.push(Destination.ChangePassword)
        val changePassword = backStack.current.viewModel()

        // What App does when isSignedIn turns false.
        backStack.current.destination.redirectFor(signedIn = false, town = SyncStatus.Ready)?.let(backStack::resetTo)

        assertTrue(month.cleared && settings.cleared && changePassword.cleared)
        assertEquals(listOf<Destination>(Destination.Landing), backStack.destinations)
        assertFalse(backStack.canGoBack)
    }

    @Test
    fun aTownDeletedByItsOwnerClearsTheStackToOnboarding() {
        val backStack = BackStack<Destination>(root = Destination.Month(), onRemoved = {})
        val month = backStack.current.viewModel()
        backStack.push(Destination.Settings)

        backStack.current.destination.redirectFor(signedIn = true, town = SyncStatus.TownDeleted)?.let(backStack::resetTo)

        assertTrue(month.cleared)
        assertEquals(listOf<Destination>(Destination.Onboarding), backStack.destinations)
    }
}
