package org.messenger.app.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

data class DroppedFile(
    val name: String,
    val mimeType: String,
    val bytes: ByteArray,
)

@Composable
expect fun Modifier.fileDropTarget(
    enabled: Boolean = true,
    onDragStateChange: (Boolean) -> Unit = {},
    onFilesDropped: (List<DroppedFile>) -> Unit,
): Modifier