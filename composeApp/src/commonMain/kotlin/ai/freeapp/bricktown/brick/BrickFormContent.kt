package ai.freeapp.bricktown.brick

import ai.freeapp.bricktown.designsystem.BrickColors
import ai.freeapp.bricktown.designsystem.ButtonKind
import ai.freeapp.bricktown.designsystem.Chip
import ai.freeapp.bricktown.designsystem.ChunkyButton
import ai.freeapp.bricktown.designsystem.ChunkyEdge
import ai.freeapp.bricktown.designsystem.ChunkySurface
import ai.freeapp.bricktown.designsystem.FormHero
import ai.freeapp.bricktown.designsystem.LocalBrickType
import ai.freeapp.bricktown.designsystem.Numpad
import ai.freeapp.bricktown.designsystem.Toggle
import ai.freeapp.bricktown.designsystem.World
import ai.freeapp.bricktown.designsystem.WorldKind
import ai.freeapp.bricktown.designsystem.onWorld
import ai.freeapp.bricktown.domain.TowerProblem
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** What the form reports back. One callback per thing a user can do. */
class BrickFormActions(
    val onClose: () -> Unit,
    val onKey: (Char) -> Unit,
    val onSelectTower: (String) -> Unit,
    val onNoteChange: (String) -> Unit,
    val onMonthlyChange: (Boolean) -> Unit,
    val onRefundChange: (Boolean) -> Unit,
    val onSharedChange: (Boolean) -> Unit,
    val onOpenNewTower: () -> Unit,
    val onCancelNewTower: () -> Unit,
    val onNewTowerNameChange: (String) -> Unit,
    val onCreateTower: () -> Unit,
    val onSubmit: () -> Unit,
    val onDelete: () -> Unit,
)

/** The pink full-screen form behind Add brick, Edit brick and the refund variant (6e). */
@Composable
fun BrickFormContent(state: BrickFormState, actions: BrickFormActions, modifier: Modifier = Modifier) {
    val type = LocalBrickType.current
    World(WorldKind.Pink, modifier) {
        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(state.title, style = type.body15.copy(color = BrickColors.White, fontWeight = FontWeight.Black).onWorld())
                CloseDisc(actions.onClose, size = 44.dp)
            }
            when {
                state.missing -> Message("That brick is gone.", "Back", actions.onClose)
                !state.ready -> Message("Loading your town...", null, actions.onClose)
                else -> FormBody(state, actions)
            }
        }
    }
}

@Composable
private fun Message(text: String, button: String?, onClick: () -> Unit) {
    val type = LocalBrickType.current
    Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(text, style = type.body16.copy(color = BrickColors.White, textAlign = TextAlign.Center).onWorld())
        if (button != null) ChunkyButton(button, onClick, Modifier.fillMaxWidth(), kind = ButtonKind.Soft)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.FormBody(state: BrickFormState, actions: BrickFormActions) {
    val type = LocalBrickType.current
    val focus = LocalFocusManager.current
    // The numpad types the amount; the system keyboard types the note and a tower name. With both
    // up, the keyboard plus the numpad leave no room for the field being typed in, so the numpad
    // steps aside while a text field has focus. Tapping the amount (or Done) brings it back.
    var noteFocused by remember { mutableStateOf(false) }
    var towerNameFocused by remember { mutableStateOf(false) }
    val typing = noteFocused || towerNameFocused
    // 6e spaces the blocks one by one: 14 above the amount, 10 under it and under the chips, 8 under the
    // note row, 12 under the toggles and the refund card.
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
        FormHero(
            sign = state.heroSign,
            symbol = state.currency.symbol,
            symbolAfter = state.currency.symbolAfter,
            digits = state.heroDigits,
            spoken = state.heroText,
            modifier = Modifier.fillMaxWidth().clickableNoRipple { focus.clearFocus() }.padding(top = 14.dp, bottom = 10.dp),
        )
        FlowRow(Modifier.padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            state.towers.forEach { tower ->
                Chip(
                    label = tower.name,
                    selected = tower.id == state.selectedTowerId,
                    onClick = { actions.onSelectTower(tower.id) },
                    selectedColors = BrickColors.CategoryPairs[tower.colorIndex % BrickColors.CategoryPairs.size],
                    unselectedFill = BrickColors.White,
                    selectedText = BrickColors.White,
                )
            }
            run {
                Box(
                    Modifier
                        .semantics { contentDescription = "New tower" }
                        .dashedOutline(BrickColors.White.copy(alpha = 0.8f), radius = 12.dp)
                        .background(BrickColors.White.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                        .clickableNoRipple(actions.onOpenNewTower),
                ) {
                    // The 2 dp dashed border sits inside the box, so the padding carries it: 12 + 2 and 8 + 2.
                    Text(
                        "+ New",
                        style = type.small12.copy(color = BrickColors.White, fontWeight = FontWeight.Black),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }
        }
        if (state.newTowerOpen) NewTowerRow(state, actions, onNameFocus = { towerNameFocused = it }, Modifier.padding(bottom = 10.dp))
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            InputBox(
                value = state.note,
                onValueChange = actions.onNoteChange,
                placeholder = "Note (optional), e.g. Lidl",
                description = "Note",
                onFocusChange = { noteFocused = it },
                modifier = Modifier.weight(1f),
            )
            ToggleRow("Monthly", state.monthly, actions.onMonthlyChange, wrap = true)
        }
        if (state.isEdit) {
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToggleRow("Refund", state.refund, actions.onRefundChange, Modifier.weight(1f))
                ToggleRow("Shared", state.shared, actions.onSharedChange, Modifier.weight(1f))
            }
        }
        if (state.refund) RefundExplainer(state, Modifier.padding(bottom = 12.dp))
        state.saveFailed?.let { Text(it, style = type.small12.copy(color = BrickColors.White).onWorld()) }
    }
    // The handoff's 44 dp under Save is iOS's 34 dp home indicator plus 10; the World already pads for the system bar.
    Column(Modifier.padding(bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // The handoff's keys are a 50 dp face on a 4 dp edge; Numpad counts the edge in keySize.
        if (!typing) Numpad(actions.onKey, Modifier.fillMaxWidth(), keySize = 54.dp)
        // The trash stretches to the Save button's height, as the handoff's flex row does.
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.isEdit) {
                ChunkySurface(
                    modifier = Modifier.width(58.dp).fillMaxHeight(),
                    fill = BrickColors.White,
                    edge = BrickColors.CardEdge,
                    edgeWidth = ChunkyEdge.Primary,
                    radius = 18.dp,
                    onClick = actions.onDelete,
                    contentDescription = "Delete brick",
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { TrashGlyph() }
                }
            }
            ChunkyButton(state.submitLabel, actions.onSubmit, Modifier.weight(1f), enabled = state.canSubmit)
        }
    }
}

@Composable
private fun TrashGlyph() {
    Canvas(Modifier.size(22.dp)) {
        val u = size.width / 24f
        val stroke = Stroke(2.6f * u, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(3f * u, 6f * u); lineTo(21f * u, 6f * u)
            moveTo(8f * u, 6f * u); lineTo(8f * u, 4f * u); lineTo(16f * u, 4f * u); lineTo(16f * u, 6f * u)
            moveTo(6f * u, 6f * u); lineTo(7f * u, 20f * u); lineTo(17f * u, 20f * u); lineTo(18f * u, 6f * u)
        }
        drawPath(path, BrickColors.Negative, style = stroke)
    }
}

/** A white 44 dp pill with a label and a switch. [wrap] keeps it as wide as both (Monthly); otherwise it fills and the switch sits at the end. */
@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, wrap: Boolean = false) {
    val type = LocalBrickType.current
    ChunkySurface(modifier, fill = BrickColors.White, edge = BrickColors.CardEdge, edgeWidth = ChunkyEdge.Tiny, radius = 12.dp) {
        Row(
            (if (wrap) Modifier else Modifier.fillMaxWidth()).height(44.dp).padding(start = 12.dp, end = if (wrap) 10.dp else 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, if (wrap) Modifier else Modifier.weight(1f), style = type.small12.copy(color = BrickColors.Ink, fontWeight = FontWeight.Black), maxLines = 1)
            Toggle(checked, onChange, contentDescription = label)
        }
    }
}

@Composable
private fun InputBox(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    description: String,
    modifier: Modifier = Modifier,
    onFocusChange: (Boolean) -> Unit = {},
) {
    val type = LocalBrickType.current
    val focus = LocalFocusManager.current
    // A field leaving composition (the new-tower row closing) never reports a lost focus.
    DisposableEffect(Unit) { onDispose { onFocusChange(false) } }
    ChunkySurface(modifier.fillMaxWidth(), fill = BrickColors.White, edge = BrickColors.CardEdge, edgeWidth = ChunkyEdge.Tiny, radius = 12.dp) {
        Box(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text(placeholder, style = type.small13.copy(color = BrickColors.Disabled))
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = type.small13.copy(color = BrickColors.Ink),
                cursorBrush = SolidColor(BrickColors.Gold.fill),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                modifier = Modifier.fillMaxWidth().onFocusChanged { onFocusChange(it.isFocused) }.semantics { contentDescription = description },
            )
        }
    }
}

@Composable
private fun NewTowerRow(state: BrickFormState, actions: BrickFormActions, onNameFocus: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val type = LocalBrickType.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            InputBox(state.newTowerName, actions.onNewTowerNameChange, "Category name, e.g. Pets", "New tower name", Modifier.weight(1f), onNameFocus)
            ChunkySurface(fill = BrickColors.Green.fill, edge = BrickColors.Green.edge, edgeWidth = ChunkyEdge.Chip, radius = 12.dp, onClick = actions.onCreateTower, contentDescription = "Create tower") {
                Text("Create", style = type.small13.copy(color = BrickColors.White), modifier = Modifier.height(42.dp).padding(horizontal = 14.dp).wrapCenter())
            }
            ChunkySurface(fill = BrickColors.White, edge = BrickColors.CardEdge, edgeWidth = ChunkyEdge.Chip, radius = 12.dp, onClick = actions.onCancelNewTower, contentDescription = "Cancel new tower") {
                Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) { Text("x", style = type.small13.copy(color = BrickColors.Muted)) }
            }
        }
        state.newTowerProblem?.let { Text(towerProblemText(it), style = type.small12.copy(color = BrickColors.White).onWorld()) }
    }
}

internal fun towerProblemText(problem: TowerProblem): String = when (problem) {
    TowerProblem.NameRequired -> "Give the tower a name."
    TowerProblem.NameTooLong -> "Use 24 characters or fewer."
    TowerProblem.NameTaken -> "You already have a tower with that name."
    TowerProblem.LastTower, TowerProblem.MergeIntoItself -> "That change is not allowed."
}

/** "Removes 4 bricks from Shopping" with a picture: the tower's bricks, the removed ones dashed. */
@Composable
private fun RefundExplainer(state: BrickFormState, modifier: Modifier = Modifier) {
    val type = LocalBrickType.current
    val removed = state.refundBricks
    val color = state.towers.firstOrNull { it.id == state.selectedTowerId }?.colorIndex ?: 0
    val pair = BrickColors.CategoryPairs[color % BrickColors.CategoryPairs.size]
    val noun = if (removed == 1) "brick" else "bricks"
    ChunkySurface(modifier.fillMaxWidth(), fill = BrickColors.White, edge = BrickColors.CardEdge, edgeWidth = ChunkyEdge.Chip, radius = 16.dp) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            val solid = (state.towerBricks - removed).coerceIn(0, 4)
            val ghosts = removed.coerceIn(0, 4)
            val count = (solid + ghosts).coerceAtLeast(1)
            Canvas(Modifier.size(22.dp, (count * 10 - 2).dp)) {
                val h = 8.dp.toPx()
                val gap = 2.dp.toPx()
                val line = 2.dp.toPx()
                val corner = CornerRadius(2.dp.toPx())
                for (i in 0 until ghosts + solid) {
                    val top = i * (h + gap)
                    if (i < ghosts) {
                        // A 2 dp dashed border drawn inside the 22 x 8 box, as CSS box-sizing:border-box does.
                        drawRoundRect(
                            BrickColors.Negative, Offset(line / 2, top + line / 2), Size(size.width - line, h - line), corner,
                            style = Stroke(line, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3 * line, 1.5f * line))),
                        )
                    } else {
                        drawRoundRect(pair.fill, Offset(0f, top), Size(size.width, h), corner)
                    }
                }
            }
            Text(
                "Removes $removed $noun from ${state.selectedTowerName} · shows in History as a refund",
                style = type.small12.copy(color = BrickColors.Muted),
                modifier = Modifier.weight(1f),
            )
        }
    }
}
