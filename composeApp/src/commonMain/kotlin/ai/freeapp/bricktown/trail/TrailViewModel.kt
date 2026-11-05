package ai.freeapp.bricktown.trail

import ai.freeapp.bricktown.brick.BrickLedger
import ai.freeapp.bricktown.core.time.TownClock
import ai.freeapp.bricktown.core.ui.BaseViewModel
import ai.freeapp.bricktown.domain.badgesOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

sealed interface TrailEffect

/** A read-only projection of the eight badges for the synced town. */
class TrailViewModel(
    ledger: BrickLedger,
    private val clock: TownClock,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : BaseViewModel<TrailUiState, TrailEffect>(TrailUiState(), dispatcher) {

    init {
        tryToCollect(
            flow = { ledger.town },
            onEach = { town ->
                if (town == null) {
                    updateState { it.copy(ready = true) }
                } else {
                    val next = trailUiState(badgesOf(town, clock.today(town.town.settings)))
                    updateState { next }
                }
            },
            onError = { updateState { it.copy(ready = true) } },
        )
    }
}
