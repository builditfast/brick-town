package ai.freeapp.bricktown.join

import ai.freeapp.bricktown.auth.BrickTextField
import ai.freeapp.bricktown.auth.ErrorCard
import ai.freeapp.bricktown.designsystem.BrickColors
import ai.freeapp.bricktown.designsystem.ButtonKind
import ai.freeapp.bricktown.designsystem.ChunkyButton
import ai.freeapp.bricktown.designsystem.ChunkyEdge
import ai.freeapp.bricktown.designsystem.ChunkySurface
import ai.freeapp.bricktown.designsystem.Dimens
import ai.freeapp.bricktown.designsystem.LocalBrickType
import ai.freeapp.bricktown.designsystem.Penny
import ai.freeapp.bricktown.designsystem.PennyMood
import ai.freeapp.bricktown.designsystem.PennyMotion
import ai.freeapp.bricktown.designsystem.World
import ai.freeapp.bricktown.designsystem.WorldKind
import ai.freeapp.bricktown.designsystem.onWorld
import ai.freeapp.bricktown.wire.InvitePreview
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * "Join {town}?" for an invite [code] (empty: the user types one), then colour
 * and initial (spec 5.5, task 1026). [onOpenMonth] gets the joined town's name,
 * which Month greets once. Back on the colour step returns to the preview here.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun JoinScreen(
    code: String,
    onOpenMonth: (townName: String) -> Unit,
    onBack: () -> Unit,
    viewModel: JoinViewModel = koinViewModel(parameters = { parametersOf(code) }),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler(enabled = state.step == JoinStep.Pick) { viewModel.onStepBack() }
    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is JoinEffect.Joined -> onOpenMonth(effect.townName)
            }
        }
    }
    JoinContent(
        state = state,
        onCodeChange = viewModel::onCodeChange,
        onLookUp = viewModel::onLookUp,
        onContinue = viewModel::onContinue,
        onPickColor = viewModel::onPickColor,
        onInitialChange = viewModel::onInitialChange,
        onJoin = viewModel::onJoin,
        onBack = onBack,
    )
}

/** Every step of joining, stateless so the gallery can draw each. */
@Composable
internal fun JoinContent(
    state: JoinUiState,
    onCodeChange: (String) -> Unit,
    onLookUp: () -> Unit,
    onContinue: () -> Unit,
    onPickColor: (Int) -> Unit,
    onInitialChange: (String) -> Unit,
    onJoin: () -> Unit,
    onBack: () -> Unit,
) {
    val type = LocalBrickType.current
    val preview = state.preview
    World(WorldKind.Sky) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Dimens.space24, vertical = Dimens.space16),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.space16),
        ) {
            Penny(PennyMood.Smile, size = 88.dp, motion = PennyMotion.Bob, modifier = Modifier.padding(top = Dimens.space24))
            when {
                state.step == JoinStep.Looking -> {
                    Text("Looking for your invite...", style = type.hero40.copy(fontSize = type.title18.fontSize).onWorld(), color = BrickColors.White, textAlign = TextAlign.Center)
                }
                preview == null -> {
                    Text("Join a town", style = type.hero40.onWorld(hero = true), color = BrickColors.White, textAlign = TextAlign.Center)
                    Text("Type the invite code you were sent.", style = type.body16.onWorld(), color = BrickColors.White, textAlign = TextAlign.Center)
                    BrickTextField(
                        value = state.codeInput,
                        onValueChange = onCodeChange,
                        label = "Invite code",
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Go,
                        onImeAction = onLookUp,
                    )
                    ErrorCard(state.error)
                    ChunkyButton("Look up invite", onLookUp, Modifier.fillMaxWidth(), enabled = state.canLookUp)
                    ChunkyButton("Not now", onBack, Modifier.fillMaxWidth(), kind = ButtonKind.Soft)
                }
                state.step == JoinStep.Preview -> PreviewBlock(preview, state, onContinue, onBack)
                else -> PickBlock(preview, state, onPickColor, onInitialChange, onJoin)
            }
        }
    }
}

@Composable
private fun PreviewBlock(preview: InvitePreview, state: JoinUiState, onContinue: () -> Unit, onBack: () -> Unit) {
    val type = LocalBrickType.current
    Text("Join ${preview.townName}?", style = type.hero40.onWorld(hero = true), color = BrickColors.White, textAlign = TextAlign.Center)
    Text("${preview.ownerName} invited you to share their town.", style = type.body16.onWorld(), color = BrickColors.White, textAlign = TextAlign.Center)
    TowersPreview(preview)
    ErrorCard(state.error)
    ChunkyButton("Join", onContinue, Modifier.fillMaxWidth())
    ChunkyButton("Not now", onBack, Modifier.fillMaxWidth(), kind = ButtonKind.Soft)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TowersPreview(preview: InvitePreview) {
    val type = LocalBrickType.current
    ChunkySurface(Modifier.fillMaxWidth(), fill = BrickColors.White, edge = BrickColors.CardEdgeSky, edgeWidth = ChunkyEdge.Row) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("THEIR TOWERS", style = type.label11, color = BrickColors.Muted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                preview.categories.sortedBy { it.position }.forEach { category ->
                    val pair = BrickColors.CategoryPairs[category.colorIndex % BrickColors.CategoryPairs.size]
                    ChunkySurface(fill = pair.fill, edge = pair.edge, edgeWidth = ChunkyEdge.Tiny, radius = 12.dp) {
                        Text(category.name, style = type.small12, color = BrickColors.White, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PickBlock(
    preview: InvitePreview,
    state: JoinUiState,
    onPickColor: (Int) -> Unit,
    onInitialChange: (String) -> Unit,
    onJoin: () -> Unit,
) {
    val type = LocalBrickType.current
    Text("Pick your colour", style = type.hero40.onWorld(hero = true), color = BrickColors.White, textAlign = TextAlign.Center)
    Text("It marks your bricks in ${preview.townName}.", style = type.body16.onWorld(), color = BrickColors.White, textAlign = TextAlign.Center)
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BrickColors.MemberColors.forEachIndexed { index, color ->
            val taken = index in preview.takenColors
            val picked = state.colorIndex == index
            Box(
                Modifier
                    .size(36.dp)
                    .alpha(if (taken) 0.3f else 1f)
                    .background(color, CircleShape)
                    .border(if (picked) 4.dp else 0.dp, BrickColors.White, CircleShape)
                    .semantics { contentDescription = "Colour ${index + 1}" + if (taken) ", taken" else "" }
                    .clickable(enabled = !taken, role = Role.RadioButton) { onPickColor(index) },
            )
        }
    }
    BrickTextField(
        value = state.initial,
        onValueChange = onInitialChange,
        label = "Your initial",
        capitalization = KeyboardCapitalization.Characters,
        imeAction = ImeAction.Done,
        onImeAction = onJoin,
    )
    ErrorCard(state.error)
    ChunkyButton(
        text = if (state.isJoining) "Joining..." else "Join ${preview.townName}",
        onClick = onJoin,
        modifier = Modifier.fillMaxWidth(),
        enabled = state.canJoin,
    )
}
