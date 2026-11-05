package ai.freeapp.bricktown.core.navigation

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

/**
 * One visit to a screen. It owns that screen's ViewModels: `App` provides the
 * entry as `LocalViewModelStoreOwner`, so `koinViewModel()` inside the screen
 * stores its ViewModel here and nowhere else.
 *
 * That is the whole point of the class. With one Activity-wide store, opening
 * a screen a second time hands back the first visit's ViewModel, still
 * "Saving…", and a second account sees the first one's state.
 */
class BackStackEntry<T : Any> internal constructor(
    val destination: T,
    /** Unique for the life of the stack; also keys the entry's saved UI state. */
    val key: Int,
) : ViewModelStoreOwner {
    override val viewModelStore: ViewModelStore = ViewModelStore()
}

/**
 * Navigation as a plain list of entries; the last one is on screen. Every
 * entry that leaves for good has its ViewModels cleared (`onCleared` runs, the
 * `viewModelScope` is cancelled), and [onRemoved] hears about it so its saved
 * UI state can go too.
 *
 * Plain Kotlin over snapshot state, so a commonTest drives it without a UI.
 * Call it from the main thread only, as composition and click handlers do.
 */
@Stable
class BackStack<T : Any>(
    root: T,
    private val onRemoved: (BackStackEntry<T>) -> Unit,
) {
    private var nextKey = 0
    private val entries = mutableStateListOf(newEntry(root))

    val current: BackStackEntry<T> get() = entries.last()
    val canGoBack: Boolean get() = entries.size > 1
    val destinations: List<T> get() = entries.map { it.destination }

    /** Opens [destination] on top. A second push of the same destination is a new visit with new ViewModels. */
    fun push(destination: T) {
        entries.add(newEntry(destination))
    }

    /** Leaves the top screen. The root is never popped: system back on it closes the app. */
    fun pop() {
        if (canGoBack) remove(entries.removeAt(entries.lastIndex))
    }

    /**
     * Replaces the whole stack with [destination], clearing every entry:
     * sign-in, sign-out, account deletion and an ended session all go through
     * here, so no screen of the previous session keeps a live ViewModel.
     */
    fun resetTo(destination: T) {
        val removed = entries.toList()
        entries.clear()
        entries.add(newEntry(destination))
        removed.forEach(::remove)
    }

    /**
     * Goes back to [destination] as the only entry. When the root already is
     * [destination] it is kept with its ViewModels and only the entries above
     * it are cleared (a tab switch back to Month); otherwise this is [resetTo].
     */
    fun returnTo(destination: T) {
        if (entries.first().destination != destination) return resetTo(destination)
        while (canGoBack) remove(entries.removeAt(entries.lastIndex))
    }

    /** Shows [destination] with only [base] under it, so Back lands on [base] (see [returnTo]). */
    fun showAbove(base: T, destination: T) {
        returnTo(base)
        push(destination)
    }

    /** Clears every entry's ViewModels; for when the host itself leaves composition. */
    fun clearAll() {
        entries.forEach { it.viewModelStore.clear() }
    }

    private fun newEntry(destination: T) = BackStackEntry(destination, nextKey++)

    private fun remove(entry: BackStackEntry<T>) {
        entry.viewModelStore.clear()
        onRemoved(entry)
    }
}
