package ai.freeapp.bricktown.history

import ai.freeapp.bricktown.designsystem.BottomSheet
import ai.freeapp.bricktown.designsystem.BrickColors
import ai.freeapp.bricktown.designsystem.ChunkyEdge
import ai.freeapp.bricktown.designsystem.ChunkySurface
import ai.freeapp.bricktown.designsystem.Dimens
import ai.freeapp.bricktown.designsystem.LocalBrickType
import ai.freeapp.bricktown.designsystem.World
import ai.freeapp.bricktown.designsystem.WorldKind
import ai.freeapp.bricktown.designsystem.bounceIn
import ai.freeapp.bricktown.trail.ArrowGlyph
import ai.freeapp.bricktown.trail.CloseGlyph
import ai.freeapp.bricktown.trail.CupGlyph
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.datetime.LocalDate

/** One callback per thing the screen can do; navigation callbacks share a word with their destination. */
@Immutable
class HistoryActions(
    val onQueryChange: (String) -> Unit,
    val onClearQuery: () -> Unit,
    val onOpenRecap: (weekStart: LocalDate) -> Unit,
    val onOpenMonths: () -> Unit,
    val onCloseMonths: () -> Unit,
    val onEditBrick: (expenseId: String) -> Unit,
)

@Composable
fun HistoryContent(state: HistoryUiState, actions: HistoryActions, modifier: Modifier = Modifier) {
    val type = LocalBrickType.current
    Box(modifier.fillMaxSize()) {
        World(WorldKind.Gold) {
            Column(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    state.title,
                    style = type.body15.copy(color = BrickColors.Ink),
                    modifier = Modifier.padding(top = Dimens.space12),
                )
                SearchRow(state.query, actions)
                LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.recap?.let { recap ->
                        item(key = "recap") { RecapCard(recap, actions, Modifier.padding(bottom = 4.dp)) }
                    }
                    if (state.trophies.isNotEmpty()) {
                        item(key = "months") { MonthsRow(state, actions) }
                    }
                    if (state.ready && state.rows.isEmpty()) {
                        item(key = "empty") {
                            Text(
                                if (state.query.isBlank()) "No bricks yet." else "No brick matches “${state.query.trim()}”.",
                                style = type.body14.copy(color = BrickColors.Ink, textAlign = TextAlign.Center),
                                modifier = Modifier.fillMaxWidth().padding(top = Dimens.space24),
                            )
                        }
                    }
                    items(state.rows, key = { it.id }) { row -> BrickRow(row, actions) }
                    item(key = "end") { Box(Modifier.height(Dimens.space16)) }
                }
            }
        }
        if (state.monthsOpen) {
            BottomSheet(onDismiss = actions.onCloseMonths) { MonthsSheet(state) }
        }
    }
}

@Composable
private fun SearchRow(query: String, actions: HistoryActions) {
    val type = LocalBrickType.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ChunkySurface(Modifier.weight(1f), edge = BrickColors.CardEdge, edgeWidth = ChunkyEdge.Chip, radius = 14.dp) {
            Box(Modifier.fillMaxWidth().height(46.dp).padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) {
                BasicTextField(
                    value = query,
                    onValueChange = actions.onQueryChange,
                    singleLine = true,
                    textStyle = type.body14.copy(color = BrickColors.Ink),
                    cursorBrush = SolidColor(BrickColors.Ink),
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Search bricks" },
                    decorationBox = { inner ->
                        if (query.isEmpty()) {
                            Text(
                                "Search bricks: Lidl, Wolt, Transport…",
                                style = type.body14.copy(color = BrickColors.Disabled),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        inner()
                    },
                )
            }
        }
        ChunkySurface(
            edge = BrickColors.CardEdge,
            edgeWidth = ChunkyEdge.Chip,
            radius = 14.dp,
            onClick = actions.onClearQuery,
            contentDescription = "Clear search",
        ) {
            Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) { CloseGlyph(BrickColors.Muted, 16.dp) }
        }
    }
}

@Composable
private fun RecapCard(recap: RecapCardUi, actions: HistoryActions, modifier: Modifier = Modifier) {
    val type = LocalBrickType.current
    ChunkySurface(
        modifier.fillMaxWidth().bounceIn(),
        edgeWidth = ChunkyEdge.Row,
        radius = 18.dp,
        onClick = { actions.onOpenRecap(recap.weekStart) },
        contentDescription = "Open week ${recap.weekNumber} recap",
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ChunkySurface(fill = BrickColors.YouBlue, edge = BrickColors.LinkBlue, edgeWidth = ChunkyEdge.Tiny, radius = 12.dp) {
                Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                    Text("W\n${recap.weekNumber}", style = type.small13.copy(color = BrickColors.White, textAlign = TextAlign.Center, lineHeight = 14.sp))
                }
            }
            Column(Modifier.weight(1f)) {
                Text("Week ${recap.weekNumber} recap is ready", style = type.body14.copy(color = BrickColors.Ink))
                Text(recap.summary, style = type.label11.copy(color = BrickColors.Muted, letterSpacing = 0.sp), maxLines = 2)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Open", style = type.small12.copy(color = BrickColors.LinkBlue))
                ArrowGlyph(BrickColors.LinkBlue, 14.dp)
            }
        }
    }
}

@Composable
private fun MonthsRow(state: HistoryUiState, actions: HistoryActions) {
    val type = LocalBrickType.current
    ChunkySurface(Modifier.fillMaxWidth(), edgeWidth = ChunkyEdge.Chip, radius = 16.dp, onClick = actions.onOpenMonths, contentDescription = "Months, ${state.monthsSubtitle}") {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            ChunkySurface(fill = BrickColors.Gold.fill, edge = BrickColors.Gold.edge, edgeWidth = ChunkyEdge.Tiny, radius = 10.dp) {
                Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) { CupGlyph(BrickColors.Ink, 22.dp) }
            }
            Column(Modifier.weight(1f)) {
                Text("Months", style = type.body14.copy(color = BrickColors.Ink))
                Text(state.monthsSubtitle, style = type.label11.copy(color = BrickColors.Muted, letterSpacing = 0.sp))
            }
            ArrowGlyph(BrickColors.Muted, 16.dp)
        }
    }
}

@Composable
private fun BrickRow(row: HistoryRowUi, actions: HistoryActions) {
    val type = LocalBrickType.current
    val pair = BrickColors.CategoryPairs[row.colorIndex.mod(BrickColors.CategoryPairs.size)]
    ChunkySurface(
        Modifier.fillMaxWidth(),
        edgeWidth = ChunkyEdge.Chip,
        radius = 16.dp,
        onClick = { actions.onEditBrick(row.id) },
        contentDescription = "${row.note}, ${row.amountText}${if (row.refund) ", refund" else ""}",
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).background(BrickColors.SoftFillAlt, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                Text(row.dateTile, style = type.label10.copy(color = BrickColors.Muted, letterSpacing = 0.sp, textAlign = TextAlign.Center, lineHeight = 11.sp))
            }
            Column(Modifier.weight(1f)) {
                Text(row.note, style = type.body14.copy(color = BrickColors.Ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).background(pair.fill, RoundedCornerShape(3.dp)))
                    Text(row.categoryName, style = type.label11.copy(color = BrickColors.Muted, letterSpacing = 0.sp), maxLines = 1)
                    if (row.monthly) Text("· Monthly", style = type.label11.copy(color = BrickColors.LinkBlue, letterSpacing = 0.sp))
                    if (row.refund) Text("· refund", style = type.label11.copy(color = BrickColors.Green.edge, letterSpacing = 0.sp))
                }
            }
            row.whoInitial?.let { initial ->
                Box(
                    Modifier.size(22.dp).background(BrickColors.MemberColors[row.whoColorIndex.mod(BrickColors.MemberColors.size)], CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Text(initial.take(1).uppercase(), style = type.label10.copy(color = BrickColors.White, letterSpacing = 0.sp)) }
            }
            Text(row.amountText, style = type.row17.copy(color = if (row.refund) BrickColors.Green.edge else BrickColors.Ink), maxLines = 1)
        }
    }
}

@Composable
private fun MonthsSheet(state: HistoryUiState) {
    val type = LocalBrickType.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Months", style = type.sheetTitle.copy(color = BrickColors.Ink))
        state.trophies.forEach { trophy ->
            ChunkySurface(
                Modifier.fillMaxWidth(),
                fill = BrickColors.SoftFill,
                edgeWidth = ChunkyEdge.Chip,
                radius = 16.dp,
                contentDescription = "${trophy.title}, ${trophy.spentText}, ${if (trophy.underBudget) "under budget" else "over budget"}",
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                        if (trophy.underBudget) CupGlyph(BrickColors.Gold.edge, 30.dp)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(trophy.title, style = type.body14.copy(color = BrickColors.Ink))
                        Text(trophy.goldDaysText, style = type.label11.copy(color = BrickColors.Muted, letterSpacing = 0.sp))
                    }
                    Text(trophy.spentText, style = type.small13.copy(color = BrickColors.Ink))
                }
            }
        }
    }
}
