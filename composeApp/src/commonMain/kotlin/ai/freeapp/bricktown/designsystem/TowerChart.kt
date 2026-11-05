package ai.freeapp.bricktown.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * One tower. [bricks] counts every solid brick, the top [overCapBricks] of them striped.
 * [capBricks] is 0 when the category has no cap. [ghostBricks] stack translucent above the solid ones.
 * [dashedOutline] is the fresh month (6c): a full-height dashed frame around the column, with the
 * bricks (ghosts there, dashed too) sitting inside it.
 */
@Immutable
data class TowerColumn(
    val label: String,
    val colorIndex: Int,
    val bricks: Int,
    val capBricks: Int = 0,
    val overCapBricks: Int = 0,
    val ghostBricks: Int = 0,
    val dashedOutline: Boolean = false,
    val contentDescription: String = label,
)

object TowerGeometry {
    const val GapDp = 3
    const val EdgeDp = 3

    /** Height in dp of [bricks] stacked at [brickHeight] with the 3 dp gap between bricks. */
    fun columnHeight(bricks: Int, brickHeight: Int): Int =
        if (bricks <= 0) 0 else bricks * brickHeight + (bricks - 1) * GapDp

    /** Distance of brick [index] (0 = bottom) top edge from the column bottom, in dp. */
    fun brickBottom(index: Int, brickHeight: Int): Int = index * (brickHeight + GapDp)

    /** Where the cap line sits above the column bottom, in dp. */
    fun capLineOffset(capBricks: Int, brickHeight: Int): Int = capBricks * (brickHeight + GapDp)

    /** Stagger delay in ms for brick [brick] of column [column]. */
    fun delayMillis(column: Int, brick: Int): Int = column * 26 + brick * 55
}

private const val BounceMillis = 500

/**
 * Towers of bricks on a ground bar with a label chip under each. [brickHeight] is chosen by the caller
 * (the layout rule lives with the domain). Each column is one full-height tap target.
 */
@Composable
fun TowerChart(
    columns: List<TowerColumn>,
    brickHeight: Int,
    onColumnClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    chartHeight: Dp = 250.dp,
    playIn: Boolean = true,
    onPlayedIn: () -> Unit = {},
) {
    val type = LocalBrickType.current
    val reduce = LocalReduceMotion.current
    val totalMillis = remember(columns.size, columns.maxOfOrNull { it.bricks }) {
        TowerGeometry.delayMillis(columns.size, (columns.maxOfOrNull { it.bricks + it.ghostBricks } ?: 0)) + BounceMillis
    }
    val clock = remember { Animatable(if (reduce || !playIn) totalMillis.toFloat() else 0f) }
    LaunchedEffect(reduce) {
        if (reduce) clock.snapTo(totalMillis.toFloat()) else clock.animateTo(totalMillis.toFloat(), tween(totalMillis, easing = LinearEasing))
        onPlayedIn()
    }
    Column(modifier.padding(horizontal = 6.dp)) {
        Box(Modifier.fillMaxWidth().height(chartHeight)) {
            Canvas(Modifier.fillMaxSize()) {
                drawTowers(columns, brickHeight, clock.value)
            }
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                columns.forEachIndexed { index, column ->
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .semantics { contentDescription = column.contentDescription }
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                role = Role.Button,
                            ) { onColumnClick(index) },
                    )
                }
            }
        }
        Box(
            Modifier
                .padding(top = 6.dp)
                .fillMaxWidth()
                .height(16.dp)
                .background(BrickColors.Ground.edge, RoundedCornerShape(8.dp)),
        ) {
            Box(Modifier.fillMaxWidth().height(12.dp).background(BrickColors.Ground.fill, RoundedCornerShape(8.dp)))
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            columns.forEachIndexed { index, column ->
                Text(
                    column.label,
                    style = type.label10.copy(color = BrickColors.Ink, textAlign = TextAlign.Center, letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .background(BrickColors.TowerLabel, RoundedCornerShape(8.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onColumnClick(index) }
                        .clearAndSetSemantics { }
                        .padding(vertical = 4.dp),
                )
            }
        }
    }
}

private fun DrawScope.drawTowers(columns: List<TowerColumn>, brickHeight: Int, elapsedMillis: Float) {
    if (columns.isEmpty()) return
    val gap = 8.dp.toPx()
    val slotW = (size.width - gap * (columns.size - 1)) / columns.size
    val bh = brickHeight.dp.toPx()
    val g = TowerGeometry.GapDp.dp.toPx()
    val edge = TowerGeometry.EdgeDp.dp.toPx()
    val corner = CornerRadius(4.dp.toPx())
    // 6c: a 3 dp dashed frame (radius 8) and 3 dp of padding, so the bricks sit 6 dp in from the column.
    val frame = 3.dp.toPx()
    val inset = 2 * frame
    columns.forEachIndexed { k, col ->
        val colX = k * (slotW + gap)
        val pair = BrickColors.CategoryPairs[col.colorIndex.mod(8)]
        val x = if (col.dashedOutline) colX + inset else colX
        val bottom = if (col.dashedOutline) size.height - inset else size.height
        val colW = if (col.dashedOutline) slotW - 2 * inset else slotW
        if (col.dashedOutline) {
            drawRoundRect(
                Color.White.copy(alpha = 0.55f), Offset(colX + frame / 2, frame / 2), Size(slotW - frame, size.height - frame), CornerRadius(8.dp.toPx()),
                style = Stroke(frame, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2 * frame, frame))),
            )
        }
        val total = col.bricks + col.ghostBricks
        for (i in 0 until total) {
            val p = ((elapsedMillis - TowerGeometry.delayMillis(k, i)) / BounceMillis).coerceIn(0f, 1f)
            if (p <= 0f) continue
            val s = if (p < 0.6f) 0.4f + 0.72f * (p / 0.6f) else 1.12f - 0.12f * ((p - 0.6f) / 0.4f)
            val a = (p / 0.6f).coerceIn(0f, 1f)
            val top = bottom - (i + 1) * bh - i * g
            val ghost = i >= col.bricks
            val over = !ghost && i >= col.bricks - col.overCapBricks
            scale(s, s, pivot = Offset(x + colW / 2, top + bh)) {
                when {
                    ghost -> {
                        drawRoundRect(pair.fill.copy(alpha = 0.4f * a), Offset(x, top), Size(colW, bh), corner)
                        if (col.dashedOutline) {
                            val line = 2.dp.toPx()
                            drawRoundRect(
                                pair.fill.copy(alpha = a), Offset(x + line / 2, top + line / 2), Size(colW - line, bh - line), corner,
                                style = Stroke(line, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2 * line, line))),
                            )
                        }
                    }
                    over -> drawOverCapBrick(x, top, colW, bh, edge, corner, a)
                    else -> {
                        drawRoundRect(pair.edge.copy(alpha = a), Offset(x, top), Size(colW, bh), corner)
                        drawRoundRect(pair.fill.copy(alpha = a), Offset(x, top), Size(colW, bh - edge), corner)
                    }
                }
            }
        }
        if (col.capBricks > 0) {
            val y = bottom - TowerGeometry.capLineOffset(col.capBricks, brickHeight).dp.toPx() - 1.5.dp.toPx()
            drawLine(
                Color.White, Offset(x - 4.dp.toPx(), y), Offset(x + colW + 4.dp.toPx(), y), 3.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
            )
        }
    }
}

private fun DrawScope.drawOverCapBrick(x: Float, top: Float, w: Float, h: Float, edge: Float, corner: CornerRadius, alpha: Float) {
    drawRoundRect(BrickColors.OverCapEdge.copy(alpha = alpha), Offset(x, top), Size(w, h), corner)
    val face = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(x, top, x + w, top + h - edge, corner)) }
    clipPath(face) {
        drawRect(BrickColors.OverCapStripeA.copy(alpha = alpha), Offset(x, top), Size(w, h - edge))
        // repeating-linear-gradient(45deg, A 0 4px, B 4px 8px): bands 8 dp apart across the diagonal.
        val period = 8.dp.toPx() * 1.4142135f
        val band = period / 2f
        val rise = h - edge
        var start = x - rise
        val stripe = Path()
        while (start < x + w) {
            stripe.reset()
            stripe.moveTo(start + band, top + rise)
            stripe.lineTo(start + band * 2f, top + rise)
            stripe.lineTo(start + band * 2f + rise, top)
            stripe.lineTo(start + band + rise, top)
            stripe.close()
            drawPath(stripe, BrickColors.OverCapStripeB.copy(alpha = alpha))
            start += period
        }
    }
}
