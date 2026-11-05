package ai.freeapp.bricktown.month

import ai.freeapp.bricktown.brick.CloseDisc
import ai.freeapp.bricktown.brick.dashedOutline
import ai.freeapp.bricktown.designsystem.Avatar
import ai.freeapp.bricktown.designsystem.BottomSheet
import ai.freeapp.bricktown.designsystem.BrickColors
import ai.freeapp.bricktown.designsystem.ButtonKind
import ai.freeapp.bricktown.designsystem.Chip
import ai.freeapp.bricktown.designsystem.ChunkyButton
import ai.freeapp.bricktown.designsystem.ChunkyEdge
import ai.freeapp.bricktown.designsystem.ChunkySurface
import ai.freeapp.bricktown.designsystem.LocalBrickType
import ai.freeapp.bricktown.designsystem.Penny
import ai.freeapp.bricktown.designsystem.PennyMotion
import ai.freeapp.bricktown.designsystem.SwipeRow
import ai.freeapp.bricktown.designsystem.TowerChart
import ai.freeapp.bricktown.designsystem.TowerColumn
import ai.freeapp.bricktown.designsystem.World
import ai.freeapp.bricktown.designsystem.WorldKind
import ai.freeapp.bricktown.designsystem.onWorld
import ai.freeapp.bricktown.domain.CAP_CHOICES_CENTS
import ai.freeapp.bricktown.domain.Currency
import ai.freeapp.bricktown.domain.PennyMood
import ai.freeapp.bricktown.domain.formatWhole
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ai.freeapp.bricktown.designsystem.PennyMood as PennyFace
import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** What Month reports back; one callback per thing a user can do. */
class MonthActions(
    val onOpenSettings: () -> Unit,
    val onOpenTower: (Int) -> Unit,
    val onCloseTower: () -> Unit,
    val onCapChosen: (categoryId: String, capCents: Long?) -> Unit,
    val onEditBrick: (expenseId: String) -> Unit,
    val onDeleteBrick: (expenseId: String) -> Unit,
    val onAddBrickHere: (categoryId: String) -> Unit,
    val onOpenGhost: (seriesKey: String) -> Unit,
    val onCloseGhost: () -> Unit,
    val onConfirmGhost: () -> Unit,
    val onSkipGhost: () -> Unit,
    val onTowersPlayed: () -> Unit = {},
)

private fun PennyMood.face(): PennyFace = when (this) {
    PennyMood.SMILE -> PennyFace.Smile
    PennyMood.FROWN -> PennyFace.Frown
    PennyMood.NEUTRAL -> PennyFace.Neutral
}

/** The Month tab: header, spent card, the towers, and the sheets that open from them. */
@Composable
fun MonthContent(
    state: MonthUiState,
    actions: MonthActions,
    modifier: Modifier = Modifier,
    pennyJump: Boolean = false,
) {
    Box(modifier.fillMaxSize()) {
        World(WorldKind.Pink) {
            Column(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Header(state, actions, pennyJump)
                state.activity?.let { ActivityPill(it) }
                SpentCard(state)
                if (state.ready) {
                    Chart(state, actions)
                    if (state.fresh) GhostExplainer(state) else Hint("Tap a tower · dashed line = cap · striped bricks are over it")
                    if (state.fresh) Hint(state.freshHint)
                }
            }
        }
        state.openTower?.let { tower ->
            val ghost = state.openGhost
            if (ghost != null) GhostSheet(ghost, tower, actions) else TowerSheet(tower, state.currency, actions)
        }
    }
}

@Composable
private fun Header(state: MonthUiState, actions: MonthActions, pennyJump: Boolean) {
    val type = LocalBrickType.current
    Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ChunkySurface(
            fill = BrickColors.White,
            edge = BrickColors.CardEdge,
            edgeWidth = ChunkyEdge.Tiny,
            radius = 26.dp,
            onClick = actions.onOpenSettings,
            contentDescription = "Settings",
        ) {
            Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                Penny(state.mood.face(), size = 42.dp, motion = if (pennyJump) PennyMotion.Jump else PennyMotion.Still)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(state.monthLine, style = type.title18.copy(color = BrickColors.White).onWorld(), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(state.pennyLine, style = type.small12.copy(color = BrickColors.White).onWorld(), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Row(horizontalArrangement = Arrangement.spacedBy((-10).dp)) {
            state.members.take(3).forEach { m ->
                Avatar(m.initial, BrickColors.MemberColors[m.colorIndex % BrickColors.MemberColors.size])
            }
        }
    }
}

@Composable
private fun ActivityPill(text: String) {
    val type = LocalBrickType.current
    Row(
        Modifier.fillMaxWidth().background(BrickColors.White.copy(alpha = 0.25f), RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(12.dp).background(BrickColors.PartnerPurple, RoundedCornerShape(6.dp)))
        Text(text, style = type.small12.copy(color = BrickColors.White).onWorld(), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SpentCard(state: MonthUiState) {
    val type = LocalBrickType.current
    ChunkySurface(Modifier.fillMaxWidth(), fill = BrickColors.White, edge = BrickColors.CardEdge, edgeWidth = ChunkyEdge.Primary, radius = 26.dp) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text("SPENT", style = type.label11.copy(color = BrickColors.Muted))
                Text(state.spentText.ifEmpty { "€0" }, style = type.hero46.copy(color = BrickColors.Ink), maxLines = 1)
                Text(state.billsLine, style = type.label10.copy(color = BrickColors.Muted), maxLines = 2)
            }
            StatChip(state.todayAmountText, state.todayOfText, BrickColors.SoftSky, BrickColors.SoftSkyEdge, BrickColors.Green.edge, BrickColors.Muted)
            StatChip(state.monthLeftText, "month", BrickColors.Green.fill, BrickColors.Green.edge, BrickColors.White, BrickColors.White)
        }
    }
}

@Composable
private fun StatChip(
    amount: String,
    caption: String,
    fill: androidx.compose.ui.graphics.Color,
    edge: androidx.compose.ui.graphics.Color,
    amountColor: androidx.compose.ui.graphics.Color,
    captionColor: androidx.compose.ui.graphics.Color,
) {
    val type = LocalBrickType.current
    ChunkySurface(fill = fill, edge = edge, edgeWidth = ChunkyEdge.Row, radius = 16.dp) {
        Column(Modifier.width(84.dp).padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(amount, style = type.row17.copy(color = amountColor), maxLines = 1)
            Text(caption, style = type.label10.copy(color = captionColor, textAlign = TextAlign.Center))
        }
    }
}

@Composable
private fun Chart(state: MonthUiState, actions: MonthActions) {
    val columns = state.towers.map { t ->
        TowerColumn(
            label = t.label,
            colorIndex = t.colorIndex,
            bricks = t.bricks,
            capBricks = t.capBricks,
            overCapBricks = t.overCapBricks,
            ghostBricks = t.ghostBricks,
            dashedOutline = state.fresh,
            contentDescription = "${t.name}, ${t.bricks} bricks, ${t.totalText}",
        )
    }
    TowerChart(columns, state.brickHeight, actions.onOpenTower, Modifier.fillMaxWidth(), playIn = !state.towersPlayed, onPlayedIn = actions.onTowersPlayed)
}

@Composable
private fun Hint(text: String) {
    val type = LocalBrickType.current
    Text(text, style = type.small12.copy(color = BrickColors.White, textAlign = TextAlign.Center).onWorld(), modifier = Modifier.fillMaxWidth())
}

@Composable
private fun GhostExplainer(state: MonthUiState) {
    val type = LocalBrickType.current
    ChunkySurface(Modifier.fillMaxWidth(), fill = BrickColors.White, edge = BrickColors.CardEdge, edgeWidth = ChunkyEdge.Primary, radius = 22.dp) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            GhostStack()
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(state.ghostExplainerTitle, style = type.body14.copy(color = BrickColors.Ink, fontWeight = FontWeight.Black))
                Text(state.ghostExplainerBody, style = type.small12.copy(color = BrickColors.Muted, fontSize = 11.sp, lineHeight = 14.85.sp))
            }
        }
    }
}

/** A 14 x 44 pill of sky bricks, 6 dp each with 3 dp gaps counted from the bottom (the handoff's repeating gradient). */
@Composable
private fun GhostStack() {
    Canvas(Modifier.size(width = 14.dp, height = 44.dp).clip(RoundedCornerShape(7.dp))) {
        val brick = 6.dp.toPx()
        val step = 9.dp.toPx()
        var bottom = size.height
        while (bottom > 0f) {
            drawRect(BrickColors.Home.fill, Offset(0f, bottom - brick), Size(size.width, brick))
            bottom -= step
        }
    }
}

@Composable
private fun TowerSheet(tower: TowerUi, currency: Currency, actions: MonthActions) {
    val type = LocalBrickType.current
    val pair = BrickColors.CategoryPairs[tower.colorIndex % BrickColors.CategoryPairs.size]
    BottomSheet(onDismiss = actions.onCloseTower) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(44.dp).background(pair.fill, RoundedCornerShape(12.dp)))
                Column(Modifier.weight(1f)) {
                    Text("${tower.name} · ${tower.totalText}", style = type.sheetTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${tower.rows.size} ${if (tower.rows.size == 1) "brick" else "bricks"} · swipe left to delete · tap to edit",
                        style = type.small12.copy(color = BrickColors.Muted),
                    )
                }
                CloseDisc(actions.onCloseTower, size = 36.dp)
            }
            CapRow(tower, currency, actions)
            if (tower.ghosts.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    tower.ghosts.forEach { g -> GhostRow(g, actions) }
                }
            }
            LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(tower.rows, key = { it.id }) { row ->
                    SwipeRow(onClick = { actions.onEditBrick(row.id) }, onDelete = { actions.onDeleteBrick(row.id) }) { BrickRow(row) }
                }
            }
            ChunkyButton("+ Add a brick here", { actions.onAddBrickHere(tower.id) }, Modifier.fillMaxWidth().padding(bottom = 16.dp), kind = ButtonKind.Gold)
        }
    }
}

@Composable
private fun CapRow(tower: TowerUi, currency: Currency, actions: MonthActions) {
    val type = LocalBrickType.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val label = tower.capCents?.let { "Cap ${formatWhole(it, currency)}" + (tower.overText?.let { o -> " · " + o.replaceFirstChar { c -> c.lowercase() } } ?: "") } ?: "No cap"
        Text(label, style = type.small13.copy(color = BrickColors.Ink))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(CAP_CHOICES_CENTS, key = { it ?: -1L }) { cap ->
                Chip(
                    label = cap?.let { formatWhole(it, currency) } ?: "None",
                    selected = tower.capCents == cap,
                    onClick = { actions.onCapChosen(tower.id, cap) },
                    small = true,
                )
            }
        }
    }
}

@Composable
private fun BrickRow(row: BrickRowUi) {
    val type = LocalBrickType.current
    ChunkySurface(Modifier.fillMaxWidth(), fill = BrickColors.SoftFill, edge = BrickColors.CardEdge, edgeWidth = ChunkyEdge.Row, radius = 16.dp) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.background(BrickColors.White, RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 6.dp)) {
                Text(row.dateTile, style = type.label11.copy(color = BrickColors.Ink))
            }
            Text(row.note, style = type.small13.copy(color = BrickColors.Ink), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (row.monthly) Text("Monthly", style = type.label10.copy(color = BrickColors.LinkBlue))
            row.partnerInitial?.let { Avatar(it, BrickColors.MemberColors[row.partnerColorIndex % BrickColors.MemberColors.size], size = 24.dp, ring = 2.dp) }
            Text(row.amountText, style = type.row17.copy(color = if (row.refund) BrickColors.Green.fill else BrickColors.Ink), maxLines = 1)
        }
    }
}

@Composable
private fun GhostRow(ghost: GhostUi, actions: MonthActions) {
    val type = LocalBrickType.current
    Row(
        Modifier.fillMaxWidth().dashedOutline(BrickColors.Muted, radius = 16.dp).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(ghost.title, style = type.small13.copy(color = BrickColors.Ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(ghost.dueText, style = type.label10.copy(color = BrickColors.Muted))
        }
        Text(ghost.amountText, style = type.row17.copy(color = BrickColors.Muted))
        ChunkyButton("Review", { actions.onOpenGhost(ghost.seriesKey) }, kind = ButtonKind.Soft)
    }
}

@Composable
private fun GhostSheet(ghost: GhostUi, tower: TowerUi, actions: MonthActions) {
    val type = LocalBrickType.current
    BottomSheet(onDismiss = actions.onCloseGhost) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("${ghost.title} · ${ghost.amountText} · ${ghost.dueText}", style = type.sheetTitle)
            Text("A bill pencilled into ${tower.name}.", style = type.small13.copy(color = BrickColors.Muted))
            ChunkyButton("Confirm", actions.onConfirmGhost, Modifier.fillMaxWidth())
            ChunkyButton("Skip this month", actions.onSkipGhost, Modifier.fillMaxWidth(), kind = ButtonKind.Soft)
        }
    }
}
