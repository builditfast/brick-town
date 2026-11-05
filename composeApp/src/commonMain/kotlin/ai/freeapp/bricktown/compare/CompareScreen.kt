package ai.freeapp.bricktown.compare

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

/** This month next to last month at the same day, tower by tower (spec 5.7, task 1028). */
@Composable
fun CompareScreen() {
    val viewModel = koinViewModel<CompareViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    CompareContent(state)
}
