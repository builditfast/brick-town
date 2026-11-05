package ai.freeapp.bricktown.designsystem

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp

@Immutable
enum class WorldKind { Pink, Sky, Gold, Night }

/** Light status-bar icons while composed; the previous appearance returns when it leaves. */
@Composable
internal expect fun LightStatusBarIcons()

/**
 * The root of every screen: pink tile grid, sky with its frame's [clouds], gold, or
 * night, drawn under the status bar and the navigation bar. [content] is kept
 * out of the bars, the display cutout and the keyboard, so a screen never
 * handles insets itself. A [BottomSheet] goes beside the World, not in
 * [content], or it stops at the navigation bar instead of covering it.
 */
@Composable
fun World(
    kind: WorldKind,
    modifier: Modifier = Modifier,
    clouds: List<SkyCloud> = emptyList(),
    content: @Composable BoxScope.() -> Unit = {},
) {
    val reduce = LocalReduceMotion.current
    val sways: List<State<Float>>? = if (kind == WorldKind.Sky && clouds.isNotEmpty() && !reduce) {
        val t = rememberInfiniteTransition(label = "clouds")
        clouds.map { c ->
            t.animateFloat(0f, 1f, infiniteRepeatable(tween(c.seconds * 1000, easing = EaseInOut), RepeatMode.Reverse), label = "drift")
        }
    } else {
        null
    }
    val safeTop = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding()
    if (kind == WorldKind.Night) LightStatusBarIcons()
    Box(
        modifier
            .fillMaxSize()
            .drawBehind {
                when (kind) {
                    WorldKind.Pink -> {
                        drawRect(BrickColors.WorldPink)
                        drawTileGrid()
                    }
                    WorldKind.Sky -> {
                        drawRect(BrickColors.SkyBrush)
                        clouds.forEachIndexed { i, c -> drawCloud(c.rect(size.width.toDp(), safeTop, sways?.get(i)?.value ?: 0f), c.alpha) }
                    }
                    WorldKind.Gold -> drawRect(BrickColors.GoldBrush)
                    WorldKind.Night -> drawRect(
                        Brush.linearGradient(
                            0f to BrickColors.NightTop, 0.55f to BrickColors.NightMid, 1f to BrickColors.NightBottom,
                            start = Offset(size.width * 0.35f, 0f), end = Offset(size.width * 0.65f, size.height),
                        ),
                    )
                }
            },
    ) {
        Box(Modifier.fillMaxSize().safeDrawingPadding(), content = content)
    }
}

private fun DrawScope.drawTileGrid() {
    val step = 76.dp.toPx()
    val line = 3.dp.toPx()
    val offset = -2.dp.toPx()
    var x = offset
    while (x < size.width) {
        drawRect(BrickColors.WorldPinkLine, Offset(x, 0f), Size(line, size.height))
        x += step
    }
    var y = offset
    while (y < size.height) {
        drawRect(BrickColors.WorldPinkLine, Offset(0f, y), Size(size.width, line))
        y += step
    }
}

/**
 * A white pill on the sky (a disc when [width] equals [height]) that sways 40 dp right and back over [seconds],
 * as the handoff's `drift` animation does; [reverse] starts it at the far end (`alternate-reverse`). [top] and
 * [edge] are the frame's own numbers: [edge] is the left offset, or the right one when [fromRight], and a
 * negative edge hangs the pill off the screen. [lift] is how much higher the screen's content starts than its
 * frame's, under the 62 dp iOS status bar, so the cloud keeps its place beside the content.
 */
@Immutable
data class SkyCloud(
    val top: Dp,
    val edge: Dp,
    val width: Dp,
    val height: Dp,
    val alpha: Float,
    val seconds: Int,
    val fromRight: Boolean = false,
    val reverse: Boolean = false,
    val lift: Dp = 0.dp,
)

/** The clouds each sky frame draws. Trail, Import, Join and the sign-in screens draw none. */
object SkyClouds {
    private fun leftPill(top: Dp, lift: Dp) = SkyCloud(top, (-20).dp, 120.dp, 44.dp, 0.7f, 14, lift = lift)

    /** 5a: content at 62 in the frame, 18 on screen. */
    val Onboarding = (62.dp - 18.dp).let { lift ->
        listOf(leftPill(140.dp, lift), SkyCloud(240.dp, (-30).dp, 140.dp, 46.dp, 0.6f, 18, fromRight = true, reverse = true, lift = lift))
    }

    /** 5b Compare: the month line at 62 in the frame, 12 on screen. */
    val Compare = listOf(leftPill(120.dp, 62.dp - 12.dp))

    /** 5d: the week line at 70 in the frame, 12 on screen. */
    val Recap = listOf(leftPill(120.dp, 70.dp - 12.dp))

    /** 6b: the Back disc at 62 in the frame, 8 on screen. */
    val SettleUp = listOf(leftPill(150.dp, 62.dp - 8.dp))

    /** 6g has 6b's header and cloud. */
    val Invite = SettleUp
}

private val Sway = 40.dp

/** Where the cloud sits on a [screenWidth]-wide screen, [sway] of the way through its drift. */
internal fun SkyCloud.rect(screenWidth: Dp, safeTop: Dp, sway: Float): DpRect {
    val shift = Sway * (if (reverse) 1f - sway else sway)
    val left = (if (fromRight) screenWidth - edge - width else edge) + shift
    val y = safeTop + top - lift
    return DpRect(left, y, left + width, y + height)
}

private fun DrawScope.drawCloud(rect: DpRect, alpha: Float) {
    val h = rect.bottom.toPx() - rect.top.toPx()
    drawRoundRect(
        Color.White.copy(alpha = alpha),
        Offset(rect.left.toPx(), rect.top.toPx()),
        Size(rect.right.toPx() - rect.left.toPx(), h),
        CornerRadius(h / 2),
    )
}
