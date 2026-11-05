package ai.freeapp.bricktown.history

import ai.freeapp.bricktown.brick.BrickLedger
import ai.freeapp.bricktown.core.time.TownClock
import ai.freeapp.bricktown.core.ui.BaseViewModel
import ai.freeapp.bricktown.domain.TownSnapshot
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** History raises no one-shot effects: navigation goes through the screen's callbacks. */
sealed interface HistoryEffect

/** The state is a pure projection of the synced town and the search text; nothing here writes. */
class HistoryViewModel(
    private val ledger: BrickLedger,
    private val clock: TownClock,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : BaseViewModel<HistoryUiState, HistoryEffect>(HistoryUiState(), dispatcher) {

    private var snapshot: TownSnapshot? = null

    init {
        tryToCollect(
            flow = { ledger.town },
            onEach = { town ->
                snapshot = town
                project()
            },
            onError = { updateState { it.copy(ready = true) } },
        )
    }

    fun onQueryChange(query: String) {
        updateState { it.copy(query = query) }
        project()
    }

    fun onClearQuery() = onQueryChange("")

    fun onOpenMonths() = updateState { it.copy(monthsOpen = true) }

    fun onCloseMonths() = updateState { it.copy(monthsOpen = false) }

    private fun project() {
        val town = snapshot ?: return
        val today = clock.today(town.town.settings)
        val mapped = historyUiState(town, today, state.value.query)
        updateState { old -> mapped.copy(monthsOpen = old.monthsOpen) }
    }
}
