package ai.freeapp.bricktown.brick

import androidx.compose.runtime.Composable
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** One brick's amount, tower, note and date, with Delete (spec 5.6, task 1027). */
@Composable
fun EditBrickScreen(expenseId: String, onBack: () -> Unit) {
    val viewModel = koinViewModel<BrickFormViewModel> { parametersOf(BrickFormArgs(expenseId = expenseId)) }
    BrickFormHost(viewModel, onShowMonth = onBack, onClose = onBack)
}
