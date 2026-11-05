package ai.freeapp.bricktown.towers

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

/** Your towers: rename, recolour, cap, reorder, merge or remove a tower (spec 5.8, task 1029). */
@Composable
fun ManageTowersScreen(onBack: () -> Unit, viewModel: TowersViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val actions = remember(viewModel, onBack) {
        TowersActions(
            onBack = onBack,
            onTowerClick = viewModel::onTowerClick,
            onNewTowerClick = viewModel::onNewTowerClick,
            onMergeClick = viewModel::onMergeClick,
            onDeleteClick = viewModel::onDeleteClick,
            onMove = viewModel::onMove,
            onMoveEnd = viewModel::onMoveEnd,
            onNameChange = viewModel::onNameChange,
            onColorPick = viewModel::onColorPick,
            onCapPick = viewModel::onCapPick,
            onCustomCapClick = viewModel::onCustomCapClick,
            onCustomCapChange = viewModel::onCustomCapChange,
            onEditorCancel = viewModel::onEditorCancel,
            onEditorSave = viewModel::onEditorSave,
            onMoveTargetPick = viewModel::onMoveTargetPick,
            onPromptDismiss = viewModel::onPromptDismiss,
            onMessageShown = viewModel::onMessageShown,
        )
    }
    TowersContent(state, actions)
}
