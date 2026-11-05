package ai.freeapp.bricktown.auth

import ai.freeapp.bricktown.core.ui.LegalLinksRow
import ai.freeapp.bricktown.designsystem.BrickColors
import ai.freeapp.bricktown.designsystem.ButtonKind
import ai.freeapp.bricktown.designsystem.ChunkyButton
import ai.freeapp.bricktown.designsystem.Dimens
import ai.freeapp.bricktown.designsystem.LocalBrickType
import ai.freeapp.bricktown.designsystem.Penny
import ai.freeapp.bricktown.designsystem.PennyMood
import ai.freeapp.bricktown.designsystem.PennyMotion
import ai.freeapp.bricktown.designsystem.World
import ai.freeapp.bricktown.designsystem.WorldKind
import ai.freeapp.bricktown.designsystem.onWorld
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SignInScreen(
    onCreateAccount: () -> Unit,
    viewModel: SignInViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SignInContent(
        state = state,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onSubmit = viewModel::onSubmit,
        onCreateAccount = onCreateAccount,
    )
}

/** Sign in on the sky: Penny, two fields, the chunky button. Stateless so the gallery can draw it. */
@Composable
internal fun SignInContent(
    state: SignInUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onCreateAccount: () -> Unit,
) {
    val type = LocalBrickType.current
    World(WorldKind.Sky) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.space24, vertical = Dimens.space16),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.space16),
        ) {
            Penny(PennyMood.Smile, size = 88.dp, motion = PennyMotion.Bob, modifier = Modifier.padding(top = Dimens.space24))
            Text("Sign in", style = type.hero40.onWorld(hero = true), color = BrickColors.White)
            Text("Welcome back. Sign in to see your town.", style = type.body16.onWorld(), color = BrickColors.White)
            BrickTextField(
                value = state.email,
                onValueChange = onEmailChange,
                label = "Email",
                keyboardType = KeyboardType.Email,
            )
            BrickTextField(
                value = state.password,
                onValueChange = onPasswordChange,
                label = "Password",
                isPassword = true,
                imeAction = ImeAction.Done,
                onImeAction = onSubmit,
            )
            ErrorCard(state.error)
            ChunkyButton(
                text = if (state.isSubmitting) "Signing in..." else "Sign in",
                onClick = onSubmit,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isSubmitting,
            )
            ChunkyButton("New here? Create an account", onCreateAccount, Modifier.fillMaxWidth(), kind = ButtonKind.Soft)
            LegalLinksRow(linkColor = BrickColors.White)
        }
    }
}
