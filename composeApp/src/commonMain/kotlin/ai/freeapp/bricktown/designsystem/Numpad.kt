package ai.freeapp.bricktown.designsystem

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Keys the [Numpad] emits. Digits and the decimal point are themselves; delete is [BACKSPACE]. */
const val BACKSPACE: Char = '\b'

private val NumpadRows = listOf(
    listOf('1', '2', '3'),
    listOf('4', '5', '6'),
    listOf('7', '8', '9'),
    listOf('.', '0', BACKSPACE),
)

/**
 * 3 x 4 white keys, 8 dp apart. [keySize] counts the 4 dp [edge] under each key's face, so the handoff's
 * `height:50px` key is 54 here. Emits only the key Char, the caller owns the amount.
 */
@Composable
fun Numpad(
    onKey: (Char) -> Unit,
    modifier: Modifier = Modifier,
    keySize: Dp = 54.dp,
    edge: Color = BrickColors.CardEdge,
) {
    val type = LocalBrickType.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        NumpadRows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { key ->
                    val description = when (key) {
                        BACKSPACE -> "Delete"
                        '.' -> "Decimal point"
                        else -> key.toString()
                    }
                    ChunkySurface(
                        modifier = Modifier.weight(1f),
                        fill = BrickColors.White,
                        edge = edge,
                        edgeWidth = ChunkyEdge.Chip,
                        radius = 14.dp,
                        onClick = { onKey(key) },
                        contentDescription = description,
                    ) {
                        Box(Modifier.fillMaxWidth().height(keySize - ChunkyEdge.Chip), contentAlignment = Alignment.Center) {
                            if (key == BACKSPACE) {
                                Canvas(Modifier.size(26.dp, 20.dp)) { drawBackspace(BrickColors.Ink) }
                            } else {
                                Text(
                                    key.toString(),
                                    style = type.key.copy(color = BrickColors.Ink, textAlign = TextAlign.Center),
                                    modifier = Modifier.clearAndSetSemantics { },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBackspace(color: Color) {
    val w = size.width
    val h = size.height
    val s = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    val outline = Path().apply {
        moveTo(w * 0.32f, h * 0.05f); lineTo(w * 0.95f, h * 0.05f); lineTo(w * 0.95f, h * 0.95f)
        lineTo(w * 0.32f, h * 0.95f); lineTo(w * 0.04f, h * 0.5f); close()
    }
    drawPath(outline, color, style = s)
    drawLine(color, Offset(w * 0.5f, h * 0.3f), Offset(w * 0.78f, h * 0.7f), s.width, StrokeCap.Round)
    drawLine(color, Offset(w * 0.78f, h * 0.3f), Offset(w * 0.5f, h * 0.7f), s.width, StrokeCap.Round)
}

/**
 * The big amount with a blinking caret (5 x 56 at size 72, scaled with [fontSize]).
 * [text] arrives already formatted. Display numbers clamp the system font scale at 1.15 and shrink for long values.
 */
@Composable
fun HeroAmount(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: Float = 72f,
    color: Color = BrickColors.White,
    showCaret: Boolean = true,
) {
    val type = LocalBrickType.current
    val reduce = LocalReduceMotion.current
    val blink by rememberInfiniteTransition(label = "caret").animateFloat(
        1f, 0f,
        infiniteRepeatable(keyframes { durationMillis = 1000; 1f at 0; 1f at 499; 0f at 500; 0f at 999 }, RepeatMode.Restart),
        label = "caret",
    )
    val density = LocalDensity.current
    val fit = (9f / text.length.coerceAtLeast(9)).coerceAtLeast(0.5f)
    val px = fontSize * fit
    CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale.coerceAtMost(1.15f))) {
        Row(modifier.semantics { contentDescription = text }, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text(
                text,
                style = with(type) { hero72.copy(fontSize = px.sp, color = color).onWorld(hero = true) },
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.clearAndSetSemantics { },
            )
            if (showCaret) {
                Box(
                    Modifier
                        .width((5f * px / 72f).dp)
                        .height((56f * px / 72f).dp)
                        .drawBehind {
                            val a = if (reduce) 1f else blink
                            drawRoundRect(color.copy(alpha = a), cornerRadius = CornerRadius(size.width / 2))
                        },
                )
            }
        }
    }
}
