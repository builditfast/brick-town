package ai.freeapp.bricktown.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color

/**
 * Segmented tab row whose selector slides between tabs.
 *
 * The selector is drawn once in `drawBehind` instead of being a composable
 * that moves: no extra layout node, no overdraw under the labels, and the
 * slide animation stays in the draw phase.
 *
 * The [tab] slot receives the tint already computed for its state, so callers
 * render an icon or a label without re-deriving selection colors.
 */
@Composable
fun AnimatedTabRow(
    titles: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    trackColor: Color = MaterialTheme.colors.onSurface.copy(alpha = 0.08f),
    selectorColor: Color = MaterialTheme.colors.primary,
    selectedTint: Color = MaterialTheme.colors.onPrimary,
    unselectedTint: Color = MaterialTheme.colors.onSurface.copy(alpha = 0.70f),
    tab: @Composable (index: Int, tint: Color) -> Unit = { index, tint ->
        Text(
            text = titles[index],
            color = tint,
            style = MaterialTheme.typography.button,
        )
    },
) {
    if (titles.isEmpty()) return

    val selectorPosition by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = tween(durationMillis = 250),
        label = "tab-selector",
    )

    Row(
        modifier = modifier
            .height(Dimens.tabRowHeight)
            .clip(RoundedCornerShape(Radius.pill))
            .background(trackColor)
            .drawBehind {
                // Every tab takes weight(1f) below, so tab width is a constant
                // fraction of the row and the selector offset is a multiple of
                // it. Tabs of differing widths would need measured offsets.
                val tabWidth = size.width / titles.size
                val inset = Dimens.space4.toPx()
                drawRoundRect(
                    color = selectorColor,
                    topLeft = Offset(selectorPosition * tabWidth + inset, inset),
                    size = Size(tabWidth - inset * 2, size.height - inset * 2),
                    cornerRadius = CornerRadius(Radius.pill.toPx()),
                )
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        titles.indices.forEach { index ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(Radius.pill))
                    .clickable { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                tab(index, if (index == selectedIndex) selectedTint else unselectedTint)
            }
        }
    }
}
