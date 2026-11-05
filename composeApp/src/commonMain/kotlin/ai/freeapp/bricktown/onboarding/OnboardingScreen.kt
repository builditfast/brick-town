package ai.freeapp.bricktown.onboarding

import ai.freeapp.bricktown.auth.ErrorCard
import ai.freeapp.bricktown.designsystem.BrickColors
import ai.freeapp.bricktown.designsystem.BrickPair
import ai.freeapp.bricktown.designsystem.ButtonKind
import ai.freeapp.bricktown.designsystem.ChunkyButton
import ai.freeapp.bricktown.designsystem.ChunkyEdge
import ai.freeapp.bricktown.designsystem.ChunkySurface
import ai.freeapp.bricktown.designsystem.Chip
import ai.freeapp.bricktown.designsystem.Confetti
import ai.freeapp.bricktown.designsystem.FormHero
import ai.freeapp.bricktown.designsystem.LocalBrickType
import ai.freeapp.bricktown.designsystem.Numpad
import ai.freeapp.bricktown.designsystem.Penny
import ai.freeapp.bricktown.designsystem.PennyMood
import ai.freeapp.bricktown.designsystem.PennyMotion
import ai.freeapp.bricktown.designsystem.SkyClouds
import ai.freeapp.bricktown.designsystem.World
import ai.freeapp.bricktown.designsystem.WorldKind
import ai.freeapp.bricktown.designsystem.bounceIn
import ai.freeapp.bricktown.designsystem.onWorld
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.BackHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

/**
 * Creating a town: budget, towers, first brick, then Welcome (spec 5.5, task
 * 1026). Step 1 links to [onJoinTown]; Welcome's "Open my town" is [onOpenMonth].
 * System Back on steps 2 and 3 goes one step back here, so the app's own Back
 * only sees it on step 1. The on-screen Back of step 1 signs out (5a draws it).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun OnboardingScreen(
    onJoinTown: () -> Unit,
    onOpenMonth: () -> Unit,
    viewModel: OnboardingViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler(enabled = state.systemBackStepsBack, onBack = viewModel::onBack)
    OnboardingContent(
        state = state,
        onKey = viewModel::onKey,
        onToggleTower = viewModel::onToggleTower,
        onPickFirstTower = viewModel::onPickFirstTower,
        onNext = viewModel::onNext,
        onBack = viewModel::onBack,
        onJoinTown = onJoinTown,
        onOpenMonth = onOpenMonth,
    )
}

/** All four steps, stateless so the gallery can draw each from fixture state. */
@Composable
internal fun OnboardingContent(
    state: OnboardingUiState,
    onKey: (Char) -> Unit,
    onToggleTower: (Int) -> Unit,
    onPickFirstTower: (Int) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onJoinTown: () -> Unit,
    onOpenMonth: () -> Unit,
) {
    if (state.step == OnboardingStep.Welcome) {
        WelcomeStep(state, onOpenMonth)
        return
    }
    World(WorldKind.Sky, clouds = SkyClouds.Onboarding) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // 5a: budget keys are a 54 dp face on a 4 dp edge; Numpad counts the edge in keySize.
            val keySize = if (maxHeight < 700.dp) 50.dp else 58.dp
            // The handoff's 44 dp under each button is iOS's 34 dp home indicator plus 10; the World pads for the system bar.
            Column(Modifier.fillMaxSize().padding(horizontal = 18.dp).padding(top = 18.dp, bottom = 10.dp)) {
                ProgressRow(state, onBack)
                Spacer(Modifier.height(14.dp))
                when (state.step) {
                    OnboardingStep.Budget -> BudgetStep(state, keySize, onKey, onNext, onJoinTown)
                    OnboardingStep.Towers -> TowersStep(state, onToggleTower, onNext)
                    else -> FirstBrickStep(state, keySize, onKey, onPickFirstTower, onNext)
                }
            }
        }
    }
}

@Composable
private fun ProgressRow(state: OnboardingUiState, onBack: () -> Unit) {
    val type = LocalBrickType.current
    val active = state.step.ordinal
    Row(Modifier.fillMaxWidth().heightIn(min = 24.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            repeat(3) { i ->
                Box(
                    Modifier
                        .width(if (i == active) 28.dp else 10.dp)
                        .height(10.dp)
                        .alpha(if (i == active) 1f else 0.4f)
                        .background(BrickColors.White, RoundedCornerShape(5.dp)),
                )
            }
        }
        Spacer(Modifier.weight(1f))
        if (state.canGoBack) {
            Text(
                "Back",
                style = type.small12.onWorld(),
                color = BrickColors.White,
                // No side padding: 5a ends the word on the header's edge, and Compose widens its touch to 48 dp.
                modifier = Modifier.clickable(onClick = onBack).padding(vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun PennySays(title: String, sub: String) {
    val type = LocalBrickType.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Penny(PennyMood.Smile, size = 56.dp, motion = PennyMotion.Bob)
        ChunkySurface(Modifier.weight(1f), fill = BrickColors.White, edge = BrickColors.CardEdgeSky, edgeWidth = ChunkyEdge.Row) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                Text(title, style = type.body16.copy(fontSize = 16.sp, lineHeight = 19.sp), color = BrickColors.Ink)
                Spacer(Modifier.height(4.dp))
                Text(sub, style = type.small12, color = BrickColors.Muted)
            }
        }
    }
}

/** The typed euro amount, "€" on the digits' baseline (5a). */
@Composable
private fun AmountRow(draft: String, fontSize: Float, modifier: Modifier = Modifier) {
    val digits = draft.ifEmpty { "0" }
    FormHero(sign = "", symbol = "€", symbolAfter = false, digits = digits, spoken = "€$digits", modifier = modifier.fillMaxWidth(), fontSize = fontSize)
}

@Composable
private fun ColumnScope.BudgetStep(
    state: OnboardingUiState,
    keySize: Dp,
    onKey: (Char) -> Unit,
    onNext: () -> Unit,
    onJoinTown: () -> Unit,
) {
    val type = LocalBrickType.current
    PennySays("Hi, I'm Penny. How much can you spend a month?", state.dailyLine ?: "Rent not included, just the money that moves.")
    AmountRow(state.budgetDraft, 72f, Modifier.padding(top = 26.dp, bottom = 18.dp))
    Text(
        "Joining a town? Enter invite code",
        style = type.small13.onWorld(),
        color = BrickColors.White,
        modifier = Modifier.clickable(onClick = onJoinTown).padding(vertical = 10.dp),
    )
    Spacer(Modifier.weight(1f))
    Numpad(onKey, Modifier.fillMaxWidth(), keySize, edge = BrickColors.CardEdgeSky)
    Spacer(Modifier.height(12.dp))
    ChunkyButton("Next \u00B7 pick your towers", onNext, Modifier.fillMaxWidth(), enabled = state.canNext)
}

@Composable
private fun ColumnScope.TowersStep(state: OnboardingUiState, onToggleTower: (Int) -> Unit, onNext: () -> Unit) {
    val type = LocalBrickType.current
    PennySays("Where does your money usually go?", "Each one becomes a tower. You can add more later.")
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 22.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(state.towers) { index, tower ->
            val pair = towerPair(tower)
            ChunkySurface(
                Modifier.fillMaxWidth(),
                fill = if (tower.picked) pair.fill else BrickColors.White,
                edge = if (tower.picked) pair.edge else BrickColors.CardEdgeSky,
                edgeWidth = ChunkyEdge.Row,
                radius = 16.dp,
                onClick = { onToggleTower(index) },
                contentDescription = tower.name + if (tower.picked) ", picked" else "",
            ) {
                Text(
                    tower.name,
                    style = type.body14.copy(fontSize = 14.sp),
                    color = if (tower.picked) BrickColors.White else BrickColors.Ink,
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                )
            }
        }
    }
    Text(
        if (state.pickedCount == 1) "1 tower" else "${state.pickedCount} towers",
        style = type.small13.onWorld(),
        color = BrickColors.White,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
    Spacer(Modifier.height(12.dp))
    ChunkyButton("Next \u00B7 lay the first brick", onNext, Modifier.fillMaxWidth(), enabled = state.canNext)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.FirstBrickStep(
    state: OnboardingUiState,
    keySize: Dp,
    onKey: (Char) -> Unit,
    onPickFirstTower: (Int) -> Unit,
    onNext: () -> Unit,
) {
    PennySays("What did you last spend money on?", "That's your first brick. Every \u20AC25 is one.")
    AmountRow(state.firstDraft, 64f, Modifier.padding(top = 18.dp, bottom = 12.dp))
    FlowRow(
        Modifier.fillMaxWidth().padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        state.towers.forEachIndexed { index, tower ->
            if (tower.picked) {
                Chip(tower.name, selected = state.firstTower == index, onClick = { onPickFirstTower(index) }, selectedColors = towerPair(tower))
            }
        }
    }
    Spacer(Modifier.weight(1f))
    Numpad(onKey, Modifier.fillMaxWidth(), keySize - 4.dp, edge = BrickColors.CardEdgeSky)
    ErrorCard(state.error, Modifier.padding(top = 8.dp))
    Spacer(Modifier.height(12.dp))
    val label = when {
        state.isCreating -> "Building your town..."
        state.firstCents > 0 && state.firstTowerName != null ->
            "Lay the first brick \u00B7 ${firstAmountText(state)} on ${state.firstTowerName}"
        else -> "Skip, I'll add one later"
    }
    ChunkyButton(label, onNext, Modifier.fillMaxWidth(), kind = ButtonKind.Gold, enabled = state.canNext)
}

private fun firstAmountText(state: OnboardingUiState): String {
    val cents = state.firstCents
    return if (cents % 100 == 0L) "\u20AC${cents / 100}" else "\u20AC${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"
}

private fun towerPair(tower: TowerChoice): BrickPair = BrickColors.CategoryPairs[tower.colorIndex % BrickColors.CategoryPairs.size]

@Composable
private fun WelcomeStep(state: OnboardingUiState, onOpenMonth: () -> Unit) {
    val type = LocalBrickType.current
    val brick = state.firstTower?.let { state.towers.getOrNull(it) }?.takeIf { state.firstCents > 0 }?.let(::towerPair)
        ?: BrickColors.CategoryPairs.first()
    World(WorldKind.Pink) {
        Confetti(burstKey = 1, modifier = Modifier.fillMaxSize())
        Column(
            Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
        ) {
            Box(Modifier.height(150.dp), contentAlignment = Alignment.BottomCenter) {
                Penny(PennyMood.Smile, size = 110.dp, motion = PennyMotion.Jump, modifier = Modifier.padding(bottom = 27.dp))
                Box(
                    Modifier
                        .padding(bottom = 14.dp)
                        .width(52.dp)
                        .height(13.dp)
                        .bounceIn()
                        .background(brick.fill, RoundedCornerShape(4.dp)),
                )
                Box(Modifier.width(180.dp).height(14.dp).background(BrickColors.Ground.edge, RoundedCornerShape(7.dp)))
            }
            Text(
                "Welcome to\nBrick Town",
                style = type.hero40.onWorld(hero = true),
                color = BrickColors.White,
                textAlign = TextAlign.Center,
            )
            ChunkySurface(Modifier.fillMaxWidth(), fill = BrickColors.White, edge = BrickColors.CardEdge, edgeWidth = ChunkyEdge.Row) {
                Text(
                    state.summary,
                    style = type.body14,
                    color = BrickColors.Ink,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
            }
            ChunkyButton("Open my town", onOpenMonth, Modifier.fillMaxWidth())
        }
    }
}
