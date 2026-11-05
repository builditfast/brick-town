package ai.freeapp.bricktown.month

import ai.freeapp.bricktown.home.HomeFeedback
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * The main tab: this month's towers, Penny's line and the activity pill (spec
 * 5.6, task 1027). The Penny disc opens Settings; a tower's sheet adds a brick
 * to that tower; a brick in a tower opens it for editing. [joinedTown] is set
 * once, right after joining, for the "Welcome to {town}." line.
 */
@Composable
fun MonthScreen(
    joinedTown: String?,
    onOpenSettings: () -> Unit,
    onAddBrick: (categoryId: String?) -> Unit,
    onEditBrick: (expenseId: String) -> Unit,
) {
    val viewModel = koinViewModel<MonthViewModel> { parametersOf(joinedTown) }
    val feedback = koinInject<HomeFeedback>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val feedbackState by feedback.state.collectAsStateWithLifecycle()
    LaunchedEffect(feedbackState.pennyJump) {
        if (feedbackState.pennyJump) {
            delay(JUMP_MILLIS)
            feedback.consumeJump()
        }
    }
    val actions = remember(viewModel, onOpenSettings, onAddBrick, onEditBrick) {
        MonthActions(
            onOpenSettings = onOpenSettings,
            onOpenTower = viewModel::onOpenTower,
            onCloseTower = viewModel::onCloseTower,
            onCapChosen = viewModel::onCapChosen,
            onEditBrick = onEditBrick,
            onDeleteBrick = viewModel::onDeleteBrick,
            onAddBrickHere = { id -> onAddBrick(id) },
            onOpenGhost = viewModel::onOpenGhost,
            onCloseGhost = viewModel::onCloseGhost,
            onConfirmGhost = viewModel::onConfirmGhost,
            onSkipGhost = viewModel::onSkipGhost,
            onTowersPlayed = viewModel::onTowersPlayed,
        )
    }
    MonthContent(state, actions, pennyJump = feedbackState.pennyJump)
}

/** Two jumps of 0.7 s each. */
private const val JUMP_MILLIS = 1_400L
