package ai.freeapp.bricktown.settle

import ai.freeapp.bricktown.designsystem.Avatar
import ai.freeapp.bricktown.designsystem.BrickColors
import ai.freeapp.bricktown.designsystem.ButtonSize
import ai.freeapp.bricktown.designsystem.ChunkyButton
import ai.freeapp.bricktown.designsystem.ChunkyEdge
import ai.freeapp.bricktown.designsystem.ChunkySurface
import ai.freeapp.bricktown.designsystem.LocalBrickType
import ai.freeapp.bricktown.designsystem.SkyClouds
import ai.freeapp.bricktown.designsystem.Toast
import ai.freeapp.bricktown.designsystem.World
import ai.freeapp.bricktown.designsystem.WorldKind
import ai.freeapp.bricktown.designsystem.onWorld
import ai.freeapp.bricktown.settings.RoundBackHeader
import ai.freeapp.bricktown.settings.memberColor
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

@Immutable
class SettleUpActions(
    val onBack: () -> Unit = {},
    val onSettleClick: () -> Unit = {},
    val onPingClick: () -> Unit = {},
    val onMessageShown: () -> Unit = {},
)

/** Settle up (6b): one card with the balance and the button that clears it, then one card per shared brick. */
@Composable
fun SettleUpContent(state: SettleUpUiState, actions: SettleUpActions, modifier: Modifier = Modifier) {
    val type = LocalBrickType.current
    Box(modifier.fillMaxSize()) {
        World(WorldKind.Sky, clouds = SkyClouds.SettleUp) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 18.dp, end = 18.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RoundBackHeader(state.title, actions.onBack)
                if (!state.hasPartner) {
                    TopCard {
                        Text("Nobody to settle with yet", style = type.title18.copy(color = BrickColors.Ink))
                        Text(
                            "Invite a partner and shared bricks will split here.",
                            style = type.small13.copy(color = BrickColors.Muted),
                            textAlign = TextAlign.Center,
                        )
                    }
                } else {
                    Balance(state, actions)
                    BrickList("You paid · ${state.partnerName} owes half", state.youPaid)
                    BrickList("${state.partnerName} paid · you owe half", state.partnerPaid)
                    SharedHint(state.partnerName)
                }
            }
        }
        val message = state.message
        if (message != null) {
            Box(Modifier.align(Alignment.BottomCenter).padding(16.dp)) { Toast(message, onTimeout = actions.onMessageShown) }
        }
    }
}

@Composable
private fun TopCard(sides: Dp = 18.dp, bottom: Dp = 18.dp, content: @Composable ColumnScope.() -> Unit) {
    ChunkySurface(Modifier.fillMaxWidth(), edge = BrickColors.CardEdgeSky, edgeWidth = ChunkyEdge.Primary, radius = 24.dp) {
        Column(
            Modifier.fillMaxWidth().padding(start = sides, end = sides, top = 18.dp, bottom = bottom),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

@Composable
private fun Balance(state: SettleUpUiState, actions: SettleUpActions) {
    val type = LocalBrickType.current
    // The handoff's button is 100% wide plus its own 14 px padding, so it reaches 4 dp from the card's sides while the
    // text keeps the card's 18. With a reminder, the card's bottom padding is the link's touch target instead.
    TopCard(sides = 4.dp, bottom = if (state.canPing) 0.dp else 18.dp) {
        // 56 dp discs inside a 4 dp white ring; the gold bar tucks 2 dp under each ring.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy((-2).dp)) {
            Avatar(state.youInitial, memberColor(state.youColorIndex), size = 64.dp, ring = 4.dp, edge = BrickColors.CardEdgeSky, textSize = 20.sp)
            Box(Modifier.size(width = 40.dp, height = 4.dp).background(BrickColors.Gold.fill))
            Avatar(state.partnerInitial, memberColor(state.partnerColorIndex), size = 64.dp, ring = 4.dp, edge = BrickColors.CardEdgeSky, textSize = 20.sp)
        }
        Text(
            state.headline,
            style = type.headline30.copy(color = BrickColors.Ink),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 14.dp),
        )
        Text(
            state.summary,
            style = type.small12.copy(color = BrickColors.Muted),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 14.dp),
        )
        ChunkyButton(
            text = state.settleLabel,
            onClick = actions.onSettleClick,
            enabled = state.canSettle,
            size = ButtonSize.Compact,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
        if (state.canPing) {
            Text(
                "Remind ${state.partnerName}",
                style = type.small12.copy(color = BrickColors.LinkBlue, fontWeight = FontWeight.Black),
                modifier = Modifier.clickable(role = Role.Button, onClick = actions.onPingClick).padding(start = 16.dp, end = 16.dp, bottom = 18.dp),
            )
        }
    }
}

@Composable
private fun BrickList(label: String, rows: List<SettleBrick>) {
    if (rows.isEmpty()) return
    val type = LocalBrickType.current
    Text(
        label,
        style = type.small12.onWorld().copy(color = BrickColors.White, fontWeight = FontWeight.Black),
        modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 4.dp),
    )
    rows.forEach { row -> BrickCard(row) }
}

@Composable
private fun BrickCard(row: SettleBrick) {
    val type = LocalBrickType.current
    val tower = BrickColors.CategoryPairs[row.colorIndex.mod(BrickColors.CategoryPairs.size)]
    ChunkySurface(Modifier.fillMaxWidth(), edge = BrickColors.CardEdgeSky, edgeWidth = ChunkyEdge.Row, radius = 18.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(36.dp).background(tower.fill, RoundedCornerShape(10.dp)))
            Column(Modifier.weight(1f)) {
                Text(row.title, style = type.body14.copy(color = BrickColors.Ink, fontWeight = FontWeight.Black), maxLines = 1)
                Text(
                    row.meta,
                    style = type.label11.copy(color = BrickColors.Muted, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.sp),
                    maxLines = 1,
                )
            }
            row.partnerInitial?.let { initial ->
                Avatar(initial, memberColor(row.partnerColorIndex), size = 22.dp, ring = 0.dp, textSize = 10.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(row.amount, style = type.row17.copy(color = BrickColors.Ink), maxLines = 1)
                Text(
                    row.shareText,
                    style = type.label10.copy(color = if (row.shareOwedToYou) BrickColors.Green.edge else BrickColors.Negative, letterSpacing = 0.sp),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun SharedHint(partnerName: String) {
    val type = LocalBrickType.current
    val text = buildAnnotatedString {
        append("Mark any brick as ")
        withStyle(SpanStyle(fontWeight = FontWeight.Black)) { append("Shared") }
        append(" from its edit screen. $partnerName's own bricks stay theirs and don't count here.")
    }
    Text(
        text,
        style = type.small12.copy(color = BrickColors.White, lineHeight = 1.35.em),
        modifier = Modifier.fillMaxWidth().background(BrickColors.HintFill, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
    )
}
