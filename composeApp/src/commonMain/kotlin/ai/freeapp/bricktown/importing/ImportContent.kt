package ai.freeapp.bricktown.importing

import ai.freeapp.bricktown.designsystem.BrickColors
import ai.freeapp.bricktown.designsystem.ChunkyButton
import ai.freeapp.bricktown.designsystem.ChunkyEdge
import ai.freeapp.bricktown.designsystem.ChunkySurface
import ai.freeapp.bricktown.designsystem.Chip
import ai.freeapp.bricktown.designsystem.Dimens
import ai.freeapp.bricktown.designsystem.LocalBrickType
import ai.freeapp.bricktown.designsystem.LocalReduceMotion
import ai.freeapp.bricktown.designsystem.Penny
import ai.freeapp.bricktown.designsystem.PennyMood
import ai.freeapp.bricktown.designsystem.PennyMotion
import ai.freeapp.bricktown.designsystem.Toast
import ai.freeapp.bricktown.designsystem.World
import ai.freeapp.bricktown.designsystem.WorldKind
import ai.freeapp.bricktown.brick.CloseDisc
import ai.freeapp.bricktown.brick.dashedOutline
import ai.freeapp.bricktown.core.ui.AppIcons
import ai.freeapp.bricktown.designsystem.onWorld
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Immutable
class ImportActions(
    val onBack: () -> Unit = {},
    val onChooseClick: () -> Unit = {},
    val onRowClick: (Int) -> Unit = {},
    val onTowerClick: (Int, String) -> Unit = { _, _ -> },
    val onAddClick: () -> Unit = {},
    val onUndoClick: () -> Unit = {},
    val onUndoExpired: () -> Unit = {},
    val onTryAgainClick: () -> Unit = {},
    val onPhotoClick: () -> Unit = {},
    val onMessageShown: () -> Unit = {},
)

private val SKY_FILL = BrickColors.YouBlue
private val SKY_EDGE = BrickColors.LinkBlue

/** Import a statement (5b and 6d): pick a file, watch it read, review the rows, add them. */
@Composable
fun ImportContent(state: ImportUiState, actions: ImportActions, modifier: Modifier = Modifier) {
    val type = LocalBrickType.current
    Box(modifier.fillMaxSize()) {
        World(WorldKind.Sky) {
            // The frames pin the privacy line to the bottom (margin-top: auto) and let it scroll away under a long review.
            BoxWithConstraints(Modifier.fillMaxSize()) {
                Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = maxHeight).padding(horizontal = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(Dimens.space12),
                ) {
                    ImportHeader(actions.onBack)
                    when (state.stage) {
                        ImportStage.Pick -> PickCard(state.pdfOn, actions.onChooseClick)
                        ImportStage.Scanning -> ScanCard(state.fileName)
                        ImportStage.Review -> Review(state, actions)
                        ImportStage.Added -> AddedCard(state, actions)
                        ImportStage.Failed -> FailedCard(state, actions)
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        "CSV files are read on this phone. A PDF is sent to our server to be read and is not kept.",
                        style = type.label11.copy(color = BrickColors.White.copy(alpha = 0.9f), letterSpacing = 0.sp, fontWeight = FontWeight.ExtraBold),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(bottom = Dimens.space16),
                    )
                }
            }
        }
        val message = state.message
        if (message != null) {
            Box(Modifier.align(Alignment.BottomCenter).padding(Dimens.space16)) { Toast(message, onTimeout = actions.onMessageShown) }
        } else if (state.stage == ImportStage.Added && state.undoable > 0) {
            Box(Modifier.align(Alignment.BottomCenter).padding(Dimens.space16)) {
                Toast(
                    message = "Added ${state.undoable} ${if (state.undoable == 1) "brick" else "bricks"}",
                    onTimeout = actions.onUndoExpired,
                    onUndo = actions.onUndoClick,
                )
            }
        }
    }
}

/** Frames 4a, 5b and 6d: the title on the left, a white close disc on the right. */
@Composable
private fun ImportHeader(onClose: () -> Unit) {
    val type = LocalBrickType.current
    Row(
        Modifier.fillMaxWidth().padding(top = Dimens.space8),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Import a statement", style = type.body15.onWorld().copy(color = BrickColors.White, fontWeight = FontWeight.Black))
        // 44 dp in all: a 40 dp face over the 4 dp sky edge, as the frames draw it.
        CloseDisc(onClose, size = 44.dp, edge = BrickColors.CardEdgeSky)
    }
}

@Composable
private fun WhiteCard(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(Dimens.space16),
    content: @Composable () -> Unit,
) {
    ChunkySurface(modifier.fillMaxWidth(), edge = BrickColors.CardEdgeSky, edgeWidth = ChunkyEdge.Primary, radius = 22.dp) {
        Column(Modifier.fillMaxWidth().padding(padding), horizontalAlignment = Alignment.CenterHorizontally) { content() }
    }
}

@Composable
private fun PickCard(pdfOn: Boolean, onChoose: () -> Unit) {
    val type = LocalBrickType.current
    WhiteCard(padding = PaddingValues(18.dp)) {
        Column(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(BrickColors.DropZone)
                .dashedOutline(BrickColors.SoftSkyEdge, radius = 18.dp, width = 3.dp)
                .padding(horizontal = Dimens.space16, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Penny(PennyMood.Smile, size = 56.dp, motion = PennyMotion.Bob)
            Text(
                // (ours) with import.pdf off: the handoff's line offers a PDF the app would then refuse.
                if (pdfOn) "Drop a CSV or PDF here" else "Choose a CSV from your bank",
                style = type.body15.copy(color = BrickColors.Ink, fontWeight = FontWeight.Black),
                textAlign = TextAlign.Center,
            )
            Text(
                "Penny reads it, guesses towers and skips anything that's already a brick.",
                style = type.small12.copy(color = BrickColors.Muted),
                textAlign = TextAlign.Center,
            )
            // 4a and 5b: a compact sky button, radius 14 over a 4 dp edge, 14 sp.
            ChunkySurface(
                Modifier.padding(top = 4.dp),
                fill = SKY_FILL,
                edge = SKY_EDGE,
                edgeWidth = ChunkyEdge.Chip,
                radius = 14.dp,
                onClick = onChoose,
            ) {
                Text(
                    "Choose file",
                    style = type.body14.copy(color = BrickColors.White, fontWeight = FontWeight.Black),
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = Dimens.space12),
                )
            }
        }
        Text(
            // (ours) with import.pdf on: a PDF is read on our server, so "nothing leaves your phone" would be false.
            if (pdfOn) "Works with exports from any bank · CSVs stay on your phone" else "Works with exports from any bank · nothing leaves your phone",
            style = type.label11.copy(color = BrickColors.Muted, letterSpacing = 0.sp, fontWeight = FontWeight.ExtraBold),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 14.dp),
        )
    }
}

@Composable
private fun ScanCard(fileName: String) {
    val type = LocalBrickType.current
    val reduce = LocalReduceMotion.current
    val progress = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(reduce) {
        if (!reduce) progress.animateTo(1f, tween(SCAN_MILLIS.toInt(), easing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)))
    }
    WhiteCard {
        Column(Modifier.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Penny(PennyMood.Neutral, size = 64.dp, motion = PennyMotion.Wobble)
            Text("Reading $fileName…", style = type.body15.copy(color = BrickColors.Ink, fontWeight = FontWeight.Black), textAlign = TextAlign.Center)
            Box(Modifier.fillMaxWidth().height(12.dp).clip(CircleShape).background(BrickColors.SoftSky)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(progress.value).clip(CircleShape).background(SKY_FILL))
            }
        }
    }
}

@Composable
private fun Review(state: ImportUiState, actions: ImportActions) {
    val type = LocalBrickType.current
    ChunkySurface(Modifier.fillMaxWidth(), edge = BrickColors.CardEdgeSky, edgeWidth = ChunkyEdge.Row, radius = 18.dp) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Dimens.space16, vertical = Dimens.space12), verticalAlignment = Alignment.CenterVertically) {
            Penny(PennyMood.Smile, size = 36.dp)
            Text(
                state.summary,
                style = type.body14.copy(color = BrickColors.Ink, fontWeight = FontWeight.Black),
                modifier = Modifier.padding(start = 10.dp),
            )
        }
    }
    state.rows.forEach { row -> RowCard(row, state.towers, actions) }
    ChunkyButton(
        text = state.addLabel,
        onClick = actions.onAddClick,
        enabled = state.addCount > 0,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RowCard(row: ImportRowUi, towers: List<TowerChoice>, actions: ImportActions) {
    val type = LocalBrickType.current
    val skipped = row.status == RowStatus.Duplicate
    ChunkySurface(
        Modifier.fillMaxWidth().alpha(if (skipped) 0.55f else 1f),
        edge = BrickColors.CardEdgeSky,
        edgeWidth = ChunkyEdge.Row,
        radius = 18.dp,
        onClick = if (skipped) null else ({ actions.onRowClick(row.key) }),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = Dimens.space12), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(row.merchant, style = type.body14.copy(color = BrickColors.Ink, fontWeight = FontWeight.Black), maxLines = 1)
                    Text(row.meta, style = type.label11.copy(color = BrickColors.Muted))
                }
                Text(row.amount, style = type.title18.copy(color = BrickColors.Ink))
            }
            if (!skipped) {
                val shown = if (row.open || row.towerId == null) towers else towers.filter { it.id == row.towerId }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    shown.forEach { tower ->
                        Chip(
                            label = tower.name,
                            selected = tower.id == row.towerId,
                            onClick = { actions.onTowerClick(row.key, tower.id) },
                            selectedColors = BrickColors.CategoryPairs[tower.colorIndex % BrickColors.CategoryPairs.size],
                            small = true,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AddedCard(state: ImportUiState, actions: ImportActions) {
    val type = LocalBrickType.current
    WhiteCard {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Penny(PennyMood.Smile, size = 64.dp, motion = PennyMotion.Jump)
            Text(
                "Your towers just grew",
                style = type.title18.copy(color = BrickColors.Ink),
                textAlign = TextAlign.Center,
            )
            Text(
                "Every brick from the statement is in. Undo takes the whole import back out.",
                style = type.small13.copy(color = BrickColors.Muted),
                textAlign = TextAlign.Center,
            )
        }
    }
    ChunkyButton("Done", actions.onBack, Modifier.fillMaxWidth().padding(top = 4.dp))
}

@Composable
private fun FailedCard(state: ImportUiState, actions: ImportActions) {
    val type = LocalBrickType.current
    WhiteCard(padding = PaddingValues(horizontal = 18.dp, vertical = 22.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Dimens.space12)) {
            Penny(PennyMood.Frown, size = 72.dp, motion = PennyMotion.Wobble)
            Text("I couldn't read that one", style = type.heading26.copy(color = BrickColors.Ink), textAlign = TextAlign.Center)
            Text(
                failureLine(state.fileName, state.failure),
                style = type.small13.copy(color = BrickColors.Muted, lineHeight = 17.5.sp),
                textAlign = TextAlign.Center,
            )
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(BrickColors.NegativeSoft)
                    .padding(horizontal = Dimens.space12, vertical = 10.dp),
            ) {
                Text(
                    "Nothing was added · your towers are unchanged",
                    style = type.small12.copy(color = BrickColors.Negative),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
    OptionRow(
        title = "Try a CSV export instead",
        subtitle = "Most banking apps: Statement → Export → CSV",
        primary = true,
        onClick = actions.onTryAgainClick,
    ) { Text("CSV", style = type.small12.copy(color = BrickColors.White, fontWeight = FontWeight.Black)) }
    OptionRow(title = "Take a photo of it", subtitle = "Penny reads the lines she can · coming soon", onClick = actions.onPhotoClick) {
        Icon(AppIcons.Camera, contentDescription = null, tint = SKY_EDGE, modifier = Modifier.size(18.dp))
    }
    OptionRow(
        title = "Type the bricks by hand",
        subtitle = "3 seconds each",
        tileFill = BrickColors.HandTile.fill,
        onClick = actions.onBack,
    ) { Text("€", style = type.row17.copy(color = BrickColors.HandTile.edge, fontSize = 18.sp)) }
}

/** The 6d reason line: the file's name in bold Ink, then what is wrong with it. */
internal fun failureLine(fileName: String, failure: String): AnnotatedString = buildAnnotatedString {
    withStyle(SpanStyle(color = BrickColors.Ink, fontWeight = FontWeight.Black)) { append(fileName.ifBlank { "That file" }) }
    append(' ')
    append(failure)
}

/** A 6d way out: a 36 dp tile on the left, a 15 sp title and an 11 sp subtitle. */
@Composable
private fun OptionRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    primary: Boolean = false,
    tileFill: Color = if (primary) BrickColors.White.copy(alpha = 0.25f) else BrickColors.SoftSky,
    tile: @Composable () -> Unit,
) {
    val type = LocalBrickType.current
    val ink = if (primary) BrickColors.White else BrickColors.Ink
    val sub = if (primary) BrickColors.White.copy(alpha = 0.9f) else BrickColors.Muted
    ChunkySurface(
        Modifier.fillMaxWidth(),
        fill = if (primary) SKY_FILL else BrickColors.White,
        edge = if (primary) SKY_EDGE else BrickColors.CardEdgeSky,
        edgeWidth = ChunkyEdge.Primary,
        radius = 18.dp,
        onClick = onClick,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Dimens.space16, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.space12),
        ) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(tileFill), contentAlignment = Alignment.Center) { tile() }
            Column(Modifier.weight(1f)) {
                Text(title, style = type.body15.copy(color = ink, fontWeight = FontWeight.Black))
                Text(subtitle, style = type.label11.copy(color = sub, letterSpacing = 0.sp, fontWeight = FontWeight.ExtraBold))
            }
        }
    }
}
