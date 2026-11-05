package ai.freeapp.bricktown.trail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

/** The Sky Trail: the eight badges as a zigzag path (spec 5.7, task 1028). */
@Composable
fun TrailScreen() {
    val viewModel = koinViewModel<TrailViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    TrailContent(state)
}
