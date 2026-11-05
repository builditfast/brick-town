package ai.freeapp.bricktown.auth

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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** The root without a session: the sky, Penny, and the two ways in. */
@Composable
fun LandingScreen(onSignIn: () -> Unit, onCreateAccount: () -> Unit) {
    val type = LocalBrickType.current
    World(WorldKind.Sky, Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(Dimens.space24),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.space16),
        ) {
            Spacer(Modifier.weight(1f))
            Penny(PennyMood.Smile, size = 120.dp, motion = PennyMotion.Bob)
            Text("Brick Town", style = type.hero46.onWorld(hero = true), color = BrickColors.White, textAlign = TextAlign.Center)
            Text(
                "Every €25 you spend is one brick. Watch your month build a town.",
                style = type.body16.onWorld(),
                color = BrickColors.White,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.weight(1f))
            ChunkyButton("Create account", onCreateAccount, Modifier.fillMaxWidth())
            ChunkyButton("Sign in", onSignIn, Modifier.fillMaxWidth(), kind = ButtonKind.Soft)
        }
    }
}
