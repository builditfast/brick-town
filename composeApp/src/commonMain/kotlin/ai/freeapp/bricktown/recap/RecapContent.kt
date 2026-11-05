package ai.freeapp.bricktown.recap

import ai.freeapp.bricktown.designsystem.BrickColors
import ai.freeapp.bricktown.designsystem.ButtonSize
import ai.freeapp.bricktown.designsystem.ChunkyButton
import ai.freeapp.bricktown.designsystem.ChunkyEdge
import ai.freeapp.bricktown.designsystem.ChunkySurface
import ai.freeapp.bricktown.designsystem.Dimens
import ai.freeapp.bricktown.designsystem.FlexRow
import ai.freeapp.bricktown.designsystem.LocalBrickType
import ai.freeapp.bricktown.designsystem.Penny
import ai.freeapp.bricktown.designsystem.PennyMood
import ai.freeapp.bricktown.designsystem.PennyMotion
import ai.freeapp.bricktown.designsystem.SkyClouds
import ai.freeapp.bricktown.designsystem.World
import ai.freeapp.bricktown.designsystem.WorldKind
import ai.freeapp.bricktown.designsystem.bounceIn
import ai.freeapp.bricktown.designsystem.onWorld
import ai.freeapp.bricktown.trail.CheckGlyph
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

private val BarWidth = 42.dp
private val BarArea = 110.dp

@Composable
fun RecapContent(state: RecapUiState, onShareRecap: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val type = LocalBrickType.current
    World(WorldKind.Sky, modifier.fillMaxSize(), clouds = SkyClouds.Recap) {
        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    state.header,
                    style = type.body15.copy(color = BrickColors.White).onWorld(),
                    modifier = Modifier.padding(top = Dimens.space12),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                    Penny(if (state.pennyHappy) PennyMood.Smile else PennyMood.Neutral, size = 56.dp, motion = PennyMotion.Bob)
                    ChunkySurface(Modifier.weight(1f).bounceIn(), edge = BrickColors.CardEdgeSky, edgeWidth = ChunkyEdge.Row, radius = 18.dp) {
                        Text(
                            state.pennyLine,
                            style = type.body15.copy(color = BrickColors.Ink, fontWeight = FontWeight.Black, lineHeight = 1.25.em),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        )
                    }
                }
                SpentCard(state)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.insights.forEachIndexed { i, insight -> InsightRow(insight, i) }
                }
                Box(Modifier.height(Dimens.space8))
            }
            FlexRow(listOf(1f, 2f), padding = 14.dp, gap = 8.dp, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                ShareButton(onShareRecap)
                ChunkyButton("Nice · back to town", onBack, size = ButtonSize.Medium)
            }
        }
    }
}

@Composable
private fun ShareButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val type = LocalBrickType.current
    ChunkySurface(modifier, edge = BrickColors.CardEdgeSky, edgeWidth = ChunkyEdge.Primary, radius = 18.dp, onClick = onClick, contentDescription = "Share") {
        Box(Modifier.fillMaxWidth().padding(vertical = ButtonSize.Medium.padding), contentAlignment = Alignment.Center) {
            Text("Share", style = type.body15.copy(color = BrickColors.LinkBlue, fontWeight = FontWeight.Black))
        }
    }
}

@Composable
private fun SpentCard(state: RecapUiState) {
    val type = LocalBrickType.current
    ChunkySurface(Modifier.fillMaxWidth().bounceIn(delayMillis = 80), edge = BrickColors.CardEdgeSky, edgeWidth = ChunkyEdge.Primary, radius = 24.dp) {
        Column(Modifier.padding(18.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("SPENT", style = type.label11.copy(color = BrickColors.Muted))
                    Text(state.spentText, style = type.hero44.copy(color = BrickColors.Ink), maxLines = 1)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        state.deltaText,
                        style = type.row22.copy(color = if (state.deltaGood) BrickColors.Green.edge else BrickColors.Negative),
                        maxLines = 1,
                    )
                    Text(state.deltaLabel, style = type.label10.copy(color = BrickColors.Muted, letterSpacing = 0.sp))
                }
            }
            LazyRow(
                Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                userScrollEnabled = false,
            ) {
                items(state.days) { day -> DayColumn(day) }
            }
        }
    }
}

@Composable
private fun DayColumn(day: RecapDayUi) {
    val type = LocalBrickType.current
    Column(Modifier.width(BarWidth), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(BarArea), contentAlignment = Alignment.BottomCenter) {
            if (day.fraction > 0f) {
                ChunkySurface(
                    Modifier.fillMaxWidth().height(BarArea * day.fraction),
                    fill = BrickColors.YouBlue,
                    edge = BrickColors.LinkBlue,
                    edgeWidth = ChunkyEdge.Chip,
                    radius = 6.dp,
                ) { Box(Modifier.fillMaxSize()) }
            }
        }
        Box(Modifier.padding(top = 14.dp), contentAlignment = Alignment.TopCenter) {
            if (day.gold) {
                ChunkySurface(fill = BrickColors.Gold.fill, edge = BrickColors.Gold.edge, edgeWidth = ChunkyEdge.Tiny, radius = 8.dp, contentDescription = "Gold day") {
                    Box(Modifier.size(BarWidth, BarWidth - ChunkyEdge.Tiny), contentAlignment = Alignment.Center) { CheckGlyph(BrickColors.Ink, 16.dp, strokeWidth = 3.5f) }
                }
            } else {
                Text(day.letter, style = type.label11.copy(color = BrickColors.Muted))
            }
        }
    }
}

@Composable
private fun InsightRow(insight: RecapInsightUi, index: Int) {
    val type = LocalBrickType.current
    val tower = BrickColors.CategoryPairs[insight.colorIndex.mod(BrickColors.CategoryPairs.size)]
    val member = BrickColors.MemberColors[insight.colorIndex.mod(BrickColors.MemberColors.size)]
    val (fill, edge) = if (insight.initial == null) tower.fill to tower.edge else member to lerp(member, BrickColors.Ink, 0.25f)
    ChunkySurface(
        Modifier.fillMaxWidth().bounceIn(delayMillis = 160 + index * 80),
        edge = BrickColors.CardEdgeSky,
        edgeWidth = ChunkyEdge.Row,
        radius = 18.dp,
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ChunkySurface(fill = fill, edge = edge, edgeWidth = ChunkyEdge.Tiny, radius = 12.dp) {
                Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                    insight.initial?.let { Text(it, style = type.title18.copy(color = BrickColors.White)) }
                }
            }
            Column(Modifier.weight(1f)) {
                Text(insight.title, style = type.body14.copy(color = BrickColors.Ink), maxLines = 1)
                Text(insight.subtitle, style = type.label11.copy(color = BrickColors.Muted, letterSpacing = 0.sp), maxLines = 1)
            }
            Text(insight.amountText, style = type.row17.copy(color = BrickColors.Ink), maxLines = 1)
        }
    }
}
