package ai.freeapp.bricktown.rollover

import ai.freeapp.bricktown.designsystem.BrickColors
import ai.freeapp.bricktown.designsystem.ButtonKind
import ai.freeapp.bricktown.designsystem.ChunkyButton
import ai.freeapp.bricktown.designsystem.ChunkyEdge
import ai.freeapp.bricktown.designsystem.ChunkySurface
import ai.freeapp.bricktown.designsystem.Confetti
import ai.freeapp.bricktown.designsystem.Dimens
import ai.freeapp.bricktown.designsystem.LocalBrickType
import ai.freeapp.bricktown.designsystem.World
import ai.freeapp.bricktown.designsystem.WorldKind
import ai.freeapp.bricktown.designsystem.bounceIn
import ai.freeapp.bricktown.designsystem.onWorld
import ai.freeapp.bricktown.trail.CheckGlyph
import ai.freeapp.bricktown.trail.CupGlyph
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ChartHeight = 72.dp

/** The night-sky month trophy. [onStartMonth] leaves the screen. */
@Composable
fun RolloverContent(state: RolloverUiState, onStartMonth: () -> Unit, modifier: Modifier = Modifier) {
    val type = LocalBrickType.current
    World(WorldKind.Night, modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.found) {
                    Text(
                        state.dateLine,
                        style = type.body15.copy(color = BrickColors.White).onWorld(),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = Dimens.space12),
                    )
                }
                Text(
                    state.title,
                    style = type.hero40.copy(color = BrickColors.White).onWorld(hero = true),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = if (state.found) 0.dp else Dimens.space48),
                )
                if (state.found) {
                    TrophyCard(state)
                    if (state.badges.isNotEmpty()) BadgeTiles(state)
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(BrickColors.White.copy(alpha = 0.16f)).padding(14.dp)) {
                        Text(state.note, style = type.small13.copy(color = BrickColors.White))
                    }
                }
            }
            ChunkyButton(state.startLabel, onStartMonth, Modifier.fillMaxWidth().padding(vertical = 14.dp), kind = ButtonKind.Gold)
        }
        if (state.found && state.underBudget) Confetti(burstKey = 1, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun TrophyCard(state: RolloverUiState) {
    val type = LocalBrickType.current
    ChunkySurface(Modifier.fillMaxWidth().bounceIn(), edgeWidth = ChunkyEdge.Primary, radius = 24.dp) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                ChunkySurface(fill = BrickColors.Gold.fill, edge = BrickColors.Gold.edge, edgeWidth = ChunkyEdge.Tiny, radius = 10.dp) {
                    Box(Modifier.size(52.dp, 62.dp), contentAlignment = Alignment.Center) {
                        if (state.underBudget) CupGlyph(BrickColors.Ink, 30.dp) else CloseMark()
                    }
                }
                Column {
                    Text(if (state.underBudget) "UNDER BUDGET" else "OVER BUDGET", style = type.label11.copy(color = BrickColors.Muted))
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(state.spentText, style = type.hero40.copy(color = BrickColors.Ink), maxLines = 1)
                        Text(state.budgetText, style = type.small13.copy(color = BrickColors.Muted), maxLines = 1, modifier = Modifier.padding(bottom = 6.dp))
                    }
                }
            }
            MiniTowers(state)
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(BrickColors.Ground.fill))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("${state.goldDays}", "gold days", Modifier.weight(1f), BrickColors.Ink)
                Stat(state.vsText, state.vsLabel, Modifier.weight(1f), if (state.vsGood) BrickColors.Green.edge else BrickColors.Negative)
                Stat("${state.streak}", "day streak", Modifier.weight(1f), BrickColors.Ink)
            }
        }
    }
}

@Composable
private fun CloseMark() {
    val type = LocalBrickType.current
    Text("!", style = type.hero40.copy(color = BrickColors.Ink))
}

@Composable
private fun MiniTowers(state: RolloverUiState) {
    LazyRow(
        Modifier.fillMaxWidth().height(ChartHeight),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
        userScrollEnabled = false,
    ) {
        items(state.bars) { bar ->
            val pair = BrickColors.CategoryPairs[bar.colorIndex.mod(BrickColors.CategoryPairs.size)]
            ChunkySurface(Modifier.width(48.dp).height(ChartHeight * bar.fraction), fill = pair.fill, edge = pair.edge, edgeWidth = ChunkyEdge.Chip, radius = 4.dp) { Box(Modifier.fillMaxSize()) }
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier, valueColor: androidx.compose.ui.graphics.Color) {
    val type = LocalBrickType.current
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(BrickColors.SoftFill).padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = type.row22.copy(color = valueColor), maxLines = 1)
        Text(label, style = type.label10.copy(color = BrickColors.Muted, letterSpacing = 0.sp), maxLines = 1)
    }
}

@Composable
private fun BadgeTiles(state: RolloverUiState) {
    val type = LocalBrickType.current
    LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(state.badges) { title ->
            ChunkySurface(Modifier.width(170.dp).bounceIn(delayMillis = 200), fill = BrickColors.Green.fill, edge = BrickColors.Green.edge, edgeWidth = ChunkyEdge.Row, radius = 18.dp) {
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(30.dp).clip(CircleShape).background(BrickColors.White.copy(alpha = 0.3f)), contentAlignment = Alignment.Center) {
                        CheckGlyph(BrickColors.White, 16.dp)
                    }
                    Column {
                        Text(title, style = type.body14.copy(color = BrickColors.White), maxLines = 2)
                        Text("badge earned", style = type.label10.copy(color = BrickColors.White.copy(alpha = 0.85f), letterSpacing = 0.sp))
                    }
                }
            }
        }
    }
}
