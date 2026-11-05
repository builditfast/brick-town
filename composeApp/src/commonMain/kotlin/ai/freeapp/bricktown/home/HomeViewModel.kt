package ai.freeapp.bricktown.home

import ai.freeapp.bricktown.brick.BrickLedger
import ai.freeapp.bricktown.core.time.TownClock
import ai.freeapp.bricktown.core.ui.BaseViewModel
import ai.freeapp.bricktown.domain.summarize
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** What the tab frame shows besides the tab: the + pulses while the month has no brick yet (6c). */
@Immutable
data class HomeUiState(val pulseAdd: Boolean = false)

/** Nothing leaves the tab frame as an effect. */
sealed interface HomeEffect

/** Lives as long as the tab frame, which `App` keeps outside the per-visit entry. */
class HomeViewModel(
    private val ledger: BrickLedger,
    private val clock: TownClock,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : BaseViewModel<HomeUiState, HomeEffect>(HomeUiState(), dispatcher) {

    init {
        tryToCollect(
            flow = { ledger.town },
            onEach = { town ->
                val fresh = town != null && summarize(town, clock.today(town.town.settings)).fresh
                updateState { it.copy(pulseAdd = fresh) }
            },
            // Month reports a town that fails to load; the + stops pulsing.
            onError = { updateState { it.copy(pulseAdd = false) } },
        )
    }
}
