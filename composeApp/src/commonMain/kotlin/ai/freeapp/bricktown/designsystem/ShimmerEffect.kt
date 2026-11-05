package ai.freeapp.bricktown.designsystem

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape

/**
 * Skeleton-loading sweep, applied to a sized placeholder:
 *
 *     Box(Modifier.fillMaxWidth().height(Dimens.skeletonLineHeight).shimmerEffect())
 *
 * The modifier paints nothing of its own beyond one gradient rect, so a
 * skeleton is a plain `Box` with a size, never a separate widget tree.
 *
 * The animated value is read inside the `drawBehind` lambda on purpose: that
 * keeps every frame in the draw phase, so an animating skeleton never
 * recomposes or remeasures the list it sits in.
 */
@Composable
fun Modifier.shimmerEffect(
    shape: Shape = RoundedCornerShape(Radius.md),
    baseColor: Color = MaterialTheme.colors.onSurface.copy(alpha = 0.10f),
    highlightColor: Color = MaterialTheme.colors.onSurface.copy(alpha = 0.24f),
    durationMillis: Int = 1_200,
): Modifier {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val sweepProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer-sweep",
    )

    return clip(shape).drawBehind {
        // The gradient is one viewport wide and travels from fully off the left
        // edge to fully off the right, so the highlight crosses exactly once.
        val startX = size.width * (sweepProgress * 2f - 1f)
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(baseColor, highlightColor, baseColor),
                start = Offset(startX, 0f),
                end = Offset(startX + size.width, size.height),
            ),
        )
    }
}
