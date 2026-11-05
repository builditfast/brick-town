package ai.freeapp.bricktown.core.ui

import ai.freeapp.bricktown.core.config.LegalLinks
import ai.freeapp.bricktown.core.error.ErrorState
import ai.freeapp.bricktown.designsystem.Dimens
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation

/*
 * The few pieces every form screen shares, so Sign in, Create account and
 * Change password look and behave the same. Nothing here holds state.
 */

/** A single-line field. [onImeAction] runs on the keyboard's action key (Next or Done). */
@Composable
fun FormTextField(
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
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (isPassword) KeyboardType.Password else keyboardType,
            capitalization = capitalization,
            autoCorrectEnabled = false,
            imeAction = imeAction,
        ),
        // Without a handler the key keeps its default: Next moves to the next field.
        keyboardActions = KeyboardActions(
            onAny = { if (onImeAction != null) onImeAction() else defaultKeyboardAction(imeAction) },
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

/** The form's main action. While [busy] it is disabled and says [busyText], so a double tap sends once. */
@Composable
fun SubmitButton(
    text: String,
    busyText: String,
    busy: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = !busy,
        modifier = modifier.fillMaxWidth().heightIn(min = Dimens.buttonHeight),
    ) {
        Text(if (busy) busyText else text)
    }
}

@Composable
fun FormError(error: ErrorState?, modifier: Modifier = Modifier) {
    if (error == null) return
    Text(
        text = error.toMessage(),
        color = MaterialTheme.colors.error,
        style = MaterialTheme.typography.body2,
        modifier = modifier,
    )
}

/**
 * Opens the server's privacy policy and terms of use in the browser. On a
 * phone with no browser (a kiosk or work profile, an emulator image without
 * one) `openUri` throws and the app crashed (2026-10-21), so the address is
 * shown in a dialog instead. [linkColor] is for a coloured background: the
 * default Material primary is unreadable on the sky screens.
 */
@Composable
fun LegalLinksRow(
    modifier: Modifier = Modifier,
    linkColor: Color = Color.Unspecified,
    link: @Composable (text: String, onClick: () -> Unit) -> Unit = { text, onClick ->
        TextButton(onClick = onClick, modifier = Modifier.heightIn(min = Dimens.buttonHeight)) {
            // A coloured link on a coloured world is underlined so it still reads as a link.
            Text(text, color = linkColor, textDecoration = if (linkColor == Color.Unspecified) null else TextDecoration.Underline)
        }
    },
) {
    val uriHandler = LocalUriHandler.current
    var unopened by remember { mutableStateOf<String?>(null) }
    val open = { url: String ->
        try {
            uriHandler.openUri(url)
        } catch (noBrowser: IllegalArgumentException) {
            unopened = url
        }
    }
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(Dimens.space8)) {
        link("Privacy policy") { open(LegalLinks.privacyPolicy) }
        link("Terms of use") { open(LegalLinks.termsOfUse) }
    }
    unopened?.let { url ->
        AlertDialog(
            onDismissRequest = { unopened = null },
            title = { Text("No browser on this phone") },
            text = { SelectionContainer { Text("The page is at $url") } },
            confirmButton = { TextButton(onClick = { unopened = null }) { Text("OK") } },
        )
    }
}
