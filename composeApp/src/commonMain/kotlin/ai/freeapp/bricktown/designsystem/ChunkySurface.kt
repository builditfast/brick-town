package ai.freeapp.bricktown.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.minimumInteractiveComponentSize
import androidx.compose.material.Text
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.LocalRippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Edge heights from the handoff: 6 primary, 5 rows, 4 chips and keys, 3 tiny. */
object ChunkyEdge {
    val Primary = 6.dp
    val Row = 5.dp
    val Chip = 4.dp
    val Tiny = 3.dp
}

/**
 * A flat fill with a solid darker edge under it instead of a shadow. When [onClick] is set the face sinks
 * by (edge - 1) dp while pressed and the edge shrinks to 1 dp.
 *
 * The layout reserves [edge] below the face, so pressing never moves neighbours. A clickable surface also
 * takes a 48 dp box unless [reserveTouchBox] is false; then it keeps its drawn size and Compose still widens
 * its touch bounds to 48 dp, as it does for [Toggle].
 */
@Composable
fun ChunkySurface(
    modifier: Modifier = Modifier,
    fill: Color = BrickColors.White,
    edge: Color = BrickColors.CardEdge,
    edgeWidth: Dp = ChunkyEdge.Row,
    radius: Dp = 18.dp,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    contentDescription: String? = null,
    role: Role = Role.Button,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    pressable: Boolean = onClick != null && enabled,
    reserveTouchBox: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val pressed by interactionSource.collectIsPressedAsState()
    val sink = if (pressable && pressed) edgeWidth - 1.dp else 0.dp
    val sinkAnimated by animateDpAsState(sink, tween(60))
    val shape = RoundedCornerShape(radius)
    val semanticsModifier = Modifier.semantics {
        if (contentDescription != null) this.contentDescription = contentDescription
        if (onClick != null) this.role = role
        if (onClick != null && !enabled) disabled()
    }
    val clickModifier = if (onClick != null) {
        (if (reserveTouchBox) Modifier.minimumInteractiveComponentSize() else Modifier)
            .clickable(interactionSource, indication = null, enabled = enabled, role = role, onClick = onClick)
    } else {
        Modifier
    }
    Box(
        modifier
            .then(clickModifier)
            .then(semanticsModifier)
            .padding(bottom = edgeWidth)
            .drawBehind {
                val r = CornerRadius(radius.toPx())
                drawRoundRect(edge, Offset(0f, edgeWidth.toPx()), Size(size.width, size.height), r)
            },
        // The edge is drawn at the size the caller gave; the face must cover at least that much.
        propagateMinConstraints = true,
    ) {
        Box(
            Modifier
                .graphicsLayer { translationY = sinkAnimated.toPx() }
                .background(fill, shape),
            content = content,
        )
    }
}

@Immutable
enum class ButtonKind { Primary, Gold, Soft, Danger, Sky }

/**
 * The handoff's four button sizes. Large: radius 18, 6 dp edge, 16 sp, 16 dp padding.
 * Medium (5d's bottom pair): radius 18, 6 dp edge, 15 sp, 14 dp padding.
 * Compact (in-card, 6b): radius 16, 5 dp edge, 15 sp, 14 dp padding.
 * Small (editor card, 6f): radius 14, 4 dp edge, 13 sp, 12 dp padding.
 */
@Immutable
enum class ButtonSize(val radius: Dp, val edge: Dp, val padding: Dp) {
    Large(18.dp, ChunkyEdge.Primary, 16.dp),
    Medium(18.dp, ChunkyEdge.Primary, 14.dp),
    Compact(16.dp, ChunkyEdge.Row, 14.dp),
    Small(14.dp, ChunkyEdge.Chip, 12.dp),
}

/**
 * The action button: a chunky face with a solid edge, Nunito 900. [leading] is an optional icon slot.
 * [textColor] overrides the kind's label colour (6f's Cancel is Soft with Muted text).
 */
@Composable
fun ChunkyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.Primary,
    enabled: Boolean = true,
    size: ButtonSize = ButtonSize.Large,
    textColor: Color? = null,
    leading: (@Composable RowScope.() -> Unit)? = null,
) {
    val type = LocalBrickType.current
    val (fill, edge, kindTextColor) = when {
        !enabled -> Triple(BrickColors.Disabled, BrickColors.DisabledEdge, BrickColors.White)
        kind == ButtonKind.Primary -> Triple(BrickColors.Green.fill, BrickColors.Green.edge, BrickColors.White)
        kind == ButtonKind.Gold -> Triple(BrickColors.Gold.fill, BrickColors.Gold.edge, BrickColors.Ink)
        kind == ButtonKind.Danger -> Triple(BrickColors.EatingOut.fill, BrickColors.EatingOut.edge, BrickColors.White)
        kind == ButtonKind.Sky -> Triple(BrickColors.YouBlue, BrickColors.LinkBlue, BrickColors.White)
        else -> Triple(BrickColors.SoftFill, BrickColors.CardEdge, BrickColors.Ink)
    }
    val source = remember { MutableInteractionSource() }
    val radius = size.radius
    val shape = RoundedCornerShape(radius)
    val textStyle = when (size) {
        ButtonSize.Large -> type.body16.copy(fontWeight = FontWeight.Black)
        ButtonSize.Medium, ButtonSize.Compact -> type.body15.copy(fontWeight = FontWeight.Black)
        ButtonSize.Small -> type.small13.copy(fontWeight = FontWeight.Black)
    }
    val label = if (enabled && textColor != null) textColor else kindTextColor
    CompositionLocalProvider(LocalRippleConfiguration provides null) {
        Button(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            interactionSource = source,
            elevation = null,
            shape = shape,
            colors = ButtonDefaults.buttonColors(
                backgroundColor = Color.Transparent,
                disabledBackgroundColor = Color.Transparent,
            ),
            contentPadding = PaddingValues(0.dp),
        ) {
            ChunkySurface(
                modifier = Modifier.fillMaxWidth(),
                fill = fill,
                edge = edge,
                edgeWidth = size.edge,
                radius = radius,
                interactionSource = source,
                pressable = enabled,
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = size.padding),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    leading?.invoke(this)
                    Text(text, style = textStyle.copy(color = label, textAlign = TextAlign.Center), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** A selectable pill. Unselected is soft with a 3 dp edge, selected takes [selectedColors]. */
@Composable
fun Chip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selectedColors: BrickPair = BrickColors.Gold,
    small: Boolean = false,
    unselectedFill: Color = BrickColors.SoftFill,
    selectedText: Color = BrickColors.Ink,
) {
    val type = LocalBrickType.current
    val fill by animateColorAsState(if (selected) selectedColors.fill else unselectedFill, tween(150))
    val edge by animateColorAsState(if (selected) selectedColors.edge else BrickColors.CardEdge, tween(150))
    ChunkySurface(
        modifier = modifier.semantics { this.selected = selected },
        fill = fill,
        edge = edge,
        edgeWidth = ChunkyEdge.Tiny,
        radius = if (small) 9.dp else 12.dp,
        onClick = onClick,
        role = Role.Tab,
        // The form's chip rows sit 6 dp apart as the handoff draws them (6e).
        reserveTouchBox = small,
    ) {
        Text(
            label,
            style = (if (small) type.label11 else type.small12.copy(fontWeight = FontWeight.Black))
                .copy(color = if (selected) selectedText else BrickColors.Ink),
            maxLines = 1,
            modifier = Modifier.padding(horizontal = if (small) 9.dp else 12.dp, vertical = if (small) 5.dp else 8.dp),
        )
    }
}

/**
 * A 46 dp switch, 24 dp tall (26 in the settings lists, 6a): a light track that turns green, and a white knob
 * 3 dp inside it. It takes no 48 dp box of its own, so a list row keeps the handoff's height: the row that
 * holds it passes the click, and Compose widens the switch's own touch bounds to 48 dp.
 */
@Composable
fun Toggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String,
    height: Dp = 24.dp,
) {
    val track by animateColorAsState(if (checked) BrickColors.Green.fill else BrickColors.CardEdge, tween(150))
    val inset = 3.dp
    val knob = height - inset * 2
    val knobX by animateDpAsState(if (checked) ToggleWidth - knob - inset else inset, tween(150))
    Box(
        modifier
            .clickable(role = Role.Switch, indication = null, interactionSource = remember { MutableInteractionSource() }) {
                onCheckedChange(!checked)
            }
            .semantics {
                this.contentDescription = contentDescription
                toggleableState = if (checked) ToggleableState.On else ToggleableState.Off
                stateDescription = if (checked) "On" else "Off"
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(ToggleWidth, height)
                .drawBehind { drawRoundRect(track, cornerRadius = CornerRadius((height / 2).toPx())) },
        ) {
            Box(
                Modifier
                    .graphicsLayer { translationX = knobX.toPx() }
                    .padding(top = inset)
                    .size(knob)
                    .drawBehind { drawCircle(BrickColors.White) },
            )
        }
    }
}

private val ToggleWidth = 46.dp
