package ai.freeapp.bricktown.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding

/**
 * A member disc with an initial. [color] is a member colour; [ring] is the white outline used when avatars overlap.
 * [edge], when set, is a 4 dp disc of that colour under the avatar, the handoff's `box-shadow: 0 4px 0`.
 */
@Composable
fun Avatar(
    initial: String,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 34.dp,
    ring: Dp = 3.dp,
    edge: Color? = null,
    textSize: TextUnit = (size.value * 0.36f).sp,
    contentDescription: String = initial,
) {
    val type = LocalBrickType.current
    Box(
        modifier
            .size(size)
            .drawBehind { if (edge != null) drawCircle(edge, center = center.copy(y = center.y + ChunkyEdge.Chip.toPx())) }
            .background(BrickColors.White, CircleShape)
            .padding(ring)
            .background(color, CircleShape)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Text(initial.take(1).uppercase(), style = type.label11.copy(color = BrickColors.White, fontSize = textSize, letterSpacing = 0.sp))
    }
}

/** The footer line on Welcome and Settings. Plain text so it never takes a tap. */
@Composable
fun MadeWithFreeapp(modifier: Modifier = Modifier, color: Color = BrickColors.Muted) {
    val type = LocalBrickType.current
    Text(
        "Made with freeapp.ai",
        style = type.small12.copy(color = color, textAlign = TextAlign.Center),
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
    )
}
