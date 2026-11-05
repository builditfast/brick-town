package ai.freeapp.bricktown.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** A solid fill and the darker bottom edge that makes a surface read as a toy brick. */
@Immutable
data class BrickPair(val fill: Color, val edge: Color)

/** Every colour of the Brick Town handoff. Screens read these, never a raw Color(0x...). */
object BrickColors {
    // Worlds
    val WorldPink = Color(0xFFFF8FA6)
    val WorldPinkLine = Color.White.copy(alpha = 0.16f)
    val SkyTop = Color(0xFF49C7FF)
    val SkyMid = Color(0xFF7ED8FF)
    val SkyBottom = Color(0xFF9FE3FF)
    val GoldTop = Color(0xFFFFC531)
    val GoldBottom = Color(0xFFFFD978)
    val NightTop = Color(0xFF2B1F5C)
    val NightMid = Color(0xFF6A2A74)
    val NightBottom = Color(0xFFC8486A)
    val Ground = BrickPair(Color(0xFFD9617F), Color(0xFFB84A66))

    // Category and member fill/edge pairs, in palette order
    val Groceries = BrickPair(Color(0xFF5AD11E), Color(0xFF3FA10F))
    val EatingOut = BrickPair(Color(0xFFFF4D57), Color(0xFFC9242E))
    val Home = BrickPair(Color(0xFF4CC9FF), Color(0xFF1AA7E6))
    val Shopping = BrickPair(Color(0xFFA86CFF), Color(0xFF7C45D6))
    val Transport = BrickPair(Color(0xFFFFC531), Color(0xFFE59E00))
    val Subscriptions = BrickPair(Color(0xFFFF8C42), Color(0xFFD86614))
    val Seventh = BrickPair(Color(0xFF2ED3B7), Color(0xFF1AA58F))
    val Eighth = BrickPair(Color(0xFFF45BD1), Color(0xFFC23AA6))
    val CategoryPairs: List<BrickPair> =
        listOf(Groceries, EatingOut, Home, Shopping, Transport, Subscriptions, Seventh, Eighth)

    // Over-cap bricks: stripes of these two inside a darker edge
    val OverCapStripeA = Color(0xFFFF4D57)
    val OverCapStripeB = Color(0xFFC9242E)
    val OverCapEdge = Color(0xFF9C1A22)

    // Member colours by index 0..7
    val MemberColors: List<Color> = listOf(
        Color(0xFF49C7FF), Color(0xFFA86CFF), Color(0xFF5AD11E), Color(0xFFFF8C42),
        Color(0xFFF45BD1), Color(0xFF2ED3B7), Color(0xFFFFC531), Color(0xFFFF4D57),
    )

    // UI
    val Ink = Color(0xFF4A3426)
    val Muted = Color(0xFF8A7566)
    val Disabled = Color(0xFFC9BDB3)
    val DisabledEdge = Color(0xFFB0A49A)
    val CardEdge = Color(0xFFE6DCD3)
    val CardEdgeSky = Color(0xFFD6EBF7)
    val SoftFill = Color(0xFFF7F3EF)
    val SoftFillAlt = Color(0xFFF3EEE9)
    val SoftSky = Color(0xFFEAF6FF)
    val SoftSkyEdge = Color(0xFFC9E3F2)
    /** 4a and 5b: the dashed drop zone on the import card. */
    val DropZone = Color(0xFFF4FBFF)
    /** Trail: a locked badge node and its lock icon (handoff README, Trail tab). */
    val TrailLocked = Color(0xFFDCEEF7)
    val TrailLockIcon = Color(0xFF8FB3C7)
    val Green = BrickPair(Color(0xFF5AD11E), Color(0xFF3FA10F))
    val Negative = Color(0xFFE8557D)
    val DeleteArea = Color(0xFFFF7E9D)
    val Gold = BrickPair(Color(0xFFFFC531), Color(0xFFE59E00))
    val LinkBlue = Color(0xFF1AA7E6)
    val PartnerPurple = Color(0xFFA86CFF)
    val YouBlue = Color(0xFF49C7FF)
    val Toast = BrickPair(Color(0xFF4A3426), Color(0xFF2E1F16))
    val Scrim = Color(0xFF4A3426).copy(alpha = 0.45f)
    val TabActivePill = Color(0xFFFFF5F7)
    val TabMonth = Color(0xFFFF7E9D)
    /** 6d: the "Nothing was added" note behind Negative text. */
    val NegativeSoft = Color(0xFFFFF0F3)
    /** 6d: the "Type the bricks by hand" tile, a pink euro on a pale pink square. */
    val HandTile = BrickPair(Color(0xFFFFF5F7), Color(0xFFFF7E9D))
    val BarTopBorder = Color(0xFFF1E9E3)
    val White = Color.White
    val TextShadow = Color.Black.copy(alpha = 0.15f)
    /** Tower label under the chart: rgba(255,255,255,.5) in the handoff, over the world background. */
    val TowerLabel = Color.White.copy(alpha = 0.5f)
    /** Hint card over a world background: rgba(255,255,255,.3) in the handoff. */
    val HintFill = Color.White.copy(alpha = 0.3f)

    val SkyBrush: Brush
        get() = Brush.verticalGradient(0f to SkyTop, 0.7f to SkyMid, 1f to SkyBottom)
    val GoldBrush: Brush
        get() = Brush.verticalGradient(listOf(GoldTop, GoldBottom))
}
