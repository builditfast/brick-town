package ai.freeapp.bricktown.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import bricktown.composeapp.generated.resources.Res
import bricktown.composeapp.generated.resources.fredoka_bold
import bricktown.composeapp.generated.resources.fredoka_medium
import bricktown.composeapp.generated.resources.fredoka_semibold
import bricktown.composeapp.generated.resources.nunito_black
import bricktown.composeapp.generated.resources.nunito_bold
import bricktown.composeapp.generated.resources.nunito_extrabold
import org.jetbrains.compose.resources.Font as ResourceFont

/** Fredoka carries numbers, Nunito carries UI text. Loaded from the bundled static TTFs. */
@Immutable
class BrickFonts(val fredoka: FontFamily, val nunito: FontFamily)

@Composable
fun rememberBrickFonts(): BrickFonts {
    val f500 = ResourceFont(Res.font.fredoka_medium, FontWeight.Medium)
    val f600 = ResourceFont(Res.font.fredoka_semibold, FontWeight.SemiBold)
    val f700 = ResourceFont(Res.font.fredoka_bold, FontWeight.Bold)
    val n700 = ResourceFont(Res.font.nunito_bold, FontWeight.Bold)
    val n800 = ResourceFont(Res.font.nunito_extrabold, FontWeight.ExtraBold)
    val n900 = ResourceFont(Res.font.nunito_black, FontWeight.Black)
    return remember(f500, f600, f700, n700, n800, n900) {
        BrickFonts(FontFamily(f500, f600, f700), FontFamily(n700, n800, n900))
    }
}

/** Text styles from the handoff type scale. Sizes are fixed sp, display numbers clamp under big font scales. */
@Immutable
class BrickType(private val fonts: BrickFonts) {
    private fun num(size: TextUnit, weight: FontWeight = FontWeight.Bold) =
        TextStyle(fontFamily = fonts.fredoka, fontWeight = weight, fontSize = size, letterSpacing = 0.sp)

    private fun ui(size: TextUnit, weight: FontWeight = FontWeight.ExtraBold) =
        TextStyle(fontFamily = fonts.nunito, fontWeight = weight, fontSize = size)

    val hero72 = num(72.sp)
    val hero64 = num(64.sp)
    val hero46 = num(46.sp)
    val hero44 = num(44.sp)
    val hero40 = num(40.sp)
    /** Fredoka 30 at line height 1: the Settle up balance. */
    val headline30 = num(30.sp).copy(lineHeight = 30.sp)
    val sheetTitle = num(28.sp)
    /** Fredoka 26 at line height 1.1: the import failure heading (6d). */
    val heading26 = num(26.sp).copy(lineHeight = 28.6.sp)
    val key = num(24.sp, FontWeight.SemiBold)
    val row22 = num(22.sp)
    val row20 = num(20.sp)
    val row17 = num(17.sp)

    val title18 = ui(18.sp, FontWeight.Black)
    val body16 = ui(16.sp)
    val body15 = ui(15.sp)
    val body14 = ui(14.sp)
    val small13 = ui(13.sp)
    val small12 = ui(12.sp)
    val label11 = ui(11.sp, FontWeight.Black).copy(letterSpacing = 0.06.em)
    val label10 = ui(10.sp, FontWeight.Black).copy(letterSpacing = 0.06.em)
}

/** White text on a world background carries a hard 2 dp drop shadow (4 dp for the hero). */
fun TextStyle.onWorld(hero: Boolean = false): TextStyle =
    copy(shadow = Shadow(BrickColors.TextShadow, Offset(0f, if (hero) 4f else 2f), 0f))

/**
 * CSS `text-wrap: pretty` for a short paragraph: the last two words are joined by a no-break space, so the last
 * line never holds one word alone. A last line that already holds both is unchanged.
 */
fun String.pretty(): String {
    val gap = trimEnd().lastIndexOf(' ')
    return if (gap < 0) this else replaceRange(gap, gap + 1, "\u00A0")
}

/**
 * CSS `line-height: 1` for one line of text: it takes exactly [fontSize] of height, with the font's own box
 * centred in it as a browser centres it. A style's lineHeight cannot do this, because Compose pads any line
 * shorter than the font so that no glyph clips; Fredoka at 64 sp stays 78 dp tall. The font's empty top and
 * bottom overflow instead, and the baseline moves with the text, so baseline alignment still holds.
 */
fun Modifier.lineHeightOne(fontSize: TextUnit): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
    val height = constraints.constrainHeight(fontSize.roundToPx())
    layout(placeable.width, height) { placeable.place(0, (height - placeable.height) / 2) }
}

val LocalBrickType = staticCompositionLocalOf<BrickType> { error("Wrap the UI in AppTheme") }
