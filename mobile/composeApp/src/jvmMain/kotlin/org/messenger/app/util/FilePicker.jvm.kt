package org.messenger.app.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.nio.file.Files

@Composable
actual fun rememberFilePicker(
    onPicked: (PickedFile) -> Unit,
): FilePickerLauncher = remember {
    object : FilePickerLauncher {
        override fun launch(mimeFilter: String) {
            try {
                val dialog = FileDialog(null as Frame?, "Выберите файл", FileDialog.LOAD)
                dialog.isVisible = true
                val dir = dialog.directory
                val file = dialog.file
                if (dir != null && file != null) {
                    val f = File(dir, file)
                    val bytes = f.readBytes()
                    val mime = try {
                        Files.probeContentType(f.toPath()) ?: "application/octet-stream"
                    } catch (_: Exception) { "application/octet-stream" }
                    onPicked(PickedFile(name = f.name, mimeType = mime, bytes = bytes))
                }
            } catch (_: Exception) {}
        }
    }
}