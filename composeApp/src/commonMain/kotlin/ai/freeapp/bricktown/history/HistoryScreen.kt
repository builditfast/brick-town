package ai.freeapp.bricktown.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.datetime.LocalDate
import org.koin.compose.viewmodel.koinViewModel

/**
 * Every brick, newest first, with the weekly recap card and the Months trophy
 * list (spec 5.7, task 1028). The recap card opens that week's recap; a brick
 * opens for editing.
 */
@Composable
fun HistoryScreen(onOpenRecap: (weekStart: LocalDate) -> Unit, onEditBrick: (expenseId: String) -> Unit) {
    val viewModel = koinViewModel<HistoryViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val actions = remember(viewModel, onOpenRecap, onEditBrick) {
        HistoryActions(
            onQueryChange = viewModel::onQueryChange,
            onClearQuery = viewModel::onClearQuery,
            onOpenRecap = onOpenRecap,
            onOpenMonths = viewModel::onOpenMonths,
            onCloseMonths = viewModel::onCloseMonths,
            onEditBrick = onEditBrick,
        )
    }
    HistoryContent(state, actions)
}
