package ai.freeapp.bricktown.settings

import ai.freeapp.bricktown.core.ui.AppIcons
import ai.freeapp.bricktown.designsystem.BrickColors
import ai.freeapp.bricktown.designsystem.ChunkyEdge
import ai.freeapp.bricktown.designsystem.ChunkySurface
import ai.freeapp.bricktown.designsystem.Dimens
import ai.freeapp.bricktown.designsystem.LocalBrickType
import ai.freeapp.bricktown.designsystem.Toggle
import ai.freeapp.bricktown.designsystem.onWorld
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight

/**
 * The few building blocks Settings, Manage towers, Invite and Settle up share:
 * the round Back header, the white section label, the white group card and its
 * rows. Internal to the module so each screen reads like the handoff.
 */
@Composable
internal fun RoundBackHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    end: @Composable RowScope.() -> Unit = {},
) {
    val type = LocalBrickType.current
    // Only above: the frames put the first card one column gap under the disc.
    Row(modifier.fillMaxWidth().padding(top = Dimens.space8), verticalAlignment = Alignment.CenterVertically) {
        // The face sits on the content's side; touch still reaches 48 dp around it.
        ChunkySurface(
            Modifier.size(width = 40.dp, height = 44.dp),
            edgeWidth = ChunkyEdge.Chip,
            radius = 20.dp,
            onClick = onBack,
            contentDescription = "Back",
            reserveTouchBox = false,
        ) {
            Icon(AppIcons.ChevronBack, contentDescription = null, tint = BrickColors.LinkBlue, modifier = Modifier.align(Alignment.Center).size(18.dp))
        }
        Text(
            title,
            style = type.title18.onWorld().copy(color = BrickColors.White),
            modifier = Modifier.weight(1f).padding(start = 10.dp),
        )
        end()
    }
}

/** 6a's labels are 11 sp capitals; 6g's ("New members can", "Pending") are 12 sp as written ([caps] false). */
@Composable
internal fun SectionLabel(text: String, modifier: Modifier = Modifier, caps: Boolean = true) {
    val type = LocalBrickType.current
    Text(
        if (caps) text.uppercase() else text,
        style = if (caps) {
            type.label11.onWorld().copy(color = BrickColors.White, letterSpacing = 0.08.em)
        } else {
            type.small12.onWorld().copy(color = BrickColors.White, fontWeight = FontWeight.Black)
        },
        modifier = modifier.padding(start = 6.dp, end = 6.dp, top = 4.dp),
    )
}

@Composable
internal fun GroupCard(
    modifier: Modifier = Modifier,
    edge: Color = BrickColors.CardEdge,
    content: @Composable ColumnScope.() -> Unit,
) {
    ChunkySurface(modifier.fillMaxWidth(), edge = edge, edgeWidth = ChunkyEdge.Primary, radius = 22.dp) {
        Column(Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
internal fun RowDivider(color: Color = BrickColors.SoftFillAlt) {
    Box(Modifier.fillMaxWidth().height(2.dp).background(color))
}

/** One row of a group: a title with an optional subtitle, then a value, a chevron or a custom trailing slot. */
@Composable
internal fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
    titleColor: Color = BrickColors.Ink,
    valueColor: Color = BrickColors.Ink,
    chevron: Boolean = false,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val type = LocalBrickType.current
    val tap = if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier
    Row(
        modifier.fillMaxWidth().then(tap).padding(horizontal = Dimens.space16, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.space12),
    ) {
        if (leading != null) leading()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = type.body14.copy(color = titleColor, fontWeight = FontWeight.Black))
            if (subtitle != null) Text(subtitle, style = type.small12.copy(color = BrickColors.Muted, fontSize = 11.sp))
        }
        if (value != null) {
            Text(value, style = type.row17.copy(color = valueColor, fontSize = 18.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (trailing != null) trailing()
        if (chevron) RowChevron()
    }
}

/** The grey row chevron of the handoff's list rows. */
@Composable
internal fun RowChevron() {
    Icon(AppIcons.ChevronForward, contentDescription = null, tint = BrickColors.Disabled, modifier = Modifier.size(16.dp))
}

@Composable
internal fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
) {
    SettingsRow(
        title = title,
        subtitle = subtitle,
        modifier = modifier,
        leading = leading,
        onClick = { onCheckedChange(!checked) },
        trailing = { Toggle(checked, onCheckedChange, contentDescription = title, height = SettingsToggleHeight) },
    )
}

/** 6a's list switches are 26 dp tall; 6g's and the brick form's keep the default 24. */
internal val SettingsToggleHeight = 26.dp

/** The town card's age (6a, "82 days"). The day the town is made reads "1 day", never "0 days". */
internal fun townAgeLabel(days: Int): String = if (days <= 1) "1 day" else "$days days"
