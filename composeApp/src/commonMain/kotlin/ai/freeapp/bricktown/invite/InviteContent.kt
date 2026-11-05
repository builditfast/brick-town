package ai.freeapp.bricktown.invite

import androidx.compose.foundation.horizontalScroll
import ai.freeapp.bricktown.core.ui.AppIcons
import ai.freeapp.bricktown.designsystem.ButtonKind
import ai.freeapp.bricktown.designsystem.ButtonSize
import ai.freeapp.bricktown.designsystem.ChunkyButton
import ai.freeapp.bricktown.settings.SettingsToggleHeight
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import ai.freeapp.bricktown.designsystem.Avatar
import ai.freeapp.bricktown.designsystem.BrickColors
import ai.freeapp.bricktown.designsystem.ChunkyEdge
import ai.freeapp.bricktown.designsystem.ChunkySurface
import ai.freeapp.bricktown.designsystem.Dimens
import ai.freeapp.bricktown.designsystem.LocalBrickType
import ai.freeapp.bricktown.designsystem.QrCode
import ai.freeapp.bricktown.designsystem.SkyClouds
import ai.freeapp.bricktown.designsystem.Toast
import ai.freeapp.bricktown.designsystem.Toggle
import ai.freeapp.bricktown.designsystem.World
import ai.freeapp.bricktown.designsystem.WorldKind
import ai.freeapp.bricktown.designsystem.pretty
import ai.freeapp.bricktown.designsystem.pulseRing
import ai.freeapp.bricktown.settings.GroupCard
import ai.freeapp.bricktown.settings.RoundBackHeader
import ai.freeapp.bricktown.settings.RowDivider
import ai.freeapp.bricktown.settings.SectionLabel
import ai.freeapp.bricktown.settings.SettingsRow
import ai.freeapp.bricktown.settings.memberColor
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Immutable
class InviteActions(
    val onBack: () -> Unit = {},
    val onShareClick: () -> Unit = {},
    val onCopyClick: () -> Unit = {},
    val onQrToggle: () -> Unit = {},
    val onRetryClick: () -> Unit = {},
    val onCanEditChange: (Boolean) -> Unit = {},
    val onCanManageChange: (Boolean) -> Unit = {},
    val onRevokeClick: (String) -> Unit = {},
    val onMessageShown: () -> Unit = {},
)

/** Invite to the town (6g): link, share, QR, what new members can do, and who has not joined yet. */
@Composable
fun InviteContent(state: InviteUiState, actions: InviteActions, modifier: Modifier = Modifier) {
    val type = LocalBrickType.current
    Box(modifier.fillMaxSize()) {
        World(WorldKind.Sky, clouds = SkyClouds.Invite) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(Dimens.space12),
            ) {
                RoundBackHeader("Invite to your town", actions.onBack)
                ChunkySurface(Modifier.fillMaxWidth(), edgeWidth = ChunkyEdge.Primary, radius = 24.dp, edge = BrickColors.CardEdgeSky) {
                    // The pill and Share link are 100% wide plus their own 14 px padding, so they reach 4 dp from the
                    // card's sides while the words keep the card's 18.
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Dimens.space12),
                    ) {
                        Avatars(state)
                        Text(
                            if (state.full) "Your town is full" else "One town, shared bricks",
                            style = type.row22.copy(color = BrickColors.Ink, fontSize = 24.sp, lineHeight = 26.4.sp, textAlign = TextAlign.Center),
                            modifier = Modifier.padding(horizontal = 14.dp),
                        )
                        Text(
                            when {
                                // Both full and not-owner lines are ours: 6g draws only the owner's open invite.
                                state.full -> "A town holds two people, and yours already has both."
                                !state.isOwner -> "Only the owner of the town can invite someone."
                                else -> "Anyone with the link joins your town. They see the towers, add bricks, and mark theirs as shared for settling up."
                            }.pretty(),
                            style = type.small12.copy(color = BrickColors.Muted, lineHeight = 16.2.sp, textAlign = TextAlign.Center),
                            modifier = Modifier.padding(horizontal = 14.dp),
                        )
                        if (!state.full && state.isOwner) LinkBlock(state, actions)
                    }
                }
                if (!state.full && state.isOwner) {
                    SectionLabel("New members can", caps = false)
                    GroupCard(edge = BrickColors.CardEdgeSky) {
                        SettingsRow(
                            title = "Add bricks",
                            // The handoff's line has a dash here; the house bans it, so a comma takes its place.
                            subtitle = "Always on, that's the point",
                            trailing = {
                                Box(Modifier.alpha(0.6f).clearAndSetSemantics { }) {
                                    Toggle(true, {}, contentDescription = "Add bricks, always on", height = SettingsToggleHeight)
                                }
                            },
                        )
                        RowDivider(BrickColors.SoftSky)
                        SettingsRow(
                            title = "Edit and delete any brick",
                            subtitle = "Off = only their own",
                            onClick = { actions.onCanEditChange(!state.canEdit) },
                            trailing = {
                                Toggle(state.canEdit, actions.onCanEditChange, contentDescription = "New members can edit and delete any brick", height = SettingsToggleHeight)
                            },
                        )
                        RowDivider(BrickColors.SoftSky)
                        SettingsRow(
                            title = "Change budget, towers and caps",
                            subtitle = "Owner only by default",
                            onClick = { actions.onCanManageChange(!state.canManage) },
                            trailing = {
                                Toggle(state.canManage, actions.onCanManageChange, contentDescription = "New members can change budget, towers and caps", height = SettingsToggleHeight)
                            },
                        )
                    }
                }
                if (state.pending.isNotEmpty() && state.isOwner) {
                    SectionLabel("Pending", caps = false)
                    state.pending.forEach { invite -> PendingCard(invite, actions) }
                }
                Box(Modifier.size(Dimens.space24))
            }
        }
        val message = state.message
        if (message != null) {
            Box(Modifier.align(Alignment.BottomCenter).padding(Dimens.space16)) { Toast(message, onTimeout = actions.onMessageShown) }
        }
    }
}

/** One invite nobody has used yet. A code has no name until someone joins, so the disc shows "?" (ours). */
@Composable
private fun PendingCard(invite: PendingInvite, actions: InviteActions) {
    val type = LocalBrickType.current
    ChunkySurface(Modifier.fillMaxWidth(), edge = BrickColors.CardEdgeSky, edgeWidth = ChunkyEdge.Row, radius = 18.dp) {
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.space12),
        ) {
            DashedAvatar(36.dp, "?", BrickColors.Disabled)
            Column(Modifier.weight(1f)) {
                Text(invite.code, style = type.body14.copy(color = BrickColors.Ink, fontWeight = FontWeight.Black))
                Text(invite.age, style = type.small12.copy(color = BrickColors.Muted, fontSize = 11.sp))
            }
            Text(
                "Revoke",
                style = type.small12.copy(color = BrickColors.Negative, fontWeight = FontWeight.Black),
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(role = Role.Button) { actions.onRevokeClick(invite.code) }
                    // 14 dp to the card's edge, as the frame's padding; the rest widens the tap target.
                    .padding(start = 8.dp, end = 14.dp, top = 12.dp, bottom = 12.dp)
                    .semantics { contentDescription = "Revoke invite ${invite.code}" },
            )
        }
    }
}

/** Members overlap by 12 dp in 4 dp white rings over a sky shadow; the open seat pulses gold (6g). */
@Composable
private fun Avatars(state: InviteUiState) {
    Row(
        // A scroll clips sideways; 18 dp each side leaves room for the pulse ring and keeps the row centred.
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy((-12).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        state.members.forEach { member ->
            Avatar(member.initial, memberColor(member.colorIndex), size = 60.dp, ring = 4.dp, edge = BrickColors.CardEdgeSky, textSize = 18.sp)
        }
        if (!state.full) {
            DashedAvatar(58.dp, null, BrickColors.SoftSkyEdge, ring = 3.dp, modifier = Modifier.pulseRing())
        }
    }
}

/** A white disc in a dashed ring: the open seat ([label] null draws 6g's blue plus) or an unknown member. */
@Composable
private fun DashedAvatar(size: Dp, label: String?, ringColor: Color, ring: Dp = 2.dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .background(BrickColors.White, CircleShape)
            .drawBehind {
                val stroke = ring.toPx()
                drawCircle(
                    ringColor,
                    radius = this.size.minDimension / 2 - stroke / 2,
                    style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        if (label == null) {
            Icon(AppIcons.Plus, contentDescription = null, tint = BrickColors.LinkBlue, modifier = Modifier.size(18.dp))
        } else {
            Text(label, style = LocalBrickType.current.title18.copy(color = BrickColors.Disabled))
        }
    }
}

@Composable
private fun LinkBlock(state: InviteUiState, actions: InviteActions) {
    val type = LocalBrickType.current
    val link = state.link
    Row(
        Modifier.fillMaxWidth().background(BrickColors.SoftSky, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            link?.substringAfter("://") ?: if (state.creating) "Making your link..." else "No link yet",
            style = type.small13.copy(color = if (link != null) BrickColors.LinkBlue else BrickColors.Muted, fontWeight = FontWeight.Black),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (link != null && state.expires.isNotEmpty()) {
            Text(state.expires, style = type.small12.copy(color = BrickColors.Muted, fontSize = 11.sp, fontWeight = FontWeight.Black))
        }
    }
    if (link == null && !state.creating) {
        ChunkyButton("Try again", actions.onRetryClick, Modifier.fillMaxWidth(), kind = ButtonKind.Sky, size = ButtonSize.Compact)
    } else {
        ChunkyButton(
            "Share link",
            actions.onShareClick,
            Modifier.fillMaxWidth(),
            kind = ButtonKind.Sky,
            enabled = link != null,
            size = ButtonSize.Compact,
            leading = { Icon(AppIcons.Share, contentDescription = null, tint = BrickColors.White, modifier = Modifier.size(18.dp)) },
        )
        val linkStyle = type.small12.copy(color = BrickColors.LinkBlue, fontWeight = FontWeight.Black)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Copy",
                style = linkStyle,
                modifier = Modifier.clickable(role = Role.Button, enabled = link != null, onClick = actions.onCopyClick),
            )
            Text(" · ", style = linkStyle)
            Text(
                if (state.showQr) "Hide QR" else "Show QR",
                style = linkStyle,
                modifier = Modifier.clickable(role = Role.Button, enabled = link != null, onClick = actions.onQrToggle),
            )
        }
        if (state.showQr && link != null) QrCode(link, size = 180.dp, contentDescription = "QR code for the invite link")
    }
}
