package ai.freeapp.bricktown

import ai.freeapp.bricktown.designsystem.BrickColors
import ai.freeapp.bricktown.designsystem.BrickPair
import ai.freeapp.bricktown.designsystem.TowerGeometry
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.test.Test
import kotlin.test.assertEquals

class DesignSystemTest {
    private fun hex(color: Color): String = "#" + (color.toArgb() and 0xFFFFFF).toString(16).padStart(6, '0')

    private fun assertPair(fill: String, edge: String, pair: BrickPair) {
        assertEquals(fill, hex(pair.fill))
        assertEquals(edge, hex(pair.edge))
    }

    @Test
    fun categoryPalettePairsMatchReadme() {
        val expected = listOf(
            "#5ad11e" to "#3fa10f", "#ff4d57" to "#c9242e", "#4cc9ff" to "#1aa7e6", "#a86cff" to "#7c45d6",
            "#ffc531" to "#e59e00", "#ff8c42" to "#d86614", "#2ed3b7" to "#1aa58f", "#f45bd1" to "#c23aa6",
        )
        assertEquals(expected.size, BrickColors.CategoryPairs.size)
        expected.forEachIndexed { i, (fill, edge) -> assertPair(fill, edge, BrickColors.CategoryPairs[i]) }
    }

    @Test
    fun uiPairsMatchReadme() {
        assertPair("#d9617f", "#b84a66", BrickColors.Ground)
        assertPair("#5ad11e", "#3fa10f", BrickColors.Green)
        assertPair("#ffc531", "#e59e00", BrickColors.Gold)
        assertPair("#4a3426", "#2e1f16", BrickColors.Toast)
        assertEquals("#4a3426", hex(BrickColors.Ink))
        assertEquals("#8a7566", hex(BrickColors.Muted))
        assertEquals("#c9bdb3", hex(BrickColors.Disabled))
        assertEquals("#b0a49a", hex(BrickColors.DisabledEdge))
        assertEquals("#e6dcd3", hex(BrickColors.CardEdge))
        assertEquals("#d6ebf7", hex(BrickColors.CardEdgeSky))
        assertEquals("#f7f3ef", hex(BrickColors.SoftFill))
        assertEquals("#f3eee9", hex(BrickColors.SoftFillAlt))
        assertEquals("#eaf6ff", hex(BrickColors.SoftSky))
        assertEquals("#c9e3f2", hex(BrickColors.SoftSkyEdge))
        assertEquals("#e8557d", hex(BrickColors.Negative))
        assertEquals("#1aa7e6", hex(BrickColors.LinkBlue))
        assertEquals("#a86cff", hex(BrickColors.PartnerPurple))
        assertEquals("#49c7ff", hex(BrickColors.YouBlue))
        assertEquals("#9c1a22", hex(BrickColors.OverCapEdge))
        assertEquals("#ff4d57", hex(BrickColors.OverCapStripeA))
        assertEquals("#c9242e", hex(BrickColors.OverCapStripeB))
    }

    @Test
    fun worldColorsMatchReadme() {
        assertEquals("#ff8fa6", hex(BrickColors.WorldPink))
        assertEquals("#2b1f5c", hex(BrickColors.NightTop))
        assertEquals("#6a2a74", hex(BrickColors.NightMid))
        assertEquals("#c8486a", hex(BrickColors.NightBottom))
    }

    @Test
    fun memberColorsIndexZeroToSeven() {
        val expected = listOf("#49c7ff", "#a86cff", "#5ad11e", "#ff8c42", "#f45bd1", "#2ed3b7", "#ffc531", "#ff4d57")
        assertEquals(expected, BrickColors.MemberColors.map(::hex))
    }

    @Test
    fun towerColumnHeightIsBricksPlusThreeDpGaps() {
        assertEquals(0, TowerGeometry.columnHeight(0, 14))
        assertEquals(14, TowerGeometry.columnHeight(1, 14))
        assertEquals(14 * 14 + 13 * 3, TowerGeometry.columnHeight(14, 14))
        assertEquals(3 * 9 + 2 * 3, TowerGeometry.columnHeight(3, 9))
    }

    @Test
    fun capLineAndBrickPositionsUseBrickPlusGap() {
        assertEquals(10 * 17, TowerGeometry.capLineOffset(10, 14))
        assertEquals(0, TowerGeometry.brickBottom(0, 14))
        assertEquals(3 * 17, TowerGeometry.brickBottom(3, 14))
    }

    @Test
    fun bounceStaggerIsColumnTimes26PlusBrickTimes55() {
        assertEquals(0, TowerGeometry.delayMillis(0, 0))
        assertEquals(2 * 26 + 3 * 55, TowerGeometry.delayMillis(2, 3))
    }
}
