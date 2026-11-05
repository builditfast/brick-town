package ai.freeapp.bricktown.auth

import ai.freeapp.bricktown.core.ui.toMessage
import ai.freeapp.bricktown.designsystem.BrickColors
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
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.datetime.LocalDate
import org.koin.compose.viewmodel.koinViewModel

/** The sky with Penny while [StartViewModel] checks the session and the town. */
@Composable
fun StartScreen(
    onShowJoin: (code: String) -> Unit,
    onShowOnboarding: () -> Unit,
    onShowRollover: (monthStart: LocalDate) -> Unit,
    onShowMonth: () -> Unit,
    viewModel: StartViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val showJoin by rememberUpdatedState(onShowJoin)
    val showOnboarding by rememberUpdatedState(onShowOnboarding)
    val showRollover by rememberUpdatedState(onShowRollover)
    val showMonth by rememberUpdatedState(onShowMonth)
    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is StartEffect.ShowJoin -> showJoin(effect.code)
                StartEffect.ShowOnboarding -> showOnboarding()
                is StartEffect.ShowRollover -> showRollover(effect.monthStart)
                StartEffect.ShowMonth -> showMonth()
            }
        }
    }

    val type = LocalBrickType.current
    World(WorldKind.Sky, Modifier.fillMaxSize()) {
        Column(
            Modifier.align(Alignment.Center).fillMaxWidth().padding(Dimens.space24),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.space24),
        ) {
            val error = state.error
            Penny(
                mood = if (error == null) PennyMood.Smile else PennyMood.Neutral,
                size = 96.dp,
                motion = if (error == null) PennyMotion.Bob else PennyMotion.Still,
            )
            if (error == null) {
                CircularProgressIndicator(color = BrickColors.White)
            } else {
                Text(
                    text = error.toMessage(),
                    style = type.body16.onWorld(),
                    color = BrickColors.White,
                    textAlign = TextAlign.Center,
                )
                ChunkyButton(text = "Try again", onClick = viewModel::onRetry, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
