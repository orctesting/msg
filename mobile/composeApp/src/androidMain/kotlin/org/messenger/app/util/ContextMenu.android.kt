package org.messenger.app.util

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun PlatformContextMenu(
    itemsProvider: () -> List<ContextMenuItem>,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier) { content() }
}