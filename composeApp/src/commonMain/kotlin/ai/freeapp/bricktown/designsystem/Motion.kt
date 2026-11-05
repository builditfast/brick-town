package ai.freeapp.bricktown.designsystem

import ai.freeapp.bricktown.core.ui.platformReduceMotion
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/** True when the system asks for less motion: confetti, jump and bob switch off. */
val LocalReduceMotion = compositionLocalOf { false }

@Composable
fun ProvideReduceMotion(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalReduceMotion provides platformReduceMotion(), content = content)
}

internal val EaseOutCubic = CubicBezierEasing(0.215f, 0.61f, 0.355f, 1f)

/** CSS `ease-out`, the timing the handoff writes for pulseRing. */
private val CssEaseOut = CubicBezierEasing(0f, 0f, 0.58f, 1f)

private val PulseRingSpread = 18.dp
private const val PULSE_RING_MILLIS = 1400

/**
 * The handoff's pulseRing (`box-shadow: 0 0 0 0` to `0 0 0 18px`, gold from 80 % to nothing, 1.4 s
 * ease-out, forever): an oval of the element's own bounds that grows 18 dp and fades, drawn behind it.
 * Off when [enabled] is false and under Reduce Motion. The ring reaches 18 dp outside the bounds, so a
 * clipping parent must leave that much room.
 */
@Composable
fun Modifier.pulseRing(enabled: Boolean = true): Modifier {
    if (!enabled || LocalReduceMotion.current) return this
    val progress = rememberInfiniteTransition(label = "pulseRing").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(PULSE_RING_MILLIS, easing = CssEaseOut), RepeatMode.Restart),
        label = "pulseRing",
    )
    return drawBehind {
        val p = progress.value
        val spread = PulseRingSpread.toPx() * p
        drawOval(
            BrickColors.Gold.fill.copy(alpha = 0.8f * (1f - p)),
            topLeft = Offset(-spread, -spread),
            size = Size(size.width + 2 * spread, size.height + 2 * spread),
        )
    }
}

/**
 * Counts from 0 to [target] over [durationMillis] (ease-out cubic), driven by the frame clock.
 * Restarts from 0 when [target] changes. Returns the value as State so the read stays in a lambda.
 */
@Composable
fun rememberCountUp(target: Float, durationMillis: Int = 1200, reduceMotion: Boolean = LocalReduceMotion.current): State<Float> {
    val state = remember { mutableFloatStateOf(if (reduceMotion) target else 0f) }
    LaunchedEffect(target, reduceMotion) {
        if (reduceMotion || durationMillis <= 0) {
            state.floatValue = target
            return@LaunchedEffect
        }
        val start = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val t = ((now - start) / 1_000_000f / durationMillis).coerceIn(0f, 1f)
            val eased = 1f - (1f - t) * (1f - t) * (1f - t)
            state.floatValue = target * eased
            if (t >= 1f) break
        }
    }
    return state
}

/** scale .4 to 1.12 to 1 with a fade over [durationMillis], after [delayMillis]. */
@Composable
fun Modifier.bounceIn(delayMillis: Int = 0, durationMillis: Int = 550): Modifier {
    val reduce = LocalReduceMotion.current
    val progress = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(reduce) {
        if (reduce) progress.snapTo(1f) else progress.animateTo(1f, tween(durationMillis, delayMillis, EaseOutCubic))
    }
    return graphicsLayer {
        val p = progress.value
        val s = if (p < 0.6f) 0.4f + (1.12f - 0.4f) * (p / 0.6f) else 1.12f - 0.12f * ((p - 0.6f) / 0.4f)
        scaleX = s
        scaleY = s
        alpha = (p / 0.6f).coerceIn(0f, 1f)
        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
    }
}
