package org.messenger.app.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.io.FilenameFilter
import java.nio.file.Files

@Composable
actual fun rememberFilePicker(
    onPicked: (PickedFile) -> Unit,
): FilePickerLauncher = remember {
    object : FilePickerLauncher {
        override fun launch(mimeFilter: String) {
            try {
                val dialog = FileDialog(null as Frame?, "Выберите файл", FileDialog.LOAD).apply {
                    // mimeFilter вида "image/*", "*/*", "application/pdf"
                    val (type, sub) = parseMime(mimeFilter)
                    if (type != null && type != "*") {
                        filenameFilter = FilenameFilter { _, name ->
                            val ext = name.substringAfterLast('.', "").lowercase()
                            matchExtension(type, sub, ext)
                        }
                    }
                }
                dialog.isVisible = true
                val dir = dialog.directory
                val file = dialog.file
                if (dir != null && file != null) {
                    val f = File(dir, file)
                    val bytes = f.readBytes()
                    val mime = try {
                        Files.probeContentType(f.toPath()) ?: "application/octet-stream"
                    } catch (_: Exception) {
                        "application/octet-stream"
                    }
                    onPicked(PickedFile(name = f.name, mimeType = mime, bytes = bytes))
                }
            } catch (_: Exception) {}
        }
    }
}

private fun parseMime(filter: String): Pair<String?, String?> {
    val parts = filter.trim().split("/")
    val type = parts.getOrNull(0)?.takeIf { it.isNotBlank() }
    val sub = parts.getOrNull(1)?.takeIf { it.isNotBlank() }
    return type to sub
}

private val IMAGE_EXTS = setOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "heif")
private val VIDEO_EXTS = setOf("mp4", "mov", "mkv", "avi", "webm", "m4v")
private val AUDIO_EXTS = setOf("mp3", "wav", "ogg", "m4a", "flac", "aac", "opus")

private fun matchExtension(type: String, sub: String?, ext: String): Boolean {
    if (ext.isEmpty()) return false
    return when (type) {
        "image" -> IMAGE_EXTS.contains(ext)
        "video" -> VIDEO_EXTS.contains(ext)
        "audio" -> AUDIO_EXTS.contains(ext)
        "application" -> when (sub) {
            null, "*" -> true
            "pdf" -> ext == "pdf"
            else -> sub.equals(ext, ignoreCase = true)
        }
        else -> true
    }
}