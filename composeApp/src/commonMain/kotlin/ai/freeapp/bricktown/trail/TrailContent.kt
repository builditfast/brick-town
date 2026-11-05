package ai.freeapp.bricktown.trail

import ai.freeapp.bricktown.designsystem.BrickColors
import ai.freeapp.bricktown.designsystem.ChunkyEdge
import ai.freeapp.bricktown.designsystem.ChunkySurface
import ai.freeapp.bricktown.designsystem.Dimens
import ai.freeapp.bricktown.designsystem.LocalBrickType
import ai.freeapp.bricktown.designsystem.Penny
import ai.freeapp.bricktown.designsystem.PennyMood
import ai.freeapp.bricktown.designsystem.PennyMotion
import ai.freeapp.bricktown.designsystem.World
import ai.freeapp.bricktown.designsystem.WorldKind
import ai.freeapp.bricktown.designsystem.bounceIn
import ai.freeapp.bricktown.designsystem.onWorld
import ai.freeapp.bricktown.designsystem.pulseRing
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val NodeSize = 66.dp
private val LabelWidth = 118.dp
private val NodeGap = 10.dp
private val RowHeight = 88.dp

/** Node centre as a fraction of the row width: 20 %, 50 %, 80 %, 50 % and round again. */
private val NodeX = floatArrayOf(0.2f, 0.5f, 0.8f, 0.5f)

@Composable
fun TrailContent(state: TrailUiState, modifier: Modifier = Modifier) {
    val type = LocalBrickType.current
    World(WorldKind.Sky, modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                Modifier.padding(top = Dimens.space12),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Penny(PennyMood.Smile, size = 40.dp, motion = PennyMotion.Bob)
                Text(state.penny, style = type.body15.copy(color = BrickColors.White).onWorld(), modifier = Modifier.weight(1f))
            }
            Column(Modifier.padding(vertical = 8.dp)) {
                state.nodes.forEachIndexed { i, node -> TrailRow(i, node) }
            }
            Box(Modifier.height(Dimens.space16))
        }
    }
}

@Composable
private fun TrailRow(index: Int, node: TrailNodeUi) {
    val x = NodeX[index % NodeX.size]
    val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
    val lineColor = BrickColors.White.copy(alpha = 0.8f)
    Box(
        Modifier.fillMaxWidth().height(RowHeight).drawBehind {
            val w = 4.dp.toPx()
            val cx = size.width / 2f
            drawLine(lineColor, Offset(cx, 0f), Offset(cx, size.height), w, pathEffect = dash)
            if (x != 0.5f) {
                drawLine(lineColor, Offset(cx, size.height / 2f), Offset(size.width * x, size.height / 2f), w, pathEffect = dash)
            }
        },
    ) {
        // The label hangs toward the centre line; a node on the centre line puts it to the right.
        val labelOnRight = x <= 0.5f
        Layout(
            content = {
                NodeCircle(node, index)
                LabelCard(node)
            },
            modifier = Modifier.fillMaxSize(),
        ) { measurables, constraints ->
            val loose = constraints.copy(minWidth = 0, minHeight = 0)
            val circle = measurables[0].measure(loose)
            val label = measurables[1].measure(loose)
            val gap = NodeGap.roundToPx()
            val centreX = (constraints.maxWidth * x).toInt()
            val circleX = centreX - circle.width / 2
            val labelX = if (labelOnRight) circleX + circle.width + gap else circleX - gap - label.width
            layout(constraints.maxWidth, constraints.maxHeight) {
                circle.place(circleX, (constraints.maxHeight - circle.height) / 2)
                label.place(labelX.coerceAtLeast(0), (constraints.maxHeight - label.height) / 2)
            }
        }
    }
}

@Composable
private fun NodeCircle(node: TrailNodeUi, index: Int) {
    val (fill, edge) = when (node.kind) {
        TrailNodeKind.Earned -> BrickColors.Green.fill to BrickColors.Green.edge
        TrailNodeKind.Next -> BrickColors.White to BrickColors.CardEdgeSky
        TrailNodeKind.Locked -> BrickColors.TrailLocked to BrickColors.SoftSkyEdge
    }
    Box(
        Modifier
            .bounceIn(delayMillis = index * 90)
            .size(NodeSize + 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(NodeSize).pulseRing(enabled = node.kind == TrailNodeKind.Next).background(edge, CircleShape),
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(Modifier.size(NodeSize, NodeSize - 6.dp).background(fill, CircleShape), contentAlignment = Alignment.Center) {
                when (node.kind) {
                    TrailNodeKind.Earned -> CheckGlyph(BrickColors.White, 30.dp, strokeWidth = 4f)
                    TrailNodeKind.Next -> Penny(PennyMood.Smile, size = 34.dp)
                    TrailNodeKind.Locked -> LockGlyph(BrickColors.TrailLockIcon, 26.dp)
                }
            }
        }
    }
}

@Composable
private fun LabelCard(node: TrailNodeUi) {
    val type = LocalBrickType.current
    ChunkySurface(
        Modifier.width(LabelWidth),
        edge = BrickColors.CardEdgeSky,
        edgeWidth = ChunkyEdge.Chip,
        radius = 12.dp,
        contentDescription = "${node.title}, ${node.subtitle}",
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(node.title, style = type.small12.copy(color = BrickColors.Ink, lineHeight = 14.sp))
            Text(node.subtitle, style = type.label10.copy(color = BrickColors.Muted, letterSpacing = 0.sp), modifier = Modifier.padding(top = 2.dp))
        }
    }
}
