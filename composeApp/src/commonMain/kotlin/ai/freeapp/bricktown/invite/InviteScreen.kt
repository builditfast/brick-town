package ai.freeapp.bricktown.invite

import ai.freeapp.bricktown.core.ui.rememberSharer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

/** Invite: share your town with a link or a QR code (spec 5.8, task 1029). */
@Composable
fun InviteScreen(onBack: () -> Unit, viewModel: InviteViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sharer = rememberSharer()
    val clipboard = LocalClipboardManager.current
    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is InviteEffect.Share -> sharer.shareText(effect.text)
                is InviteEffect.Copy -> clipboard.setText(AnnotatedString(effect.text))
            }
        }
    }
    val actions = remember(viewModel, onBack) {
        InviteActions(
            onBack = onBack,
            onShareClick = viewModel::onShareClick,
            onCopyClick = viewModel::onCopyClick,
            onQrToggle = viewModel::onQrToggle,
            onRetryClick = viewModel::onRetryClick,
            onCanEditChange = viewModel::onCanEditChange,
            onCanManageChange = viewModel::onCanManageChange,
            onRevokeClick = viewModel::onRevokeClick,
            onMessageShown = viewModel::onMessageShown,
        )
    }
    InviteContent(state, actions)
}
