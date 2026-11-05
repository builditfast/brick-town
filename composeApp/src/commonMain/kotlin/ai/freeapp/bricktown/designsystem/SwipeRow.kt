package ai.freeapp.bricktown.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import kotlinx.coroutines.launch
import kotlin.math.abs

object SwipeRowSpec {
    const val DeleteAtDp = 80
    const val MaxDp = 130
    const val TapSlopDp = 6
}

/**
 * Swipe left to reveal a 130 dp delete area; releasing past 80 dp calls [onDelete]. Less than 6 dp of
 * movement is a tap and calls [onClick]. Screen readers get a Delete custom action instead of the drag.
 */
@Composable
fun SwipeRow(
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val type = LocalBrickType.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    val click by rememberUpdatedState(onClick)
    val delete by rememberUpdatedState(onDelete)
    val maxPx = with(density) { SwipeRowSpec.MaxDp.dp.toPx() }
    val deletePx = with(density) { SwipeRowSpec.DeleteAtDp.dp.toPx() }
    val slopPx = with(density) { SwipeRowSpec.TapSlopDp.dp.toPx() }
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (-offset.value >= deletePx) BrickColors.Negative else BrickColors.DeleteArea, shape),
    ) {
        Row(
            Modifier.matchParentSize().width(SwipeRowSpec.MaxDp.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(Modifier.width(SwipeRowSpec.MaxDp.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(18.dp)) {
                    val u = size.width / 24f
                    val stroke = Stroke(2.6f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    val path = Path().apply {
                        moveTo(3f * u, 6f * u); lineTo(21f * u, 6f * u)
                        moveTo(8f * u, 6f * u); lineTo(8f * u, 4f * u); lineTo(16f * u, 4f * u); lineTo(16f * u, 6f * u)
                        moveTo(6f * u, 6f * u); lineTo(7f * u, 20f * u); lineTo(17f * u, 20f * u); lineTo(18f * u, 6f * u)
                    }
                    drawPath(path, BrickColors.White, style = stroke)
                }
                Text("  Delete", style = type.small13.copy(color = BrickColors.White))
            }
        }
        Box(
            Modifier
                .graphicsLayer { translationX = offset.value }
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                    onClick { click(); true }
                    customActions = listOf(CustomAccessibilityAction("Delete") { delete(); true })
                }
                .pointerInput(maxPx, deletePx, slopPx) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var total = 0f
                        var dragging = false
                        var cancelled = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null || change.isConsumed && !dragging) {
                                cancelled = true
                                break
                            }
                            if (change.changedToUpIgnoreConsumed()) break
                            val dx = change.positionChange().x
                            total += dx
                            if (!dragging && abs(total) > slopPx) dragging = true
                            if (dragging) {
                                change.consume()
                                scope.launch { offset.snapTo((offset.value + dx).coerceIn(-maxPx, 0f)) }
                            }
                        }
                        when {
                            cancelled -> scope.launch { offset.animateTo(0f, tween(150)) }
                            !dragging -> click()
                            offset.value <= -deletePx -> {
                                delete()
                                scope.launch { offset.animateTo(0f, tween(150)) }
                            }
                            else -> scope.launch { offset.animateTo(0f, tween(150)) }
                        }
                    }
                },
        ) {
            content()
        }
    }
}
