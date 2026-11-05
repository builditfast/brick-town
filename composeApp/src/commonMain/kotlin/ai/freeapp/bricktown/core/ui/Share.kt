package ai.freeapp.bricktown.core.ui

import androidx.compose.runtime.Composable

/** Opens the system share sheet. Obtained through [rememberSharer] because Android needs a Context. */
interface Sharer {
    fun shareText(text: String)
    fun shareFile(fileName: String, bytes: ByteArray, mimeType: String)
}

@Composable
expect fun rememberSharer(): Sharer
