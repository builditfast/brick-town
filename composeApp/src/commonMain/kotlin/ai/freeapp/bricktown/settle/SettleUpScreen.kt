package ai.freeapp.bricktown.settle

import ai.freeapp.bricktown.core.ui.rememberSharer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

/** Settle up: who paid what this month, and who owes whom (spec 5.8, task 1029). */
@Composable
fun SettleUpScreen(onBack: () -> Unit, viewModel: SettleUpViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sharer = rememberSharer()
    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is SettleUpEffect.Ping -> sharer.shareText(effect.text)
            }
        }
    }
    val actions = remember(viewModel, onBack) {
        SettleUpActions(
            onBack = onBack,
            onSettleClick = viewModel::onSettleClick,
            onPingClick = viewModel::onPingClick,
            onMessageShown = viewModel::onMessageShown,
        )
    }
    SettleUpContent(state, actions)
}
