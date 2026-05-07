package org.messenger.app.util

import androidx.compose.ui.Modifier

/**
 * Добавляет обработчик правого клика мыши (только desktop).
 * На Android/iOS — no-op (используется long-press).
 */
expect fun Modifier.onRightClick(onClick: () -> Unit): Modifier