package ai.freeapp.bricktown.settings

import ai.freeapp.bricktown.core.ui.rememberNotificationPermissionRequest
import ai.freeapp.bricktown.core.ui.rememberSharer
import ai.freeapp.bricktown.profile.AccountSection
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.datetime.LocalDate
import org.koin.compose.viewmodel.koinViewModel

/**
 * Settings, opened from Month's Penny disc (6a): money, towers, members, Penny's
 * nudges, data, then the ACCOUNT group and the "Made with freeapp.ai" footer.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onChangePassword: () -> Unit,
    onManageTowers: () -> Unit,
    onInvite: () -> Unit,
    onSettleUp: () -> Unit,
    onImport: () -> Unit,
    onOpenRollover: (monthStart: LocalDate) -> Unit,
) {
    val viewModel: SettingsViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sharer = rememberSharer()
    val askNotifications = rememberNotificationPermissionRequest(viewModel::onNotificationPermissionResult)

    LifecycleResumeEffect(viewModel) {
        viewModel.onResume()
        onPauseOrDispose { }
    }

    val openRollover by rememberUpdatedState(onOpenRollover)
    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                SettingsEffect.AskNotificationPermission -> askNotifications()
                is SettingsEffect.OpenRollover -> openRollover(effect.monthStart)
                is SettingsEffect.ShareExport -> sharer.shareFile(effect.fileName, effect.csv.encodeToByteArray(), "text/csv")
            }
        }
    }

    val actions = remember(viewModel, onBack, onManageTowers, onInvite, onSettleUp, onImport) {
        SettingsActions(
            onBack = onBack,
            onBudget = viewModel::onBudgetClick,
            onBudgetKey = viewModel::onBudgetKey,
            onBudgetSave = viewModel::onBudgetSave,
            onPayday = viewModel::onPaydayClick,
            onPaydayPick = viewModel::onPaydayPick,
            onCurrency = viewModel::onCurrencyClick,
            onCurrencyPick = viewModel::onCurrencyPick,
            onBillsFirst = viewModel::onBillsFirstChange,
            onManageTowers = onManageTowers,
            onMemberCanEdit = viewModel::onMemberCanEditChange,
            onInvite = onInvite,
            onNudge = viewModel::onNudgeChange,
            onRecap = viewModel::onRecapChange,
            onCelebrations = viewModel::onCelebrationsChange,
            onImport = onImport,
            onExport = viewModel::onExportClick,
            onSettleUp = onSettleUp,
            onDeleteTown = viewModel::onDeleteTownClick,
            onLeaveTown = viewModel::onLeaveTownClick,
            onConfirmDelete = viewModel::onConfirmDeleteTown,
            onConfirmLeave = viewModel::onConfirmLeaveTown,
            onDismissSheet = viewModel::onDismissSheet,
            onMessageShown = viewModel::onMessageShown,
            onTestTool = viewModel::onTestToolClick,
        )
    }
    SettingsContent(state, actions, account = { AccountSection(onChangePassword = onChangePassword, modifier = Modifier) })
}
