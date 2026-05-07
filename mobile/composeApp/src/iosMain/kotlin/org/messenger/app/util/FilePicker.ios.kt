package org.messenger.app.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.reinterpret
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIModalPresentationFormSheet
import platform.UniformTypeIdentifiers.UTType
import platform.UniformTypeIdentifiers.UTTypeData
import platform.UniformTypeIdentifiers.UTTypeImage
import platform.UniformTypeIdentifiers.UTTypeMovie
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private class IosFilePickerDelegate(
    private val onPicked: (PickedFile) -> Unit
) : NSObject(), UIDocumentPickerDelegateProtocol {

    override fun documentPicker(
        controller: UIDocumentPickerViewController,
        didPickDocumentsAtURLs: List<*>
    ) {
        val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
        if (url == null) { cleanup(); return }
        val accessed = url.startAccessingSecurityScopedResource()
        try {
            val data: NSData? = NSData.dataWithContentsOfURL(url)
            val name = url.lastPathComponent ?: "file"
            val mime = guessMime(name)
            if (data == null) {
                onPicked(PickedFile(name, mime, ByteArray(0)))
                return
            }
            val length = data.length.toInt()
            val out = ByteArray(length)
            val raw = data.bytes
            if (raw != null && length > 0) {
                val bytePtr = raw.reinterpret<ByteVar>()
                for (i in 0 until length) out[i] = bytePtr[i]
            }
            onPicked(PickedFile(name, mime, out))
        } finally {
            if (accessed) url.stopAccessingSecurityScopedResource()
            cleanup()
        }
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        cleanup()
    }

    private fun cleanup() {
        currentDelegate = null
    }

    companion object {
        var currentDelegate: IosFilePickerDelegate? = null
    }
}

private fun guessMime(name: String): String {
    val ext = name.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "mp4" -> "video/mp4"
        "mov" -> "video/quicktime"
        "pdf" -> "application/pdf"
        "txt" -> "text/plain"
        else -> "application/octet-stream"
    }
}

@Composable
actual fun rememberFilePicker(
    onPicked: (PickedFile) -> Unit,
): FilePickerLauncher = remember {
    object : FilePickerLauncher {
        override fun launch(mimeFilter: String) {
            val types: List<UTType> = when {
                mimeFilter.startsWith("image/") -> listOf(UTTypeImage)
                mimeFilter.startsWith("video/") -> listOf(UTTypeMovie)
                else -> listOf(UTTypeData)
            }
            val picker = UIDocumentPickerViewController(forOpeningContentTypes = types)
            val delegate = IosFilePickerDelegate(onPicked)
            IosFilePickerDelegate.currentDelegate = delegate
            picker.delegate = delegate
            picker.modalPresentationStyle = UIModalPresentationFormSheet

            val root = UIApplication.sharedApplication.keyWindow?.rootViewController
            var top = root
            while (top?.presentedViewController != null) top = top.presentedViewController
            top?.presentViewController(picker, animated = true, completion = null)
        }
    }
}