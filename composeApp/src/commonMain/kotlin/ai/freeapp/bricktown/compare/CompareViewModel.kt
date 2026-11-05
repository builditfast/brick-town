package ai.freeapp.bricktown.compare

import ai.freeapp.bricktown.brick.BrickLedger
import ai.freeapp.bricktown.core.time.TownClock
import ai.freeapp.bricktown.core.ui.BaseViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

sealed interface CompareEffect

/** A read-only projection of the synced town: this pay month against last month's same day. */
class CompareViewModel(
    ledger: BrickLedger,
    private val clock: TownClock,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : BaseViewModel<CompareUiState, CompareEffect>(CompareUiState(), dispatcher) {

    init {
        tryToCollect(
            flow = { ledger.town },
            onEach = { town ->
                if (town == null) {
                    updateState { it.copy(ready = true) }
                } else {
                    val next = compareUiState(town, clock.today(town.town.settings))
                    updateState { next }
                }
            },
            onError = { updateState { it.copy(ready = true) } },
        )
    }
}
