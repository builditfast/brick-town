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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun CreateAccountScreen(
    onBack: () -> Unit,
    viewModel: CreateAccountViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CreateAccountContent(
        state = state,
        onNameChange = viewModel::onNameChange,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onSubmit = viewModel::onSubmit,
        onBack = onBack,
    )
}

/** Create account on the sky. Stateless so the gallery can draw it. */
@Composable
internal fun CreateAccountContent(
    state: CreateAccountUiState,
    onNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
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
            Penny(PennyMood.Smile, size = 88.dp, motion = PennyMotion.Bob)
            Text("Create account", style = type.hero40.onWorld(hero = true), color = BrickColors.White, textAlign = TextAlign.Center)
            Text("One account, one town. Add your partner later.", style = type.body16.onWorld(), color = BrickColors.White, textAlign = TextAlign.Center)
            BrickTextField(
                value = state.name,
                onValueChange = onNameChange,
                label = "Name",
                capitalization = KeyboardCapitalization.Words,
            )
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
            Text(
                "At least ${AccountRules.MIN_PASSWORD_LENGTH} characters.",
                style = type.small12.onWorld(),
                color = BrickColors.White,
                modifier = Modifier.fillMaxWidth(),
            )
            ErrorCard(state.error)
            ChunkyButton(
                text = if (state.isSubmitting) "Creating account..." else "Create account",
                onClick = onSubmit,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isSubmitting,
            )
            ChunkyButton("Back", onBack, Modifier.fillMaxWidth(), kind = ButtonKind.Soft)
            Text(
                "By creating an account you agree to the terms of use and the privacy policy.",
                style = type.small12.onWorld(),
                color = BrickColors.White,
                textAlign = TextAlign.Center,
            )
            LegalLinksRow(linkColor = BrickColors.White)
        }
    }
}
