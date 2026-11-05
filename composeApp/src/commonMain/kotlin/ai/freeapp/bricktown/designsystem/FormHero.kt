package ai.freeapp.bricktown.designsystem

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A typed amount as the handoff draws it on the first run and the brick form (5a, 6e): left aligned, the sign
 * and the euro sign at half [fontSize] and faded beside the digits, which share their baseline, then the blinking
 * caret. The centred [HeroAmount] stays for the screens whose frames centre it.
 */
@Composable
fun FormHero(
    sign: String,
    symbol: String,
    symbolAfter: Boolean,
    digits: String,
    spoken: String,
    modifier: Modifier = Modifier,
    fontSize: Float = 64f,
) {
    val type = LocalBrickType.current
    val reduce = LocalReduceMotion.current
    val blink by rememberInfiniteTransition(label = "caret").animateFloat(
        1f, 0f,
        infiniteRepeatable(keyframes { durationMillis = 1000; 1f at 0; 1f at 499; 0f at 500; 0f at 999 }, RepeatMode.Restart),
        label = "caret",
    )
    val density = LocalDensity.current
    val fit = (9f / (sign.length + symbol.length + digits.length).coerceAtLeast(9)).coerceAtLeast(0.5f)
    val big = fontSize * fit
    val small = fontSize / 2 * fit
    CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale.coerceAtMost(1.15f))) {
        Row(modifier.semantics { contentDescription = spoken }, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.clearAndSetSemantics { }, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!symbolAfter) Text(sign + symbol, Modifier.alignByBaseline().lineHeightOne(small.sp).alpha(0.8f), maxLines = 1, softWrap = false, style = heroLine(type.hero72, small))
                Text(digits, Modifier.alignByBaseline().lineHeightOne(big.sp), maxLines = 1, softWrap = false, style = heroLine(type.hero72, big))
                if (symbolAfter) Text(symbol, Modifier.alignByBaseline().lineHeightOne(small.sp).alpha(0.8f), maxLines = 1, softWrap = false, style = heroLine(type.hero72, small))
            }
            Box(
                Modifier
                    .width((5f * big / 72f).dp)
                    .height((56f * big / 72f).dp)
                    .drawBehind {
                        val a = if (reduce) 1f else blink
                        drawRoundRect(BrickColors.White.copy(alpha = a), cornerRadius = CornerRadius(size.width / 2))
                    },
            )
        }
    }
}

/** White Fredoka at [size]; [lineHeightOne] gives it the handoff's line height 1. */
private fun heroLine(base: TextStyle, size: Float): TextStyle =
    base.copy(fontSize = size.sp, color = BrickColors.White).onWorld(hero = true)
