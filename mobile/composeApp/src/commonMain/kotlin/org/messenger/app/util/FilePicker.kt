package org.messenger.app.util

import androidx.compose.runtime.Composable

data class PickedFile(
    val name: String,
    val mimeType: String,
    val bytes: ByteArray,
)

@Composable
expect fun rememberFilePicker(
    onPicked: (PickedFile) -> Unit,
): FilePickerLauncher

interface FilePickerLauncher {
    fun launch(mimeFilter: String = "*/*")
}