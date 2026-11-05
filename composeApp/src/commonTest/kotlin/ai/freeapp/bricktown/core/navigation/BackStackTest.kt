package ai.freeapp.bricktown.core.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

class BackStackTest {

    private enum class Screen { Home, Detail, SignIn }

    private class TrackedViewModel : ViewModel() {
        var cleared = false
            private set

        override fun onCleared() {
            cleared = true
        }
    }

    private val removedKeys = mutableListOf<Int>()
    private val backStack = BackStack(root = Screen.Home, onRemoved = { removedKeys += it.key })

    // The same lookup koinViewModel() makes: through the entry as the owner.
    private fun BackStackEntry<Screen>.viewModel(): TrackedViewModel =
        ViewModelProvider.create(this, viewModelFactory { initializer { TrackedViewModel() } })[TrackedViewModel::class]

    @Test
    fun popClearsThePoppedScreensViewModel() {
        backStack.push(Screen.Detail)
        val detail = backStack.current
        val viewModel = detail.viewModel()

        backStack.pop()

        assertTrue(viewModel.cleared)
        assertEquals(Screen.Home, backStack.current.destination)
        assertEquals(listOf(detail.key), removedKeys)
    }

    @Test
    fun aSecondVisitGetsANewViewModel() {
        backStack.push(Screen.Detail)
        val first = backStack.current.viewModel()
        assertTrue(first === backStack.current.viewModel(), "one visit keeps one instance across recompositions")
        backStack.pop()

        backStack.push(Screen.Detail)
        val second = backStack.current.viewModel()

        assertNotSame(first, second)
        assertFalse(second.cleared)
    }

    @Test
    fun theScreenBelowKeepsItsViewModelWhileAnotherIsOnTop() {
        val home = backStack.current.viewModel()

        backStack.push(Screen.Detail)
        backStack.pop()

        assertFalse(home.cleared)
        assertTrue(home === backStack.current.viewModel())
    }

    @Test
    fun theRootIsNeverPopped() {
        val home = backStack.current.viewModel()

        backStack.pop()

        assertFalse(backStack.canGoBack)
        assertEquals(listOf(Screen.Home), backStack.destinations)
        assertFalse(home.cleared)
    }

    @Test
    fun resetToClearsTheWholeStack() {
        val home = backStack.current.viewModel()
        backStack.push(Screen.Detail)
        val detail = backStack.current.viewModel()

        // What signing out does.
        backStack.resetTo(Screen.SignIn)

        assertTrue(home.cleared)
        assertTrue(detail.cleared)
        assertEquals(listOf(Screen.SignIn), backStack.destinations)
        assertFalse(backStack.canGoBack)
        assertEquals(2, removedKeys.size)
    }

    @Test
    fun clearAllClearsEveryEntryButKeepsTheStack() {
        val home = backStack.current.viewModel()
        backStack.push(Screen.Detail)
        val detail = backStack.current.viewModel()

        backStack.clearAll()

        assertTrue(home.cleared)
        assertTrue(detail.cleared)
        assertEquals(listOf(Screen.Home, Screen.Detail), backStack.destinations)
    }

    @Test
    fun returnToTheRootKeepsItAndClearsWhatIsAbove() {
        val home = backStack.current.viewModel()
        backStack.push(Screen.Detail)
        val detail = backStack.current.viewModel()

        // What a tap on the Month tab does from another tab.
        backStack.returnTo(Screen.Home)

        assertFalse(home.cleared, "the root keeps its ViewModel")
        assertTrue(detail.cleared)
        assertEquals(listOf(Screen.Home), backStack.destinations)
    }

    @Test
    fun returnToAnotherScreenResetsTheStack() {
        val home = backStack.current.viewModel()
        backStack.push(Screen.Detail)

        backStack.returnTo(Screen.SignIn)

        assertTrue(home.cleared)
        assertEquals(listOf(Screen.SignIn), backStack.destinations)
    }

    @Test
    fun showAboveLeavesOnlyTheBaseUnderTheScreen() {
        val home = backStack.current.viewModel()
        backStack.push(Screen.Detail)
        backStack.push(Screen.SignIn)

        // What a tap on the History tab does: Back from it lands on Month.
        backStack.showAbove(base = Screen.Home, destination = Screen.Detail)

        assertFalse(home.cleared)
        assertEquals(listOf(Screen.Home, Screen.Detail), backStack.destinations)
        assertTrue(backStack.canGoBack)
        backStack.pop()
        assertEquals(listOf(Screen.Home), backStack.destinations)
    }

    @Test
    fun showAboveFromAnotherRootStartsAtTheBase() {
        val stack = BackStack(root = Screen.SignIn, onRemoved = {})

        stack.showAbove(base = Screen.Home, destination = Screen.Detail)

        assertEquals(listOf(Screen.Home, Screen.Detail), stack.destinations)
    }
}
