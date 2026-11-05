package ai.freeapp.bricktown.home

import ai.freeapp.bricktown.designsystem.BarTab
import ai.freeapp.bricktown.designsystem.BottomBar
import ai.freeapp.bricktown.designsystem.LocalBrickType
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.ui.Alignment
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import ai.freeapp.bricktown.designsystem.onWorld
import ai.freeapp.bricktown.designsystem.Toast
import ai.freeapp.bricktown.designsystem.LocalReduceMotion
import ai.freeapp.bricktown.designsystem.Confetti
import ai.freeapp.bricktown.designsystem.ChunkySurface
import ai.freeapp.bricktown.designsystem.ChunkyEdge
import ai.freeapp.bricktown.designsystem.BrickColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * The frame of the four tabs: the tab's screen, the bottom bar under it, and
 * the + menu (two floating cards over a scrim). `App` keeps it
 * outside the per-visit entry, so the bar stays put while the tabs change
 * under it.  It also hosts the toast and the celebration. [pulseAdd] rings the +
 * while the month has no brick yet (6c).
 *
 * A tap on the tab already showing does nothing.
 */
@Composable
fun HomeScaffold(
    selected: BarTab,
    onShowMonth: () -> Unit,
    onShowHistory: () -> Unit,
    onShowCompare: () -> Unit,
    onShowTrail: () -> Unit,
    onAddBrick: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
    pulseAdd: Boolean = false,
    content: @Composable () -> Unit,
) {
    var addMenuOpen by rememberSaveable { mutableStateOf(false) }
    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // The bar below covers the navigation bar: the tab's World pads only the
            // part of the bottom inset that reaches above it, the keyboard.
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .consumeWindowInsets(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)),
            ) { content() }
            BottomBar(
                selected = selected,
                onSelect = { tab ->
                    if (tab != selected) {
                        when (tab) {
                            BarTab.Month -> onShowMonth()
                            BarTab.History -> onShowHistory()
                            BarTab.Compare -> onShowCompare()
                            BarTab.Trail -> onShowTrail()
                        }
                    }
                },
                onAdd = { addMenuOpen = true },
                pulseAdd = pulseAdd,
            )
        }
        FeedbackHost(koinInject())
        if (addMenuOpen) {
            AddMenu(
                onAddBrick = {
                    addMenuOpen = false
                    onAddBrick()
                },
                onImport = {
                    addMenuOpen = false
                    onImport()
                },
                onDismiss = { addMenuOpen = false },
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun AddMenu(onAddBrick: () -> Unit, onImport: () -> Unit, onDismiss: () -> Unit) {
    val type = LocalBrickType.current
    BackHandler(onBack = onDismiss)
    Box(
        Modifier
            .fillMaxSize()
            .background(BrickColors.Scrim)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
    ) {
        Column(
            Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 132.dp).widthIn(min = 230.dp).width(IntrinsicSize.Max),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MenuCard("Type an amount", "One brick, 3 seconds", BrickColors.WorldPink, BrickColors.White, onAddBrick)
            MenuCard("Import statement", "CSV or PDF from your bank", BrickColors.SoftSky, BrickColors.Ink, onImport, BrickColors.SoftSkyEdge)
        }
    }
}

@Composable
private fun MenuCard(
    title: String,
    subtitle: String,
    fill: Color,
    textColor: Color,
    onClick: () -> Unit,
    edge: Color = BrickColors.CardEdge,
) {
    val type = LocalBrickType.current
    ChunkySurface(Modifier.fillMaxWidth(), fill = fill, edge = edge, edgeWidth = ChunkyEdge.Row, radius = 18.dp, onClick = onClick, contentDescription = title) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(title, style = type.title18.copy(color = textColor))
            Text(subtitle, style = type.small12.copy(color = textColor.copy(alpha = 0.8f)))
        }
    }
}

/** The toast, with its Undo, and the confetti that [HomeFeedback] raised; both survive a tab change. */
@Composable
private fun BoxScope.FeedbackHost(feedback: HomeFeedback) {
    val state by feedback.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val reduce = LocalReduceMotion.current
    val type = LocalBrickType.current
    var celebrating by remember { mutableStateOf(false) }
    LaunchedEffect(state.burst) {
        if (state.celebrate) {
            celebrating = !reduce
            feedback.consumeCelebration()
        }
    }
    if (celebrating && !reduce) {
        Confetti(state.burst, Modifier.fillMaxSize(), onFinished = { celebrating = false })
        Text(
            "+1 brick!",
            style = type.hero44.copy(color = BrickColors.White, textAlign = TextAlign.Center).onWorld(hero = true),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 260.dp),
        )
    }
    state.toast?.let { toast ->
        key(toast.sequence) {
            Toast(
                message = toast.message,
                onTimeout = feedback::dismissToast,
                modifier = Modifier.align(Alignment.BottomCenter).padding(start = 18.dp, end = 18.dp, bottom = 124.dp),
                onUndo = toast.undo?.let { { scope.launch { feedback.undo() } } },
            )
        }
    }
}
