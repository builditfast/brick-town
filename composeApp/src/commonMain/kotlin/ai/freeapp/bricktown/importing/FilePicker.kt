package ai.freeapp.bricktown.importing

import androidx.compose.runtime.Composable

/** A file the person chose, read whole. Statements are small; the platform caps the size it reads. */
class PickedFile(val name: String, val bytes: ByteArray)

/** Largest statement read into memory. Bigger files are refused as not a statement. */
const val MAX_PICKED_BYTES = 5 * 1024 * 1024

/**
 * The system document picker for a CSV or PDF. Returns the function that opens it.
 * [onPicked] gets null when the person cancels or the file cannot be read.
 */
@Composable
expect fun rememberStatementPicker(onPicked: (PickedFile?) -> Unit): () -> Unit
