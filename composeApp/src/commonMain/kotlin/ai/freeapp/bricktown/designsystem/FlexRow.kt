package ai.freeapp.bricktown.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Dp
import kotlin.math.roundToInt

/**
 * The handoff's `display:flex` row of `flex:<weight>` children that each carry [padding] on both sides. A browser
 * gives every child its own padding first and shares only what is left by [weights], so a 1:2 pair is wider than
 * a third on the small side; `Modifier.weight` would split the whole width instead. Children sit [gap] apart,
 * top aligned, and the row is as tall as its tallest child.
 */
@Composable
fun FlexRow(
    weights: List<Float>,
    padding: Dp,
    gap: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(content, modifier) { measurables, constraints ->
        require(measurables.size == weights.size) { "FlexRow has ${weights.size} weights for ${measurables.size} children" }
        require(constraints.hasBoundedWidth) { "FlexRow needs a bounded width" }
        val gapPx = gap.roundToPx()
        val basis = 2 * padding.toPx()
        val room = constraints.maxWidth - gapPx * (measurables.size - 1)
        val free = (room - basis * measurables.size).coerceAtLeast(0f)
        val total = weights.sum()
        var given = 0
        val widths = weights.mapIndexed { i, w ->
            // The last child takes the rounding remainder, so the row always ends on the parent's side.
            val width = if (i == weights.lastIndex) room - given else (basis + free * w / total).roundToInt()
            width.coerceAtLeast(0).also { given += it }
        }
        val placeables = measurables.mapIndexed { i, m ->
            m.measure(constraints.copy(minWidth = widths[i], maxWidth = widths[i], minHeight = 0))
        }
        val height = (placeables.maxOfOrNull { it.height } ?: 0).coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(constraints.maxWidth, height) {
            var x = 0
            placeables.forEach { p ->
                p.place(x, 0)
                x += p.width + gapPx
            }
        }
    }
}
