package ai.freeapp.bricktown.designsystem

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.alexzhirkevich.qrose.rememberQrCodePainter

/** A black on white QR for [text]. Scanners need the contrast, so the colours are not themed. */
@Composable
fun QrCode(text: String, modifier: Modifier = Modifier, size: Dp = 180.dp, contentDescription: String = "QR code") {
    Image(
        painter = rememberQrCodePainter(text),
        contentDescription = null,
        modifier = modifier.size(size).semantics { this.contentDescription = contentDescription },
    )
}
