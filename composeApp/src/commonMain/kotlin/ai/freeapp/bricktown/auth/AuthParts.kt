package ai.freeapp.bricktown.auth

import ai.freeapp.bricktown.core.error.ErrorState
import ai.freeapp.bricktown.core.ui.toMessage
import ai.freeapp.bricktown.designsystem.BrickColors
import ai.freeapp.bricktown.designsystem.ChunkyEdge
import ai.freeapp.bricktown.designsystem.ChunkySurface
import ai.freeapp.bricktown.designsystem.LocalBrickType
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/**
 * A chunky text field for the sky world: a small white label above a white
 * field with a solid edge. Shared by Sign in, Create account and Join (typed
 * code and initial). [onImeAction] runs on the keyboard's action key.
 */
@Composable
internal fun BrickTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    isPassword: Boolean = false,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: (() -> Unit)? = null,
) {
    val type = LocalBrickType.current
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label.uppercase(), style = type.label11, color = BrickColors.White)
        ChunkySurface(
            Modifier.fillMaxWidth(),
            fill = BrickColors.White,
            edge = BrickColors.CardEdgeSky,
            edgeWidth = ChunkyEdge.Chip,
            radius = 14.dp,
        ) {
            Box(Modifier.fillMaxWidth().heightIn(min = 50.dp).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = type.body16.copy(color = BrickColors.Ink),
                    cursorBrush = SolidColor(BrickColors.Ink),
                    visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (isPassword) KeyboardType.Password else keyboardType,
                        capitalization = capitalization,
                        autoCorrectEnabled = false,
                        imeAction = imeAction,
                    ),
                    keyboardActions = KeyboardActions(onAny = { onImeAction?.invoke() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** The server's or the ViewModel's sentence, in a white card so it reads on the sky. Nothing when [error] is null. */
@Composable
internal fun ErrorCard(error: ErrorState?, modifier: Modifier = Modifier) = ErrorCard(error?.toMessage(), modifier)

@Composable
internal fun ErrorCard(message: String?, modifier: Modifier = Modifier) {
    if (message == null) return
    val type = LocalBrickType.current
    ChunkySurface(modifier.fillMaxWidth(), fill = BrickColors.White, edge = BrickColors.CardEdgeSky, edgeWidth = ChunkyEdge.Chip, radius = 14.dp) {
        Text(message, style = type.small13, color = BrickColors.Negative, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp))
    }
}
