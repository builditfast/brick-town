package ai.freeapp.bricktown.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.dataWithBytes
import platform.Foundation.writeToURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication

private object IosSharer : Sharer {
    override fun shareText(text: String) = present(listOf(text))

    @OptIn(ExperimentalForeignApi::class)
    override fun shareFile(fileName: String, bytes: ByteArray, mimeType: String) {
        // mimeType is Android's; iOS derives the type from the file extension.
        val url = NSURL.fileURLWithPath(NSTemporaryDirectory() + fileName.substringAfterLast('/'))
        val data = if (bytes.isEmpty()) NSData() else bytes.usePinned { NSData.dataWithBytes(it.addressOf(0), bytes.size.toULong()) }
        data.writeToURL(url, atomically = true)
        present(listOf(url))
    }

    private fun present(items: List<Any>) {
        val root = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return
        var top = root
        while (top.presentedViewController != null) top = top.presentedViewController!!
        top.presentViewController(UIActivityViewController(items, null), animated = true, completion = null)
    }
}

@Composable
actual fun rememberSharer(): Sharer = remember { IosSharer }
