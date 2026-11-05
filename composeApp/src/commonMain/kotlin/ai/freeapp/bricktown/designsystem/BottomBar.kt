package ai.freeapp.bricktown.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Immutable
/** Each tab's glyph and label take its own colour when active (README: pink, gold, sky, green). */
enum class BarTab(val label: String, val activeColor: Color) {
    Month("Month", BrickColors.TabMonth),
    History("History", BrickColors.Gold.fill),
    Compare("Compare", BrickColors.YouBlue),
    Trail("Trail", BrickColors.Green.fill),
}

/**
 * Two tabs, the gold add button, two tabs, spread like the handoff's `justify-content: space-around`.
 * [pulseAdd] rings the button while the month has no brick yet (6c). The white runs under the
 * navigation bar; the tabs sit above it.
 */
@Composable
fun BottomBar(
    selected: BarTab,
    onSelect: (BarTab) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
    pulseAdd: Boolean = false,
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(BrickColors.White)
            .drawBehind { drawRect(BrickColors.BarTopBorder, size = Size(size.width, 3.dp.toPx())) }
            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .padding(start = 12.dp, end = 12.dp, top = 13.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.Bottom,
    ) {
        TabItem(BarTab.Month, selected, onSelect)
        TabItem(BarTab.History, selected, onSelect)
        AddButton(onAdd, pulseAdd)
        TabItem(BarTab.Compare, selected, onSelect)
        TabItem(BarTab.Trail, selected, onSelect)
    }
}

@Composable
private fun TabItem(tab: BarTab, selected: BarTab, onSelect: (BarTab) -> Unit) {
    val type = LocalBrickType.current
    val active = tab == selected
    val color = if (active) tab.activeColor else BrickColors.Disabled
    Column(
        Modifier
            .width(60.dp)
            .background(if (active) BrickColors.TabActivePill else Color.Transparent, RoundedCornerShape(12.dp))
            .semantics { this.selected = active }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
            ) { onSelect(tab) }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Canvas(Modifier.size(24.dp)) { drawTabIcon(color, tab.ordinal) }
        Text(tab.label, style = type.label10.copy(color = color, letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified), maxLines = 1)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTabIcon(color: Color, variant: Int) {
    val u = size.width / 24f
    drawRoundRect(color, Offset(2f * u, 4f * u), Size(20f * u, 16f * u), CornerRadius(4f * u))
    val r = CornerRadius(1.5f * u)
    drawRoundRect(Color.White, Offset(4f * u, 13f * u), Size(7f * u, 5f * u), r)
    drawRoundRect(Color.White.copy(alpha = if (variant >= 1) 1f else 0.35f), Offset(13f * u, 13f * u), Size(7f * u, 5f * u), r)
    drawRoundRect(Color.White.copy(alpha = if (variant >= 2) 1f else 0.35f), Offset(8f * u, 6f * u), Size(8f * u, 5f * u), r)
}

private val AddFace = 62.dp
private val AddEdge = 6.dp
private val AddEdgePressed = 2.dp

/** The handoff's `margin-top: -30px`: the button keeps this much of its height out of the row. */
private val AddRise = 30.dp

/**
 * A 62 dp gold disc on a 6 dp edge. Its bottom lines up with the tabs; the [AddRise] it keeps out of the
 * row lifts its top to the bar's top border.
 */
@Composable
private fun AddButton(onClick: () -> Unit, pulse: Boolean) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val edge = if (pressed) AddEdgePressed else AddEdge
    Box(
        Modifier
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val rise = AddRise.roundToPx()
                layout(placeable.width, placeable.height - rise) { placeable.place(0, -rise) }
            }
            .size(width = AddFace, height = AddFace + AddEdge)
            .pulseRing(enabled = pulse)
            .semantics { contentDescription = "Add a brick" }
            .clickable(source, indication = null, role = Role.Button, onClick = onClick)
            .drawBehind {
                val r = AddFace.toPx() / 2f
                val edgeCenter = Offset(r, size.height - r)
                drawCircle(BrickColors.Gold.edge, r, edgeCenter)
                drawCircle(BrickColors.Gold.fill, r, edgeCenter.copy(y = edgeCenter.y - edge.toPx()))
            },
        contentAlignment = Alignment.Center,
    ) {
        // Centred on the face, which sits (edge - AddEdge / 2) above the middle of the box.
        Canvas(Modifier.size(30.dp).graphicsLayer { translationY = (AddEdge / 2 - edge).toPx() }) {
            val w = 3.5f * size.width / 24f
            drawLine(BrickColors.Ink, Offset(size.width * 5 / 24, size.height / 2), Offset(size.width * 19 / 24, size.height / 2), w, StrokeCap.Round)
            drawLine(BrickColors.Ink, Offset(size.width / 2, size.height * 5 / 24), Offset(size.width / 2, size.height * 19 / 24), w, StrokeCap.Round)
        }
    }
}
