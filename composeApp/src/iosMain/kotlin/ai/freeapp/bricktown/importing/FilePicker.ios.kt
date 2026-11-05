package ai.freeapp.bricktown.importing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UniformTypeIdentifiers.UTTypeData
import platform.darwin.NSObject
import platform.posix.memcpy

/** UIKit holds a picker's delegate weakly, so the delegate lives as long as the composable that opened it. */
private class PickerDelegate(private val onPicked: () -> ((PickedFile?) -> Unit)) : NSObject(), UIDocumentPickerDelegateProtocol {
    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
        onPicked()(url?.let(::readFile))
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) = onPicked()(null)
}

@OptIn(ExperimentalForeignApi::class)
private fun readFile(url: NSURL): PickedFile? {
    val scoped = url.startAccessingSecurityScopedResource()
    try {
        val data = NSData.dataWithContentsOfURL(url) ?: return null
        val size = minOf(data.length.toInt(), MAX_PICKED_BYTES + 1)
        val bytes = ByteArray(size)
        if (size > 0) bytes.usePinned { memcpy(it.addressOf(0), data.bytes, size.toULong()) }
        return PickedFile(url.lastPathComponent ?: "statement", bytes)
    } finally {
        if (scoped) url.stopAccessingSecurityScopedResource()
    }
}

@Composable
actual fun rememberStatementPicker(onPicked: (PickedFile?) -> Unit): () -> Unit {
    val latest by rememberUpdatedState(onPicked)
    val delegate = remember { PickerDelegate { latest } }
    return {
        val root = UIApplication.sharedApplication.keyWindow?.rootViewController
        if (root != null) {
            var top = root
            while (top?.presentedViewController != null) top = top.presentedViewController
            val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeData), asCopy = true)
            picker.delegate = delegate
            top?.presentViewController(picker, animated = true, completion = null)
        }
    }
}
