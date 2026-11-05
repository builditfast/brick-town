package ai.freeapp.bricktown.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.random.Random

/** How long a toast stays before [Toast] calls its `onTimeout`. */
const val ToastMillis = 4500L

/**
 * A dark pill with an optional Undo. Hoisted: the caller shows it while it holds a message and clears it
 * in [onTimeout] or [onUndo]. A new [message] restarts the timer.
 */
@Composable
fun Toast(
    message: String,
    onTimeout: () -> Unit,
    modifier: Modifier = Modifier,
    onUndo: (() -> Unit)? = null,
) {
    val type = LocalBrickType.current
    val timeout by rememberUpdatedState(onTimeout)
    LaunchedEffect(message) {
        delay(ToastMillis)
        timeout()
    }
    val rise = remember { Animatable(0f) }
    val reduce = LocalReduceMotion.current
    LaunchedEffect(reduce) { if (reduce) rise.snapTo(1f) else rise.animateTo(1f, tween(350, easing = EaseOutCubic)) }
    ChunkySurface(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = rise.value
                translationY = (1f - rise.value) * 24.dp.toPx()
            }
            .semantics { liveRegion = LiveRegionMode.Polite },
        fill = BrickColors.Toast.fill,
        edge = BrickColors.Toast.edge,
        edgeWidth = ChunkyEdge.Row,
        radius = 16.dp,
    ) {
        Row(Modifier.padding(start = 14.dp, end = 8.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(message, style = type.small13.copy(color = BrickColors.White), modifier = Modifier.weight(1f).padding(vertical = 8.dp))
            if (onUndo != null) {
                ChunkySurface(
                    fill = BrickColors.Gold.fill,
                    edge = BrickColors.Gold.edge,
                    edgeWidth = ChunkyEdge.Tiny,
                    radius = 10.dp,
                    onClick = onUndo,
                    contentDescription = "Undo",
                ) {
                    Text("Undo", style = type.small12.copy(color = BrickColors.Ink), modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp))
                }
            }
        }
    }
}

/**
 * A white sheet with 30 dp top corners and a grabber, at most 78 % of the height, over a scrim.
 * Tapping the scrim or pressing Back calls [onDismiss]; the caller removes it from the tree.
 * The scrim covers the system bars and the white runs under the navigation bar and the
 * keyboard, so it belongs beside a [World], not inside its content.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun BottomSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val reduce = LocalReduceMotion.current
    val progress = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(reduce) { if (reduce) progress.snapTo(1f) else progress.animateTo(1f, tween(400, easing = EaseOutCubic)) }
    BackHandler(enabled = true, onBack = onDismiss)
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = progress.value }
                .background(BrickColors.Scrim)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClickLabel = "Close", onClick = onDismiss),
        )
        Column(
            modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .layout { measurable, constraints ->
                    val limited = constraints.copy(maxHeight = (constraints.maxHeight * 0.78f).toInt())
                    val placeable = measurable.measure(Constraints(limited.minWidth, limited.maxWidth, 0, limited.maxHeight))
                    layout(placeable.width, placeable.height) { placeable.placeWithLayer(0, 0) { translationY = (1f - progress.value) * placeable.height } }
                }
                .background(BrickColors.White, RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                .padding(top = 14.dp),
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(44.dp)
                    .height(5.dp)
                    .background(BrickColors.CardEdge, RoundedCornerShape(3.dp)),
            )
            Box(Modifier.height(12.dp))
            content()
        }
    }
}

private class ConfettiPiece(val x: Float, val size: Float, val color: androidx.compose.ui.graphics.Color, val delay: Float, val duration: Float, val spin: Float)

/** Number of squares in a burst. */
const val ConfettiCount = 22

/**
 * 22 squares of 8 to 14 dp fall 760 dp while spinning 720 degrees. Draws nothing under Reduce Motion.
 * [onFinished] fires once the last square lands. Replay by changing [burstKey].
 */
@Composable
fun Confetti(burstKey: Int, modifier: Modifier = Modifier, onFinished: () -> Unit = {}) {
    val reduce = LocalReduceMotion.current
    val finished by rememberUpdatedState(onFinished)
    val pieces = remember(burstKey) {
        val random = Random(burstKey)
        val palette = BrickColors.MemberColors + BrickColors.CategoryPairs.map { it.fill }
        List(ConfettiCount) {
            ConfettiPiece(
                x = random.nextFloat(), size = 8f + random.nextFloat() * 6f, color = palette[random.nextInt(palette.size)],
                delay = random.nextFloat() * 0.35f, duration = 0.65f + random.nextFloat() * 0.35f, spin = if (random.nextBoolean()) 720f else -720f,
            )
        }
    }
    val clock = remember(burstKey) { Animatable(0f) }
    LaunchedEffect(burstKey, reduce) {
        if (reduce) {
            finished()
        } else {
            clock.animateTo(1f, tween(2200, easing = LinearEasing))
            finished()
        }
    }
    if (reduce) return
    Canvas(modifier.fillMaxSize()) {
        val t = clock.value
        pieces.forEach { p ->
            val local = ((t - p.delay) / p.duration).coerceIn(0f, 1f)
            if (local <= 0f || local >= 1f) return@forEach
            val s = p.size.dp.toPx()
            val x = p.x * (size.width - s)
            val y = -s + local * 760.dp.toPx()
            rotate(p.spin * local, pivot = Offset(x + s / 2, y + s / 2)) {
                drawRoundRect(p.color, Offset(x, y), Size(s, s), CornerRadius(3.dp.toPx()), alpha = 1f - 0.3f * local * local)
            }
        }
    }
}
