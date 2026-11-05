package ai.freeapp.bricktown.recap

import ai.freeapp.bricktown.core.ui.rememberSharer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.datetime.LocalDate
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** The weekly recap of the ISO week starting [weekStart], a Monday (spec 5.7, task 1028). */
@Composable
fun RecapScreen(weekStart: LocalDate, onBack: () -> Unit) {
    val viewModel = koinViewModel<RecapViewModel>(key = "recap-$weekStart") { parametersOf(weekStart) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sharer = rememberSharer()
    RecapContent(state, onShareRecap = { sharer.shareText(state.shareText) }, onBack = onBack)
}
