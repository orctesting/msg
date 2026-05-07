package org.messenger.app.util

import androidx.compose.ui.Modifier

/**
 * No-op на desktop: ПКМ обрабатывается через PlatformContextMenu (DropdownMenu).
 * Старая логика открывала ModalBottomSheet, что дублировало контекстное меню.
 */
actual fun Modifier.onRightClick(onClick: () -> Unit): Modifier = this