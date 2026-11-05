package ai.freeapp.bricktown.rollover

import ai.freeapp.bricktown.brick.BrickLedger
import ai.freeapp.bricktown.core.time.TownClock
import ai.freeapp.bricktown.core.ui.BaseViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.LocalDate

sealed interface RolloverEffect

/** A read-only projection: the trophy of the pay month that started on [monthStart]. */
class RolloverViewModel(
    monthStart: LocalDate,
    ledger: BrickLedger,
    private val clock: TownClock,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : BaseViewModel<RolloverUiState, RolloverEffect>(RolloverUiState(), dispatcher) {

    init {
        tryToCollect(
            flow = { ledger.town },
            onEach = { town ->
                if (town == null) {
                    updateState { it.copy(ready = true) }
                } else {
                    val next = rolloverUiState(town, monthStart, clock.today(town.town.settings))
                    updateState { next }
                }
            },
            onError = { updateState { it.copy(ready = true) } },
        )
    }
}
