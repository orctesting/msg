package org.messenger.app.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

data class ContextMenuItem(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    val isDestructive: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * Оборачивает контент платформенным контекстным меню.
 * - Desktop: ПКМ открывает DropdownMenu в точке курсора.
 * - Android/iOS: no-op (используется long-press + ModalBottomSheet выше по дереву).
 */
@Composable
expect fun PlatformContextMenu(
    itemsProvider: () -> List<ContextMenuItem>,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
)