package ai.freeapp.bricktown.importing

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberStatementPicker(onPicked: (PickedFile?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val latest by rememberUpdatedState(onPicked)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        latest(uri?.let { read(context, it) })
    }
    // Banks label their CSV exports text/csv, text/plain, application/vnd.ms-excel or octet-stream: take any type.
    return { launcher.launch(arrayOf("*/*")) }
}

private fun read(context: Context, uri: Uri): PickedFile? = try {
    val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    } ?: uri.lastPathSegment.orEmpty().substringAfterLast('/')
    val bytes = context.contentResolver.openInputStream(uri)?.use { stream ->
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (out.size() <= MAX_PICKED_BYTES) {
            val n = stream.read(buffer, 0, minOf(buffer.size, MAX_PICKED_BYTES + 1 - out.size()))
            if (n < 0) break
            out.write(buffer, 0, n)
        }
        out.toByteArray()
    }
    // A file over the cap comes back as one byte too long and reads as "not a statement".
    if (bytes == null) null else PickedFile(name.ifBlank { "statement" }, bytes)
} catch (unreadable: java.io.IOException) {
    null
} catch (denied: SecurityException) {
    null
}
