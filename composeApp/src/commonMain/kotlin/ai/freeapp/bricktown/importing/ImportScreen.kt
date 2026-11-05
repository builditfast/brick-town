package ai.freeapp.bricktown.importing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

/** Import a statement: turn a bank statement into bricks (spec 5.8, task 1029). */
@Composable
fun ImportScreen(onBack: () -> Unit, viewModel: ImportViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val openPicker = rememberStatementPicker(viewModel::onFilePicked)
    val actions = remember(viewModel, onBack, openPicker) {
        ImportActions(
            onBack = onBack,
            onChooseClick = openPicker,
            onRowClick = viewModel::onRowClick,
            onTowerClick = viewModel::onTowerClick,
            onAddClick = viewModel::onAddClick,
            onUndoClick = viewModel::onUndoClick,
            onUndoExpired = viewModel::onUndoExpired,
            onTryAgainClick = viewModel::onTryAgainClick,
            onPhotoClick = viewModel::onPhotoClick,
            onMessageShown = viewModel::onMessageShown,
        )
    }
    ImportContent(state, actions)
}
