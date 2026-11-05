package ai.freeapp.bricktown.settings

import ai.freeapp.bricktown.core.ui.AppIcons
import ai.freeapp.bricktown.designsystem.ChunkyEdge
import ai.freeapp.bricktown.designsystem.ChunkySurface
import ai.freeapp.bricktown.designsystem.Avatar
import ai.freeapp.bricktown.designsystem.BottomSheet
import ai.freeapp.bricktown.designsystem.BrickColors
import ai.freeapp.bricktown.designsystem.ButtonKind
import ai.freeapp.bricktown.designsystem.ChunkyButton
import ai.freeapp.bricktown.designsystem.Chip
import ai.freeapp.bricktown.designsystem.Dimens
import ai.freeapp.bricktown.designsystem.HeroAmount
import ai.freeapp.bricktown.designsystem.LocalBrickType
import ai.freeapp.bricktown.designsystem.MadeWithFreeapp
import ai.freeapp.bricktown.designsystem.Numpad
import ai.freeapp.bricktown.designsystem.Penny
import ai.freeapp.bricktown.designsystem.PennyMood
import ai.freeapp.bricktown.designsystem.Toast
import ai.freeapp.bricktown.designsystem.World
import ai.freeapp.bricktown.designsystem.WorldKind
import ai.freeapp.bricktown.domain.Currency
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Immutable
class SettingsActions(
    val onBack: () -> Unit = {},
    val onBudget: () -> Unit = {},
    val onBudgetKey: (Char) -> Unit = {},
    val onBudgetSave: () -> Unit = {},
    val onPayday: () -> Unit = {},
    val onPaydayPick: (Int) -> Unit = {},
    val onCurrency: () -> Unit = {},
    val onCurrencyPick: (Currency) -> Unit = {},
    val onBillsFirst: (Boolean) -> Unit = {},
    val onManageTowers: () -> Unit = {},
    val onMemberCanEdit: (String, Boolean) -> Unit = { _, _ -> },
    val onInvite: () -> Unit = {},
    val onNudge: (Boolean) -> Unit = {},
    val onRecap: (Boolean) -> Unit = {},
    val onCelebrations: (Boolean) -> Unit = {},
    val onImport: () -> Unit = {},
    val onExport: () -> Unit = {},
    val onSettleUp: () -> Unit = {},
    val onDeleteTown: () -> Unit = {},
    val onLeaveTown: () -> Unit = {},
    val onConfirmDelete: () -> Unit = {},
    val onConfirmLeave: () -> Unit = {},
    val onDismissSheet: () -> Unit = {},
    val onMessageShown: () -> Unit = {},
    val onTestTool: (String) -> Unit = {},
)

/** Settings (6a), stateless so the gallery can render it. [account] is the ACCOUNT group the screen supplies. */
@Composable
fun SettingsContent(
    state: SettingsUiState,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
    account: @Composable () -> Unit = {},
) {
    val type = LocalBrickType.current
    Box(modifier.fillMaxSize()) {
        World(WorldKind.Pink) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp)
                    .padding(bottom = Dimens.space24),
                verticalArrangement = Arrangement.spacedBy(Dimens.space12),
            ) {
                RoundBackHeader("Settings", actions.onBack)
                TownCard(state)

                SectionLabel("Money")
                GroupCard {
                    SettingsRow(
                        title = "Monthly budget",
                        subtitle = "After rent · ${state.dailyText} a day for moving money",
                        value = state.budgetText.replace(",", " "),
                        chevron = state.canManage,
                        onClick = if (state.canManage) actions.onBudget else null,
                    )
                    RowDivider()
                    SettingsRow(
                        title = "Payday",
                        subtitle = "The month restarts here, not on the 1st",
                        value = ordinal(state.payday),
                        chevron = state.canManage,
                        onClick = if (state.canManage) actions.onPayday else null,
                    )
                    RowDivider()
                    ToggleRow(
                        title = "Bills first",
                        subtitle = "Monthly bricks come off the budget before the daily number",
                        checked = state.billsFirst,
                        onCheckedChange = actions.onBillsFirst,
                    )
                    RowDivider()
                    SettingsRow(
                        title = "Currency",
                        trailing = {
                            Text("${state.currency.name} ${state.currency.symbol}", style = type.body14.copy(color = BrickColors.Muted, fontWeight = FontWeight.Black))
                        },
                        chevron = state.canManage,
                        onClick = if (state.canManage) actions.onCurrency else null,
                    )
                }

                SectionLabel("Towers")
                GroupCard {
                    SettingsRow(
                        title = "${state.towerCount} towers · ${state.capCount} with caps",
                        subtitle = "Rename, recolour, reorder, merge, delete",
                        chevron = true,
                        onClick = actions.onManageTowers,
                        leading = { MiniTowers(state.towerColors) },
                    )
                }

                SectionLabel("Town members")
                GroupCard {
                    state.members.forEachIndexed { index, member ->
                        if (index > 0) RowDivider()
                        SettingsRow(
                            title = if (member.isYou) "You" else member.name,
                            subtitle = when {
                                member.owner -> "Owner"
                                member.canEdit -> "Can add and edit bricks"
                                else -> "Can add bricks"
                            },
                            leading = { Avatar(member.initial, memberColor(member.colorIndex), size = 36.dp, ring = 0.dp) },
                            onClick = if (!member.owner && state.isOwner) {
                                { actions.onMemberCanEdit(member.userId, !member.canEdit) }
                            } else {
                                null
                            },
                            trailing = if (!member.owner && state.isOwner) {
                                {
                                    ai.freeapp.bricktown.designsystem.Toggle(
                                        member.canEdit,
                                        { actions.onMemberCanEdit(member.userId, it) },
                                        contentDescription = "${member.name} can edit bricks",
                                        height = SettingsToggleHeight,
                                    )
                                }
                            } else {
                                null
                            },
                        )
                    }
                    // Settle up has no row in 6a; it sits with the partner it settles with (ours).
                    state.partnerName?.let { partner ->
                        RowDivider()
                        SettingsRow("Settle up with $partner", chevron = true, onClick = actions.onSettleUp)
                    }
                    RowDivider()
                    SettingsRow(
                        title = "Invite someone \u00B7 share a link",
                        titleColor = BrickColors.LinkBlue,
                        onClick = actions.onInvite,
                        leading = { InvitePlus() },
                    )
                }

                SectionLabel("Penny")
                GroupCard {
                    ToggleRow("Evening nudge", state.nudgeHint, state.nudgeOn, actions.onNudge)
                    RowDivider()
                    ToggleRow("Weekly recap", "Sunday 18:00", state.recapOn, actions.onRecap)
                    RowDivider()
                    ToggleRow("Celebrations", "Confetti and jumps · respects Reduce Motion", state.celebrationsOn, actions.onCelebrations)
                }

                FreezeCard(state.freezesLeft)

                SectionLabel("Data")
                GroupCard {
                    SettingsRow("Import a statement", chevron = true, onClick = actions.onImport)
                    RowDivider()
                    SettingsRow("Export everything as CSV", chevron = true, onClick = actions.onExport)
                    RowDivider()
                    Text(
                        "Your bricks live on this phone and sync to your town on our server. CSV statements are read on this phone. A PDF is sent to our server to be read and is not kept.",
                        style = type.small12.copy(color = BrickColors.Muted, fontSize = 11.sp),
                        modifier = Modifier.padding(horizontal = Dimens.space16, vertical = 14.dp),
                    )
                    if (state.isOwner) {
                        RowDivider()
                        SettingsRow("Delete the town", titleColor = BrickColors.Negative, onClick = actions.onDeleteTown)
                    } else if (state.loaded) {
                        RowDivider()
                        SettingsRow("Leave the town", titleColor = BrickColors.Negative, onClick = actions.onLeaveTown)
                    }
                }

                SectionLabel("Account")
                account()
                state.testTools?.let { tools ->
                    SectionLabel(tools.title)
                    GroupCard {
                        tools.rows.forEachIndexed { index, row ->
                            if (index > 0) RowDivider()
                            SettingsRow(row.label, chevron = true, onClick = { actions.onTestTool(row.id) })
                        }
                    }
                }
                MadeWithFreeapp(color = BrickColors.White)
            }
            state.message?.let { Toast(it, actions.onMessageShown, Modifier.align(Alignment.BottomCenter).padding(bottom = Dimens.space16)) }
        }
        SettingsSheets(state, actions)
    }
}

@Composable
private fun TownCard(state: SettingsUiState) {
    val type = LocalBrickType.current
    GroupCard {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Dimens.space16, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.space12),
        ) {
            Penny(PennyMood.Smile, size = 48.dp)
            Column {
                Text("Your town", style = type.body16.copy(color = BrickColors.Ink, fontWeight = FontWeight.Black))
                if (state.loaded) {
                    val trophies = if (state.trophies == 1) "1 trophy" else "${state.trophies} trophies"
                    Text(
                        "Since ${state.townSince} · ${townAgeLabel(state.townDays)} · $trophies",
                        style = type.small12.copy(color = BrickColors.Muted),
                    )
                }
            }
        }
    }
}

/** 6a's six hanging bars: 10 dp wide, 3 dp apart, top-aligned, in the frame's heights. */
@Composable
private fun MiniTowers(colors: List<Int>) {
    LazyRow(
        Modifier.size(width = 75.dp, height = 22.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Top,
        userScrollEnabled = false,
    ) {
        itemsIndexed(colors) { index, color ->
            Box(
                Modifier
                    .size(width = 10.dp, height = MiniTowerHeights[index % MiniTowerHeights.size].dp)
                    .background(BrickColors.CategoryPairs[color.mod(BrickColors.CategoryPairs.size)].fill, RoundedCornerShape(3.dp)),
            )
        }
    }
}

private val MiniTowerHeights = listOf(22, 18, 16, 13, 8, 7)

/** 36 dp inside a 3 dp dashed sky ring, a thin blue plus in the middle (6a). */
@Composable
private fun InvitePlus() {
    Box(
        Modifier.size(42.dp).drawBehind {
            val stroke = 3.dp.toPx()
            drawCircle(
                BrickColors.SoftSkyEdge,
                radius = size.minDimension / 2 - stroke / 2,
                style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 5.dp.toPx()))),
            )
        },
        contentAlignment = Alignment.Center,
    ) {
        Icon(AppIcons.Plus, contentDescription = null, tint = BrickColors.LinkBlue, modifier = Modifier.size(16.dp))
    }
}

/** The gold freeze card (6a): a white tile holding a small ice block, Ink text. */
@Composable
private fun FreezeCard(freezesLeft: Int) {
    val type = LocalBrickType.current
    ChunkySurface(Modifier.fillMaxWidth(), fill = BrickColors.Gold.fill, edge = BrickColors.Gold.edge, edgeWidth = ChunkyEdge.Primary, radius = 22.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Dimens.space16, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.space12),
        ) {
            Box(Modifier.size(44.dp).background(BrickColors.White, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(24.dp)
                        .background(BrickColors.LinkBlue, RoundedCornerShape(4.dp))
                        .padding(3.dp)
                        .background(BrickColors.SkyTop, RoundedCornerShape(1.dp)),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    if (freezesLeft > 0) "$freezesLeft streak freeze available" else "No streak freeze left this month",
                    style = type.body14.copy(color = BrickColors.Ink, fontWeight = FontWeight.Black),
                )
                Text(
                    "Auto-used on a missed day · one per month",
                    style = type.small12.copy(color = BrickColors.Ink.copy(alpha = 0.75f), fontSize = 11.sp),
                )
            }
        }
    }
}

@Composable
private fun SettingsSheets(state: SettingsUiState, actions: SettingsActions) {
    val type = LocalBrickType.current
    when (val sheet = state.sheet) {
        SettingsSheet.None -> Unit
        is SettingsSheet.Budget -> BottomSheet(actions.onDismissSheet) {
            Column(Modifier.padding(horizontal = Dimens.space24).padding(bottom = Dimens.space16), verticalArrangement = Arrangement.spacedBy(Dimens.space12)) {
                Text("Monthly budget", style = type.sheetTitle.copy(color = BrickColors.Ink))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    HeroAmount(sheet.draft.ifEmpty { "0" }, fontSize = 46f, color = BrickColors.Ink)
                }
                Numpad(actions.onBudgetKey)
                ChunkyButton("Save", actions.onBudgetSave, Modifier.fillMaxWidth())
            }
        }
        SettingsSheet.Payday -> BottomSheet(actions.onDismissSheet) {
            Column(Modifier.padding(horizontal = Dimens.space24).padding(bottom = Dimens.space16), verticalArrangement = Arrangement.spacedBy(Dimens.space12)) {
                Text("Payday", style = type.sheetTitle.copy(color = BrickColors.Ink))
                Text("The month restarts on this day.", style = type.small13.copy(color = BrickColors.Muted))
                LazyVerticalGrid(
                    GridCells.Fixed(7),
                    Modifier.height(260.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items((1..31).toList()) { day ->
                        Chip(day.toString(), selected = day == state.payday, onClick = { actions.onPaydayPick(day) }, small = true)
                    }
                }
            }
        }
        SettingsSheet.Currency -> BottomSheet(actions.onDismissSheet) {
            Column(Modifier.padding(horizontal = Dimens.space24).padding(bottom = Dimens.space16), verticalArrangement = Arrangement.spacedBy(Dimens.space8)) {
                Text("Currency", style = type.sheetTitle.copy(color = BrickColors.Ink))
                Currency.entries.forEach { currency ->
                    ChunkyButton(
                        "${currency.name} ${currency.symbol}",
                        { actions.onCurrencyPick(currency) },
                        Modifier.fillMaxWidth(),
                        kind = if (currency == state.currency) ButtonKind.Primary else ButtonKind.Soft,
                    )
                }
            }
        }
        SettingsSheet.DeleteTown -> ConfirmSheet(
            title = "Delete the town?",
            body = "Every brick, tower and member is removed for everyone. This cannot be undone.",
            action = "Delete the town",
            busy = state.busy,
            onConfirm = actions.onConfirmDelete,
            onDismiss = actions.onDismissSheet,
        )
        SettingsSheet.LeaveTown -> ConfirmSheet(
            title = "Leave the town?",
            body = "Your bricks stay in the town. You can rejoin with a new invite.",
            action = "Leave the town",
            busy = state.busy,
            onConfirm = actions.onConfirmLeave,
            onDismiss = actions.onDismissSheet,
        )
    }
}

@Composable
private fun ConfirmSheet(title: String, body: String, action: String, busy: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val type = LocalBrickType.current
    BottomSheet(onDismiss) {
        Column(Modifier.padding(horizontal = Dimens.space24).padding(bottom = Dimens.space16), verticalArrangement = Arrangement.spacedBy(Dimens.space12)) {
            Text(title, style = type.sheetTitle.copy(color = BrickColors.Ink))
            Text(body, style = type.body15.copy(color = BrickColors.Muted))
            ChunkyButton(action, onConfirm, Modifier.fillMaxWidth(), kind = ButtonKind.Danger, enabled = !busy)
            ChunkyButton("Keep it", onDismiss, Modifier.fillMaxWidth(), kind = ButtonKind.Soft)
        }
    }
}

internal fun memberColor(index: Int) = BrickColors.MemberColors[index.mod(BrickColors.MemberColors.size)]

internal fun ordinal(day: Int): String {
    val suffix = if (day % 100 in 11..13) "th" else when (day % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }
    return "$day$suffix"
}
