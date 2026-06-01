package org.messenger.app.util

data class ClipboardImage(
    val bytes: ByteArray,
    val mimeType: String,
    val suggestedName: String,
)

expect fun readImageFromClipboard(): ClipboardImage?