package ai.freeapp.bricktown.rollover

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.datetime.LocalDate
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * The trophy for the pay month that started on [monthStart], shown once when
 * it ends (spec 5.7, task 1028). "Start {month}" is [onStartMonth].
 */
@Composable
fun RolloverScreen(monthStart: LocalDate, onStartMonth: () -> Unit) {
    val viewModel = koinViewModel<RolloverViewModel>(key = "rollover-$monthStart") { parametersOf(monthStart) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    RolloverContent(state, onStartMonth)
}
