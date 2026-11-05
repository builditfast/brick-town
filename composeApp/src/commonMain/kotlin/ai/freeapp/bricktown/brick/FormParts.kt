package ai.freeapp.bricktown.brick

import ai.freeapp.bricktown.designsystem.BrickColors
import ai.freeapp.bricktown.designsystem.ChunkyEdge
import ai.freeapp.bricktown.designsystem.ChunkySurface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.remember
import androidx.compose.ui.composed
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** A white disc with a blue X: closes the brick form and the tower sheet. */
@Composable
fun CloseDisc(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    contentDescription: String = "Close",
    edge: Color = BrickColors.CardEdge,
) {
    ChunkySurface(
        modifier = modifier,
        fill = BrickColors.White,
        edge = edge,
        edgeWidth = ChunkyEdge.Chip,
        radius = size / 2,
        onClick = onClick,
        contentDescription = contentDescription,
        // The frames draw the disc flush with the row's padding; a reserved 48 dp box would inset it by 4.
        reserveTouchBox = false,
    ) {
        Box(Modifier.size(size - ChunkyEdge.Chip), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(18.dp)) {
                val u = this.size.width / 24f
                val w = 3.5f * u
                drawLine(BrickColors.LinkBlue, Offset(5f * u, 5f * u), Offset(19f * u, 19f * u), w, StrokeCap.Round)
                drawLine(BrickColors.LinkBlue, Offset(19f * u, 5f * u), Offset(5f * u, 19f * u), w, StrokeCap.Round)
            }
        }
    }
}

/** A rounded dashed outline, drawn behind the content. */
fun Modifier.dashedOutline(color: Color, radius: Dp = 12.dp, width: Dp = 2.dp): Modifier = drawBehind {
    val w = width.toPx()
    drawRoundRect(
        color = color,
        topLeft = androidx.compose.ui.geometry.Offset(w / 2, w / 2),
        size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w),
        cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(w * 3, w * 2))),
    )
}

/** A tap with no ripple or press sink, for the chip-like boxes that draw their own surface. */
fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = composed {
    clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
}

fun Modifier.wrapCenter(): Modifier = this.wrapContentSize(Alignment.Center)
