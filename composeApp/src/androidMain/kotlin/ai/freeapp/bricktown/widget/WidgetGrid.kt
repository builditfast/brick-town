package ai.freeapp.bricktown.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.DpSize
import kotlin.math.roundToInt

private const val CELL_DP = 48f
private const val LINE_DP = 3f

/**
 * Frame 5e's pink card carries a 48 dp grid of 3 dp lines (two CSS gradients).
 * Glance has no gradients, so the grid is drawn once at the widget's exact size.
 * The lines overlap at each crossing, as the two CSS layers do.
 */
internal fun gridBitmap(context: Context, size: DpSize): Bitmap {
    val density = context.resources.displayMetrics.density
    val width = (size.width.value * density).roundToInt().coerceAtLeast(1)
    val height = (size.height.value * density).roundToInt().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint().apply { color = WidgetColors.PinkGrid.toArgb() }
    val step = CELL_DP * density
    val line = LINE_DP * density
    var y = 0f
    while (y < height) {
        canvas.drawRect(0f, y, width.toFloat(), y + line, paint)
        y += step
    }
    var x = 0f
    while (x < width) {
        canvas.drawRect(x, 0f, x + line, height.toFloat(), paint)
        x += step
    }
    return bitmap
}
