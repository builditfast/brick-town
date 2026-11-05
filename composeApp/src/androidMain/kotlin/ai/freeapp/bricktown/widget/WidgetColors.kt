package ai.freeapp.bricktown.widget

import androidx.compose.ui.graphics.Color

/**
 * Glance cannot read BrickColors (they live in commonMain's design system), so
 * the values frame 5e of design/handoff/Where Money Goes.dc.html uses are
 * mirrored here. The category fills mirror BrickColors.CategoryPairs.
 */
internal object WidgetColors {
    val Pink = Color(0xFFFF8FA6)
    val PinkPanel = Color(0xEBFFFFFF)
    /** 5e's grid lines: white at 16%. */
    val PinkGrid = Color(0x29FFFFFF)
    val GroundLine = Color(0xFFD9617F)
    val Gold = Color(0xFFFFC531)
    val Green = Color(0xFF5AD11E)
    val GreenDeep = Color(0xFF3FA10F)
    val Ink = Color(0xFF4A3426)
    val White = Color(0xFFFFFFFF)

    val Categories: List<Color> = listOf(
        Color(0xFF5AD11E), Color(0xFFFF4D57), Color(0xFF4CC9FF), Color(0xFFA86CFF),
        Color(0xFFFFC531), Color(0xFFFF8C42), Color(0xFF2ED3B7), Color(0xFFF45BD1),
    )
}
