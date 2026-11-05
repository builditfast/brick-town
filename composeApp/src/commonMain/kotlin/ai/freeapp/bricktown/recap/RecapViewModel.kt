package ai.freeapp.bricktown.recap

import ai.freeapp.bricktown.brick.BrickLedger
import ai.freeapp.bricktown.core.ui.BaseViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.LocalDate

sealed interface RecapEffect

/** The recap of the ISO week starting [weekStart], a pure projection of the synced town. */
class RecapViewModel(
    weekStart: LocalDate,
    ledger: BrickLedger,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : BaseViewModel<RecapUiState, RecapEffect>(RecapUiState(), dispatcher) {

    init {
        tryToCollect(
            flow = { ledger.town },
            onEach = { town ->
                if (town == null) {
                    updateState { it.copy(ready = true) }
                } else {
                    val next = recapUiState(town, weekStart)
                    updateState { next }
                }
            },
            onError = { updateState { it.copy(ready = true) } },
        )
    }
}
