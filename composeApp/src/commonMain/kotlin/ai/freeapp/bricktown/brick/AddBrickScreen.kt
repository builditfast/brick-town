package ai.freeapp.bricktown.brick

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Adding a brick: the numpad, then the tower ([categoryId] picked when it
 * opens from a tower's sheet), spec 5.6, task 1027. A saved brick goes back to
 * Month through [onShowMonth], which shows the toast.
 */
@Composable
fun AddBrickScreen(categoryId: String?, onShowMonth: () -> Unit, onBack: () -> Unit) {
    val viewModel = koinViewModel<BrickFormViewModel> { parametersOf(BrickFormArgs(categoryId = categoryId)) }
    BrickFormHost(viewModel, onShowMonth = onShowMonth, onClose = onBack)
}

/** Shared by Add and Edit: collects state, runs the effects, builds the callbacks. */
@Composable
internal fun BrickFormHost(viewModel: BrickFormViewModel, onShowMonth: () -> Unit, onClose: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                BrickFormEffect.ShowMonth -> onShowMonth()
                BrickFormEffect.Close -> onClose()
            }
        }
    }
    val actions = remember(viewModel, onClose) {
        BrickFormActions(
            onClose = onClose,
            onKey = viewModel::onKey,
            onSelectTower = viewModel::onSelectTower,
            onNoteChange = viewModel::onNoteChange,
            onMonthlyChange = viewModel::onMonthlyChange,
            onRefundChange = viewModel::onRefundChange,
            onSharedChange = viewModel::onSharedChange,
            onOpenNewTower = viewModel::onOpenNewTower,
            onCancelNewTower = viewModel::onCancelNewTower,
            onNewTowerNameChange = viewModel::onNewTowerNameChange,
            onCreateTower = viewModel::onCreateTower,
            onSubmit = viewModel::onSubmit,
            onDelete = viewModel::onDelete,
        )
    }
    BrickFormContent(state, actions)
}
