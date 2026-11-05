package ai.freeapp.bricktown.profile

import ai.freeapp.bricktown.auth.AccountRules
import ai.freeapp.bricktown.core.ui.BackTopBar
import ai.freeapp.bricktown.core.ui.FormError
import ai.freeapp.bricktown.core.ui.FormTextField
import ai.freeapp.bricktown.core.ui.SubmitButton
import ai.freeapp.bricktown.designsystem.Dimens
import ai.freeapp.bricktown.designsystem.World
import ai.freeapp.bricktown.designsystem.WorldKind
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ChangePasswordScreen(
    onBack: () -> Unit,
    viewModel: ChangePasswordViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    World(WorldKind.Pink) {
        Scaffold(topBar = { BackTopBar(title = "Change password", onBack = onBack) }) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(Dimens.space24),
                verticalArrangement = Arrangement.spacedBy(Dimens.space16),
            ) {
                if (state.isChanged) {
                    Text("Password changed", style = MaterialTheme.typography.h5)
                    Text(
                        text = "Use the new password next time you sign in. Any other phone signed in " +
                            "to your account has been signed out.",
                        style = MaterialTheme.typography.body1,
                    )
                    Button(
                        onClick = onBack,
                        modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.buttonHeight),
                    ) {
                        Text("Back to profile")
                    }
                } else {
                    FormTextField(
                        value = state.currentPassword,
                        onValueChange = viewModel::onCurrentPasswordChange,
                        label = "Current password",
                        isPassword = true,
                    )
                    FormTextField(
                        value = state.newPassword,
                        onValueChange = viewModel::onNewPasswordChange,
                        label = "New password",
                        isPassword = true,
                        imeAction = ImeAction.Done,
                        onImeAction = viewModel::onSubmit,
                    )
                    Text(
                        text = "At least ${AccountRules.MIN_PASSWORD_LENGTH} characters.",
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.onBackground.copy(alpha = 0.7f),
                    )
                    FormError(state.error)
                    SubmitButton(
                        text = "Change password",
                        busyText = "Changing password...",
                        busy = state.isSaving,
                        onClick = viewModel::onSubmit,
                    )
                }
            }
        }
    }
}
