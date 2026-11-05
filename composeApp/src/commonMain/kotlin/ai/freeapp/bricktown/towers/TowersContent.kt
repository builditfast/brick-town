package ai.freeapp.bricktown.towers

import ai.freeapp.bricktown.designsystem.BottomSheet
import ai.freeapp.bricktown.designsystem.BrickColors
import ai.freeapp.bricktown.designsystem.ButtonKind
import ai.freeapp.bricktown.designsystem.ButtonSize
import ai.freeapp.bricktown.designsystem.ChunkyButton
import ai.freeapp.bricktown.designsystem.ChunkyEdge
import ai.freeapp.bricktown.designsystem.ChunkySurface
import ai.freeapp.bricktown.designsystem.Dimens
import ai.freeapp.bricktown.designsystem.FlexRow
import ai.freeapp.bricktown.designsystem.LocalBrickType
import ai.freeapp.bricktown.designsystem.LocalReduceMotion
import ai.freeapp.bricktown.designsystem.Toast
import ai.freeapp.bricktown.designsystem.World
import ai.freeapp.bricktown.designsystem.WorldKind
import ai.freeapp.bricktown.designsystem.onWorld
import ai.freeapp.bricktown.settings.RoundBackHeader
import ai.freeapp.bricktown.settings.RowChevron
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Immutable
class TowersActions(
    val onBack: () -> Unit = {},
    val onTowerClick: (String) -> Unit = {},
    val onNewTowerClick: () -> Unit = {},
    val onMergeClick: (String) -> Unit = {},
    val onDeleteClick: (String) -> Unit = {},
    val onMove: (Int, Int) -> Unit = { _, _ -> },
    val onMoveEnd: () -> Unit = {},
    val onNameChange: (String) -> Unit = {},
    val onColorPick: (Int) -> Unit = {},
    val onCapPick: (Long?) -> Unit = {},
    val onCustomCapClick: () -> Unit = {},
    val onCustomCapChange: (String) -> Unit = {},
    val onEditorCancel: () -> Unit = {},
    val onEditorSave: () -> Unit = {},
    val onMoveTargetPick: (String) -> Unit = {},
    val onPromptDismiss: () -> Unit = {},
    val onMessageShown: () -> Unit = {},
)

private val CapChoices: List<Pair<String, Long?>> = listOf("None" to null, "€150" to 15_000L, "€250" to 25_000L, "€400" to 40_000L)
private val SwipeActionWidth = 75.dp
private val RowSlot = 86.dp
private val SwatchOutline = 5.dp

/** Manage towers (6f): drag to reorder, tap to edit, swipe for Merge or Delete, "+ New tower". */
@Composable
fun TowersContent(state: TowersUiState, actions: TowersActions, modifier: Modifier = Modifier) {
    val type = LocalBrickType.current
    Box(modifier.fillMaxSize()) {
        World(WorldKind.Pink) {
            Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
                RoundBackHeader("Towers", actions.onBack) {
                    Text(
                        "Done",
                        style = type.small12.copy(color = BrickColors.White.copy(alpha = 0.9f), fontWeight = FontWeight.Black),
                        modifier = Modifier.clickable(role = Role.Button, onClick = actions.onBack),
                    )
                }
                Text(
                    if (state.canManage) "Drag to reorder · tap to edit · swipe left to merge or delete" else "Only the owner changes towers",
                    style = type.small12.onWorld().copy(color = BrickColors.White),
                    modifier = Modifier.padding(start = 6.dp, top = 10.dp, bottom = Dimens.space8),
                )
                TowerList(state, actions, Modifier.weight(1f))
            }
        }
        val prompt = state.prompt
        if (prompt != null) MoveSheet(prompt, actions)
        val message = state.message
        if (message != null) {
            Box(Modifier.align(Alignment.BottomCenter).padding(Dimens.space16)) { Toast(message, onTimeout = actions.onMessageShown) }
        }
    }
}

@Composable
private fun TowerList(state: TowersUiState, actions: TowersActions, modifier: Modifier) {
    val density = LocalDensity.current
    val slotPx = with(density) { RowSlot.toPx() }
    var dragging by remember { mutableStateOf<String?>(null) }
    var dragDelta by remember { mutableFloatStateOf(0f) }
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        itemsIndexed(state.rows, key = { _, row -> row.id }) { index, row ->
            val editor = state.editor?.takeIf { it.id == row.id }
            if (editor != null) {
                TowerEditorCard(editor, actions)
            } else {
                TowerRowItem(
                    row = row,
                    enabled = state.canManage,
                    dragOffset = if (dragging == row.id) dragDelta else 0f,
                    onTap = { actions.onTowerClick(row.id) },
                    onMerge = { actions.onMergeClick(row.id) },
                    onDelete = { actions.onDeleteClick(row.id) },
                    onDragStart = { dragging = row.id; dragDelta = 0f },
                    onDrag = { dy ->
                        dragDelta += dy
                        val steps = (dragDelta / slotPx).toInt()
                        if (steps != 0) {
                            val target = (index + steps).coerceIn(0, state.rows.lastIndex)
                            if (target != index) actions.onMove(index, target)
                            dragDelta -= steps * slotPx
                        }
                    },
                    onDragEnd = { dragging = null; dragDelta = 0f; actions.onMoveEnd() },
                )
            }
        }
        item(key = "new") {
            val editor = state.editor?.takeIf { it.id == null }
            if (editor != null) TowerEditorCard(editor, actions) else NewTowerRow(state.canManage, actions.onNewTowerClick)
        }
        item(key = "note") {
            Text(
                "Deleting a tower asks where its bricks go (merge into another tower). Bricks are never lost.",
                style = LocalBrickType.current.label11.copy(color = BrickColors.White, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.sp, lineHeight = 14.85.sp),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BrickColors.White.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun TowerRowItem(
    row: TowerRow,
    enabled: Boolean,
    dragOffset: Float,
    onTap: () -> Unit,
    onMerge: () -> Unit,
    onDelete: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    val type = LocalBrickType.current
    val reduce = LocalReduceMotion.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val openPx = with(density) { (SwipeActionWidth * 2).toPx() }
    val swipe = remember { Animatable(0f) }
    val pair = BrickColors.CategoryPairs[row.colorIndex.coerceIn(0, BrickColors.CategoryPairs.lastIndex)]
    fun settle(target: Float) = scope.launch { if (reduce) swipe.snapTo(target) else swipe.animateTo(target, tween(180)) }
    Box(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { translationY = dragOffset; if (dragOffset != 0f) shadowElevation = 12f },
    ) {
        if (enabled) {
            Row(Modifier.matchParentSize().graphicsLayer { alpha = if (swipe.value < 0f) 1f else 0f }, horizontalArrangement = Arrangement.End) {
                SwipeAction("Merge", BrickColors.PartnerPurple, Modifier.width(SwipeActionWidth)) { settle(0f); onMerge() }
                SwipeAction("Delete", BrickColors.Negative, Modifier.width(SwipeActionWidth)) { settle(0f); onDelete() }
            }
        }
        ChunkySurface(
            Modifier
                .fillMaxWidth()
                .offset { IntOffset(swipe.value.roundToInt(), 0) }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragEnd = { settle(if (swipe.value < -openPx / 2) -openPx else 0f) },
                        onDragCancel = { settle(0f) },
                    ) { change, dx ->
                        change.consume()
                        scope.launch { swipe.snapTo((swipe.value + dx).coerceIn(-openPx, 0f)) }
                    }
                },
            edgeWidth = ChunkyEdge.Row,
            radius = 18.dp,
            onClick = if (enabled) ({ if (swipe.value != 0f) settle(0f) else onTap() }) else null,
            contentDescription = row.name,
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (enabled) {
                    Box(
                        Modifier
                            .size(width = 30.dp, height = 44.dp)
                            .semantics { contentDescription = "Drag to reorder ${row.name}" }
                            .pointerInput(row.id) {
                                detectDragGestures(
                                    onDragStart = { onDragStart() },
                                    onDragEnd = onDragEnd,
                                    onDragCancel = onDragEnd,
                                ) { change, amount ->
                                    change.consume()
                                    onDrag(amount.y)
                                }
                            },
                        contentAlignment = Alignment.CenterStart,
                    ) { GripBars() }
                }
                ChunkySurface(Modifier.size(38.dp), fill = pair.fill, edge = pair.edge, edgeWidth = ChunkyEdge.Tiny, radius = 12.dp) {}
                Column(Modifier.weight(1f).padding(horizontal = Dimens.space12), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(row.name, style = type.body14.copy(color = BrickColors.Ink, fontWeight = FontWeight.Black), maxLines = 1)
                    Text(row.meta, style = type.small12.copy(color = BrickColors.Muted, fontSize = 11.sp), maxLines = 1)
                }
                RowChevron()
            }
        }
    }
}

@Composable
private fun GripBars() {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(3) { Box(Modifier.size(width = 16.dp, height = 3.dp).background(BrickColors.Disabled, RoundedCornerShape(2.dp))) }
    }
}

@Composable
private fun SwipeAction(label: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    val type = LocalBrickType.current
    Box(
        modifier.fillMaxSize().padding(start = 1.dp).clip(RoundedCornerShape(0.dp)).background(color).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = type.small12.copy(color = BrickColors.White, fontWeight = FontWeight.Black)) }
}

@Composable
private fun NewTowerRow(enabled: Boolean, onClick: () -> Unit) {
    val type = LocalBrickType.current
    Box(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                val stroke = 3.dp.toPx()
                drawRoundRect(
                    BrickColors.White.copy(alpha = 0.8f),
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, size.height - stroke),
                    cornerRadius = CornerRadius(18.dp.toPx() - stroke / 2),
                    style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(9.dp.toPx(), 6.dp.toPx()))),
                )
            }
            .clip(RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(17.dp),
        contentAlignment = Alignment.Center,
    ) { Text("+ New tower", style = type.body14.copy(color = BrickColors.White, fontWeight = FontWeight.Black)) }
}

@Composable
private fun TowerEditorCard(editor: TowerEditor, actions: TowersActions) {
    val type = LocalBrickType.current
    val pair = BrickColors.CategoryPairs[editor.colorIndex.coerceIn(0, BrickColors.CategoryPairs.lastIndex)]
    ChunkySurface(Modifier.fillMaxWidth(), edgeWidth = ChunkyEdge.Row, radius = 18.dp) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(Dimens.space12)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ChunkySurface(Modifier.size(38.dp), fill = pair.fill, edge = pair.edge, edgeWidth = ChunkyEdge.Tiny, radius = 12.dp) {}
                EditorField(editor.name, actions.onNameChange, placeholder = "Tower name", description = "Tower name", modifier = Modifier.weight(1f))
            }
            if (editor.error != null) Text(editor.error, style = type.small12.copy(color = BrickColors.Negative, fontWeight = FontWeight.ExtraBold))
            Column {
                EditorLabel("COLOUR")
                // The selected outline sits 5 dp outside its swatch; the row is shifted so the first one is not clipped.
                Row(
                    Modifier.offset(x = -SwatchOutline).horizontalScroll(rememberScrollState()).padding(horizontal = SwatchOutline),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    BrickColors.CategoryPairs.forEachIndexed { index, swatch ->
                        val selected = index == editor.colorIndex
                        Box(
                            Modifier
                                .size(30.dp)
                                .drawBehind {
                                    if (selected) {
                                        val stroke = 3.dp.toPx()
                                        val out = SwatchOutline.toPx() - stroke / 2
                                        drawRoundRect(
                                            BrickColors.Ink,
                                            topLeft = Offset(-out, -out),
                                            size = Size(size.width + out * 2, size.height + out * 2),
                                            cornerRadius = CornerRadius(10.dp.toPx() + out),
                                            style = Stroke(stroke),
                                        )
                                    }
                                }
                                .clip(RoundedCornerShape(10.dp))
                                .background(swatch.fill)
                                .semantics {
                                    contentDescription = "Colour ${index + 1}"
                                    this.selected = selected
                                }
                                .clickable(role = Role.RadioButton) { actions.onColorPick(index) },
                        )
                    }
                }
            }
            Column {
                EditorLabel("CAP")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CapChoices.forEach { (label, cents) ->
                        CapChip(label, !editor.customCap && editor.capCents == cents) { actions.onCapPick(cents) }
                    }
                    CapChip("Custom", editor.customCap, actions.onCustomCapClick)
                }
            }
            if (editor.customCap) {
                EditorField(
                    editor.customDraft,
                    actions.onCustomCapChange,
                    placeholder = "Cap in whole euros",
                    description = "Custom cap in whole euros",
                    keyboard = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            FlexRow(listOf(1f, 2f), padding = ButtonSize.Small.padding, gap = 8.dp) {
                ChunkyButton("Cancel", actions.onEditorCancel, kind = ButtonKind.Soft, size = ButtonSize.Small, textColor = BrickColors.Muted)
                ChunkyButton("Save tower", actions.onEditorSave, size = ButtonSize.Small)
            }
        }
    }
}

@Composable
private fun EditorLabel(text: String) {
    Text(text, style = LocalBrickType.current.label11.copy(color = BrickColors.Muted), modifier = Modifier.padding(bottom = 6.dp))
}

/** The editor's 44 dp soft field with its 3 dp edge (6f). */
@Composable
private fun EditorField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    description: String,
    modifier: Modifier = Modifier,
    keyboard: KeyboardOptions = KeyboardOptions.Default,
) {
    val style = LocalBrickType.current.body15.copy(color = BrickColors.Ink, fontWeight = FontWeight.Black)
    ChunkySurface(modifier, fill = BrickColors.SoftFill, edgeWidth = ChunkyEdge.Tiny, radius = 12.dp) {
        Box(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                keyboardOptions = keyboard,
                textStyle = style,
                cursorBrush = SolidColor(BrickColors.Gold.fill),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = description },
                decorationBox = { inner ->
                    if (value.isEmpty()) Text(placeholder, style = style.copy(color = BrickColors.Disabled))
                    inner()
                },
            )
        }
    }
}

@Composable
private fun MoveSheet(prompt: TowerMovePrompt, actions: TowersActions) {
    val type = LocalBrickType.current
    BottomSheet(onDismiss = actions.onPromptDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Dimens.space16).padding(bottom = Dimens.space16), verticalArrangement = Arrangement.spacedBy(Dimens.space8)) {
            Text(
                if (prompt.deleting) "Delete ${prompt.fromName}" else "Merge ${prompt.fromName}",
                style = type.title18.copy(color = BrickColors.Ink),
            )
            Text(
                "Where do its bricks go? They are never lost.",
                style = type.small13.copy(color = BrickColors.Muted),
            )
            prompt.targets.forEach { target ->
                val pair = BrickColors.CategoryPairs[target.colorIndex.coerceIn(0, BrickColors.CategoryPairs.lastIndex)]
                ChunkySurface(Modifier.fillMaxWidth(), fill = BrickColors.SoftFill, edgeWidth = ChunkyEdge.Chip, radius = 14.dp, onClick = { actions.onMoveTargetPick(target.id) }, contentDescription = "Move into ${target.name}") {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.space12)) {
                        ChunkySurface(Modifier.size(28.dp), fill = pair.fill, edge = pair.edge, edgeWidth = ChunkyEdge.Tiny, radius = 8.dp) {}
                        Text(target.name, style = type.body14.copy(color = BrickColors.Ink, fontWeight = FontWeight.ExtraBold))
                    }
                }
            }
        }
    }
}

/** 6f's cap chips are flat (no edge) and smaller than the shared Chip: 7 x 11 padding, radius 10, 12 sp. */
@Composable
private fun CapChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = LocalBrickType.current.small12.copy(color = if (selected) BrickColors.White else BrickColors.Ink, fontWeight = FontWeight.Black),
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) BrickColors.Ink else BrickColors.SoftFillAlt)
            .semantics { this.selected = selected }
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 7.dp),
    )
}
