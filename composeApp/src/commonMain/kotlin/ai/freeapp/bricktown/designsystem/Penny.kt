package ai.freeapp.bricktown.designsystem

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
enum class PennyMood(val describe: String) {
    Smile("Penny is smiling"),
    Neutral("Penny looks calm"),
    Frown("Penny is frowning"),
}

@Immutable
enum class PennyMotion { Still, Bob, Jump, Wobble }

/**
 * The coin mascot, drawn from primitives on the 44 x 44 viewBox of the handoff.
 * [motion] is ignored when the system asks for Reduce Motion.
 */
@Composable
fun Penny(
    mood: PennyMood,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    motion: PennyMotion = PennyMotion.Still,
) {
    val reduce = LocalReduceMotion.current
    val active = if (reduce) PennyMotion.Still else motion
    val transition = rememberInfiniteTransition(label = "penny")
    val bob by transition.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bob",
    )
    val wobble by transition.animateFloat(
        -1f, 1f,
        infiniteRepeatable(tween(600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "wobble",
    )
    // Two hops of 0.7 s each, replayed whenever motion switches to Jump.
    val jump = remember { Animatable(0f) }
    LaunchedEffect(active) {
        if (active == PennyMotion.Jump) {
            jump.snapTo(0f)
            repeat(2) {
                jump.animateTo(1f, tween(350, easing = FastOutSlowInEasing))
                jump.animateTo(0f, tween(350, easing = FastOutSlowInEasing))
            }
        } else {
            jump.snapTo(0f)
        }
    }
    val ink = BrickColors.Ink
    Canvas(
        modifier
            .size(size)
            .semantics { contentDescription = mood.describe }
            .graphicsLayer {
                when (active) {
                    PennyMotion.Bob -> translationY = -6f * bob * (this.size.width / 56f)
                    PennyMotion.Jump -> {
                        translationY = -26f * jump.value * (this.size.width / 56f)
                        rotationZ = -8f * jump.value
                        scaleY = 1f + 0.06f * jump.value
                        scaleX = 1f - 0.04f * jump.value
                    }
                    PennyMotion.Wobble -> rotationZ = 4f * wobble
                    PennyMotion.Still -> Unit
                }
            },
    ) {
        val u = this.size.width / 44f
        drawCircle(BrickColors.Gold.edge, radius = 21.5f * u, center = center)
        // The handoff strokes r 20 with width 3, so the rim runs from 18.5 to 21.5.
        drawCircle(BrickColors.Gold.fill, radius = 18.5f * u, center = center)
        drawCircle(BrickColors.GoldBottom, radius = 13f * u, center = center)
        drawCircle(ink, radius = 2.6f * u, center = Offset(16f * u, 20f * u))
        drawCircle(ink, radius = 2.6f * u, center = Offset(28f * u, 20f * u))
        val mouth = Path().apply {
            when (mood) {
                PennyMood.Smile -> { moveTo(14f * u, 26f * u); quadraticTo(22f * u, 33f * u, 30f * u, 26f * u) }
                PennyMood.Frown -> { moveTo(14f * u, 31f * u); quadraticTo(22f * u, 25f * u, 30f * u, 31f * u) }
                PennyMood.Neutral -> { moveTo(15f * u, 28f * u); lineTo(29f * u, 28f * u) }
            }
        }
        drawPath(mouth, ink, style = Stroke(width = 2.4f * u, cap = StrokeCap.Round))
    }
}
