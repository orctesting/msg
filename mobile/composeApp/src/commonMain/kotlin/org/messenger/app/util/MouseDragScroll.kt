package org.messenger.app.util

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier

/**
 * Параметры обработки мыши для области сообщений (Desktop).
 *
 * @param onLeftDragSelect — вызывается при ЛКМ-drag. Передаёт коллбэки для определения
 *   сообщения по экранной точке (Y-координате) и начала/обновления выделения диапазона.
 *   На Android/iOS — игнорируется.
 *
 * Сценарии:
 * - СКМ зажата + движение → auto-scroll: скорость пропорциональна Δy от точки нажатия.
 * - ЛКМ зажата + движение → выделение сообщений (через onLeftDragSelect).
 * - ПКМ зажата + движение → touch-like drag (контент следует за курсором с инверсией скролла).
 *
 * Tap (без движения > порога) — НЕ перехватывается; обычные click/longClick/onRightClick работают.
 */
data class MouseSelectionCallbacks(
    val onSelectStart: (yPx: Float) -> Unit,
    val onSelectUpdate: (yPx: Float) -> Unit,
    val onSelectEnd: () -> Unit,
)

expect fun Modifier.mouseScrollGestures(
    listState: LazyListState,
    selectionCallbacks: MouseSelectionCallbacks? = null,
): Modifier