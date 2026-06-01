package org.messenger.app.util

import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO

actual fun readImageFromClipboard(): ClipboardImage? {
    return try {
        val cb = Toolkit.getDefaultToolkit().systemClipboard
        val contents = cb.getContents(null) ?: return null

        // Сначала пробуем image flavor
        if (contents.isDataFlavorSupported(DataFlavor.imageFlavor)) {
            val img = contents.getTransferData(DataFlavor.imageFlavor) as? java.awt.Image ?: return null
            val buffered = toBufferedImage(img)
            val baos = ByteArrayOutputStream()
            ImageIO.write(buffered, "png", baos)
            val bytes = baos.toByteArray()
            if (bytes.isNotEmpty()) {
                return ClipboardImage(
                    bytes = bytes,
                    mimeType = "image/png",
                    suggestedName = "clipboard_${System.currentTimeMillis()}.png",
                )
            }
        }

        // Фолбэк: файл из буфера (если скопировали файл изображения)
        if (contents.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
            @Suppress("UNCHECKED_CAST")
            val files = contents.getTransferData(DataFlavor.javaFileListFlavor) as List<File>
            val file = files.firstOrNull { it.isFile } ?: return null
            val name = file.name.lowercase()
            val mime = when {
                name.endsWith(".png") -> "image/png"
                name.endsWith(".jpg") || name.endsWith(".jpeg") -> "image/jpeg"
                name.endsWith(".gif") -> "image/gif"
                name.endsWith(".webp") -> "image/webp"
                name.endsWith(".bmp") -> "image/bmp"
                else -> return null
            }
            return ClipboardImage(
                bytes = file.readBytes(),
                mimeType = mime,
                suggestedName = file.name,
            )
        }
        null
    } catch (_: Exception) {
        null
    }
}

private fun toBufferedImage(img: java.awt.Image): BufferedImage {
    if (img is BufferedImage) return img
    val w = img.getWidth(null).coerceAtLeast(1)
    val h = img.getHeight(null).coerceAtLeast(1)
    val buffered = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
    val g = buffered.createGraphics()
    g.drawImage(img, 0, 0, null)
    g.dispose()
    return buffered
}