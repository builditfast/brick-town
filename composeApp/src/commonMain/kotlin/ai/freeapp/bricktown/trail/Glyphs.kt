package ai.freeapp.bricktown.trail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Small glyphs of the handoff, drawn on a 24 unit grid. History, Recap and Rollover reuse them. */
@Composable
internal fun CheckGlyph(color: Color, size: Dp, modifier: Modifier = Modifier, strokeWidth: Float = 3.5f) {
    Canvas(modifier.size(size)) {
        val u = this.size.width / 24f
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(5f * u, 12.5f * u)
            lineTo(10f * u, 17.5f * u)
            lineTo(19f * u, 7f * u)
        }
        drawPath(path, color, style = Stroke(strokeWidth * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
internal fun CloseGlyph(color: Color, size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val u = this.size.width / 24f
        val s = Stroke(3.5f * u, cap = StrokeCap.Round)
        drawLine(color, Offset(18f * u, 6f * u), Offset(6f * u, 18f * u), s.width, StrokeCap.Round)
        drawLine(color, Offset(6f * u, 6f * u), Offset(18f * u, 18f * u), s.width, StrokeCap.Round)
    }
}

@Composable
internal fun LockGlyph(color: Color, size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val u = this.size.width / 24f
        drawArc(
            color, 180f, 180f, false, Offset(7f * u, 3f * u), Size(10f * u, 12f * u),
            style = Stroke(2.6f * u, cap = StrokeCap.Round),
        )
        drawRoundRect(color, Offset(4.5f * u, 10.5f * u), Size(15f * u, 11f * u), CornerRadius(3f * u))
    }
}

@Composable
internal fun ArrowGlyph(color: Color, size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val u = this.size.width / 24f
        val w = 3f * u
        drawLine(color, Offset(4f * u, 12f * u), Offset(19f * u, 12f * u), w, StrokeCap.Round)
        drawLine(color, Offset(13f * u, 6f * u), Offset(19f * u, 12f * u), w, StrokeCap.Round)
        drawLine(color, Offset(13f * u, 18f * u), Offset(19f * u, 12f * u), w, StrokeCap.Round)
    }
}

@Composable
internal fun CupGlyph(color: Color, size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val u = this.size.width / 24f
        val w = 2.4f * u
        drawRoundRect(color, Offset(6f * u, 3f * u), Size(12f * u, 10f * u), CornerRadius(3f * u))
        drawArc(color, 90f, 180f, false, Offset(1.5f * u, 5f * u), Size(7f * u, 6f * u), style = Stroke(w))
        drawArc(color, -90f, 180f, false, Offset(15.5f * u, 5f * u), Size(7f * u, 6f * u), style = Stroke(w))
        drawLine(color, Offset(12f * u, 13f * u), Offset(12f * u, 18f * u), w, StrokeCap.Round)
        drawRoundRect(color, Offset(7f * u, 18f * u), Size(10f * u, 3f * u), CornerRadius(1.5f * u))
    }
}
