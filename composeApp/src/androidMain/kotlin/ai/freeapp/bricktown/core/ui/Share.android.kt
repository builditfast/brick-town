package ai.freeapp.bricktown.core.ui

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File

private class AndroidSharer(private val context: Context) : Sharer {
    override fun shareText(text: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        launch(send)
    }

    override fun shareFile(fileName: String, bytes: ByteArray, mimeType: String) {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        // A name from outside must not climb out of cache/shared.
        val file = File(dir, File(fileName).name).apply { writeBytes(bytes) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.share", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType(mimeType)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        launch(send)
    }

    private fun launch(send: Intent) {
        val chooser = Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}

@Composable
actual fun rememberSharer(): Sharer {
    val context = LocalContext.current
    return remember(context) { AndroidSharer(context) }
}
