package org.messenger.app.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun Modifier.fileDropTarget(
    enabled: Boolean,
    onDragStateChange: (Boolean) -> Unit,
    onFilesDropped: (List<DroppedFile>) -> Unit,
): Modifier = this // на iOS drag&drop файлов не используется