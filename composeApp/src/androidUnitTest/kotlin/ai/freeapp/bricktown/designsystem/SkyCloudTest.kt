package ai.freeapp.bricktown.designsystem

import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/** The handoff's sky clouds: white pills that sway 40 px back and forth, placed per frame (README, 5a). */
class SkyCloudTest {

    private val left = SkyClouds.Onboarding[0]
    private val right = SkyClouds.Onboarding[1]

    @Test
    fun fiveADrawsAPillOnTheLeftAndOneOnTheRight() {
        assertEquals(SkyCloud(140.dp, (-20).dp, 120.dp, 44.dp, 0.7f, 14, lift = 44.dp), left)
        assertEquals(SkyCloud(240.dp, (-30).dp, 140.dp, 46.dp, 0.6f, 18, fromRight = true, reverse = true, lift = 44.dp), right)
    }

    @Test
    fun theLeftPillHangsTwentyOffTheEdgeAndSwaysFortyRight() {
        assertEquals(DpRect((-20).dp, 96.dp, 100.dp, 140.dp), left.rect(402.dp, 0.dp, sway = 0f))
        assertEquals(DpRect(20.dp, 96.dp, 140.dp, 140.dp), left.rect(402.dp, 0.dp, sway = 1f))
    }

    @Test
    fun theRightPillHangsThirtyOffTheEdgeAndStartsAtTheFarEndOfItsSway() {
        assertEquals(DpRect(332.dp, 196.dp, 472.dp, 242.dp), right.rect(402.dp, 0.dp, sway = 0f))
        assertEquals(DpRect(292.dp, 196.dp, 432.dp, 242.dp), right.rect(402.dp, 0.dp, sway = 1f))
    }

    @Test
    fun eachScreensCloudKeepsItsPlaceBesideTheContentItsFrameDrawsItNextTo() {
        // Frame cloud top minus how much higher the screen's content starts than the frame's.
        assertEquals(120.dp - (70.dp - 12.dp), SkyClouds.Recap.single().rect(402.dp, 0.dp, 0f).top)
        assertEquals(120.dp - (62.dp - 12.dp), SkyClouds.Compare.single().rect(402.dp, 0.dp, 0f).top)
        assertEquals(150.dp - (62.dp - 8.dp), SkyClouds.SettleUp.single().rect(402.dp, 0.dp, 0f).top)
        assertEquals(SkyClouds.SettleUp, SkyClouds.Invite)
    }

    @Test
    fun aCloudKeepsItsPlaceBelowTheStatusBar() {
        assertEquals(120.dp, left.rect(402.dp, 24.dp, sway = 0f).top)
    }
}
