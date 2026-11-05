package ai.freeapp.bricktown.compare

import ai.freeapp.bricktown.designsystem.BrickColors
import ai.freeapp.bricktown.designsystem.ChunkyEdge
import ai.freeapp.bricktown.designsystem.ChunkySurface
import ai.freeapp.bricktown.designsystem.Dimens
import ai.freeapp.bricktown.designsystem.LocalBrickType
import ai.freeapp.bricktown.designsystem.ProgressBar
import ai.freeapp.bricktown.designsystem.Rings
import ai.freeapp.bricktown.designsystem.SkyClouds
import ai.freeapp.bricktown.designsystem.World
import ai.freeapp.bricktown.designsystem.WorldKind
import ai.freeapp.bricktown.designsystem.bounceIn
import ai.freeapp.bricktown.designsystem.onWorld
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CompareContent(state: CompareUiState, modifier: Modifier = Modifier) {
    val type = LocalBrickType.current
    World(WorldKind.Sky, modifier.fillMaxSize(), clouds = SkyClouds.Compare) {
        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                state.title,
                style = type.body15.copy(color = BrickColors.White).onWorld(),
                modifier = Modifier.padding(top = Dimens.space12),
            )
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item(key = "rings") { RingsCard(state) }
                items(state.rows, key = { it.id }) { row -> TowerRow(row) }
                item(key = "end") { Box(Modifier.size(Dimens.space16)) }
            }
        }
    }
}

@Composable
private fun RingsCard(state: CompareUiState) {
    val type = LocalBrickType.current
    val aheadColor = when (state.ahead) {
        true -> BrickColors.Green.edge
        false -> BrickColors.Negative
        null -> BrickColors.Muted
    }
    ChunkySurface(Modifier.fillMaxWidth().bounceIn(), edge = BrickColors.CardEdgeSky, edgeWidth = ChunkyEdge.Primary, radius = 22.dp) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Rings(
                outer = state.outer,
                inner = state.inner,
                contentDescription = "${state.thisLegend}, ${state.lastLegend}",
                modifier = Modifier.size(130.dp),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(state.aheadText, style = type.hero40.copy(color = aheadColor), maxLines = 1)
                Text(state.aheadLabel, style = type.small13.copy(color = BrickColors.Muted))
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(BrickColors.YouBlue, RoundedCornerShape(3.dp)))
                    Text(state.thisLegend, style = type.small12.copy(color = BrickColors.Ink))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(BrickColors.TabMonth, RoundedCornerShape(3.dp)))
                    Text(state.lastLegend, style = type.small12.copy(color = BrickColors.Ink))
                }
            }
        }
    }
}

@Composable
private fun TowerRow(row: CompareRowUi) {
    val type = LocalBrickType.current
    val pair = BrickColors.CategoryPairs[row.colorIndex.mod(BrickColors.CategoryPairs.size)]
    ChunkySurface(
        Modifier.fillMaxWidth(),
        edge = BrickColors.CardEdgeSky,
        edgeWidth = ChunkyEdge.Row,
        radius = 18.dp,
        contentDescription = "${row.name}, ${row.amountText}, ${row.deltaText}",
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            ChunkySurface(fill = pair.fill, edge = pair.edge, edgeWidth = ChunkyEdge.Tiny, radius = 10.dp) {
                Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                    Text(row.short.take(1).uppercase(), style = type.title18.copy(color = BrickColors.White))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(row.name, style = type.body14.copy(color = BrickColors.Ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
                ProgressBar(row.barFraction, fill = pair.fill, height = 8.dp, contentDescription = null)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(row.amountText, style = type.row17.copy(color = BrickColors.Ink), maxLines = 1)
                Text(
                    row.deltaText,
                    style = type.label10.copy(color = if (row.deltaUp) BrickColors.Negative else BrickColors.Muted, letterSpacing = 0.sp),
                    maxLines = 1,
                )
            }
        }
    }
}
