package ai.freeapp.bricktown.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val InnerRingColor = BrickColors.TabMonth
private val InnerTrack = Color(0xFFFFF0F3)

/**
 * Two concentric rings on a 270 unit viewBox: the outer (r 110) is this month against budget, the inner
 * (r 72) last month on the same day. Fractions are 0..1; the center slot holds the numbers.
 */
@Composable
fun Rings(
    outer: Float,
    inner: Float,
    contentDescription: String,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val reduce = LocalReduceMotion.current
    val spec = tween<Float>(if (reduce) 0 else 900, easing = EaseOutCubic)
    val o by animateFloatAsState(outer.coerceIn(0f, 1f), spec, label = "outer")
    val i by animateFloatAsState(inner.coerceIn(0f, 1f), spec, label = "inner")
    Box(modifier.aspectRatio(1f).semantics { this.contentDescription = contentDescription }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val u = size.width / 270f
            val stroke = 26f * u
            fun ring(r: Float, track: Color, color: Color, fraction: Float) {
                val topLeft = Offset(135f * u - r * u, 135f * u - r * u)
                val diameter = Size(2 * r * u, 2 * r * u)
                drawArc(track, -90f, 360f, false, topLeft, diameter, style = Stroke(stroke))
                if (fraction > 0f) {
                    drawArc(color, -90f, 360f * fraction, false, topLeft, diameter, style = Stroke(stroke, cap = StrokeCap.Round))
                }
            }
            ring(110f, BrickColors.SoftSky, BrickColors.YouBlue, o)
            ring(72f, InnerTrack, InnerRingColor, i)
        }
        content()
    }
}

private fun Modifier.matchParentSize(): Modifier = this.then(Modifier.fillMaxWidthAspect())

private fun Modifier.fillMaxWidthAspect(): Modifier = this.fillMaxWidth().aspectRatio(1f)

/** A rounded bar that fills from the left. [progress] is 0..1 and animates. */
@Composable
fun ProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    fill: Color = BrickColors.Green.fill,
    track: Color = BrickColors.SoftSky,
    height: Dp = 12.dp,
    contentDescription: String? = null,
) {
    val reduce = LocalReduceMotion.current
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), tween(if (reduce) 0 else 400, easing = EaseOutCubic), label = "bar")
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics { if (contentDescription != null) this.contentDescription = contentDescription },
    ) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(track, cornerRadius = r)
        if (p > 0f) drawRoundRect(fill, size = Size(size.width * p, size.height), cornerRadius = r)
    }
}
