package org.messenger.app.util

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.awt.Cursor
import java.awt.MouseInfo
import javax.swing.SwingUtilities
import kotlin.math.abs
import kotlin.math.sign

private const val DRAG_THRESHOLD_PX = 6f
private const val AUTO_SCROLL_DEAD_ZONE_PX = 8f
private const val AUTO_SCROLL_MAX_SPEED_PX_PER_FRAME = 60f
private const val AUTO_SCROLL_FRAME_MS = 16L
private const val AUTO_SCROLL_DIVIDER = 3.5f

private enum class DragMode { NONE, MIDDLE_AUTOSCROLL, LEFT_SELECT, RIGHT_TOUCH_DRAG }

actual fun Modifier.mouseScrollGestures(
    listState: LazyListState,
    selectionCallbacks: MouseSelectionCallbacks?,
): Modifier = composed {
    val scope = rememberCoroutineScope()

    pointerInput(listState) {
        awaitEachGesture {
            val down = awaitPointerEvent(PointerEventPass.Initial)
            val firstChange = down.changes.firstOrNull() ?: return@awaitEachGesture
            if (firstChange.type != PointerType.Mouse) return@awaitEachGesture
            if (down.type != PointerEventType.Press) return@awaitEachGesture

            val buttons = down.buttons
            val mode: DragMode = when {
                buttons.isTertiaryPressed -> DragMode.MIDDLE_AUTOSCROLL
                buttons.isSecondaryPressed -> DragMode.RIGHT_TOUCH_DRAG
                buttons.isPrimaryPressed && selectionCallbacks != null -> DragMode.LEFT_SELECT
                else -> DragMode.NONE
            }
            if (mode == DragMode.NONE) return@awaitEachGesture

            val anchor: Offset = firstChange.position
            var lastPos: Offset = anchor
            var dragStarted = false
            var autoScrollJob: Job? = null
            var middleDeltaY = 0f
            var cursorChanged = false

            // Установить курсор для СКМ-режима
            if (mode == DragMode.MIDDLE_AUTOSCROLL) {
                setGlobalCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR))
                cursorChanged = true
            }

            try {
                while (true) {
                    val ev = awaitPointerEvent(PointerEventPass.Main)
                    val change = ev.changes.firstOrNull() ?: break

                    val released = ev.type == PointerEventType.Release ||
                            change.changedToUpIgnoreConsumed() ||
                            !ev.buttons.let {
                                when (mode) {
                                    DragMode.MIDDLE_AUTOSCROLL -> it.isTertiaryPressed
                                    DragMode.RIGHT_TOUCH_DRAG -> it.isSecondaryPressed
                                    DragMode.LEFT_SELECT -> it.isPrimaryPressed
                                    DragMode.NONE -> false
                                }
                            }

                    val pos = change.position
                    val totalDelta = pos - anchor

                    when (mode) {
                        DragMode.MIDDLE_AUTOSCROLL -> {
                            if (!dragStarted) {
                                dragStarted = true
                                autoScrollJob = startAutoScroll(scope, listState) { middleDeltaY }
                            }
                            middleDeltaY = totalDelta.y
                            change.consume()
                        }

                        DragMode.RIGHT_TOUCH_DRAG -> {
                            if (!dragStarted && abs(totalDelta.y) >= DRAG_THRESHOLD_PX) {
                                dragStarted = true
                            }
                            if (dragStarted) {
                                val frameDelta = pos.y - lastPos.y
                                scope.launch {
                                    listState.scrollBy(-frameDelta)
                                }
                                change.consume()
                            }
                        }

                        DragMode.LEFT_SELECT -> {
                            val cb = selectionCallbacks!!
                            if (!dragStarted && abs(totalDelta.y) >= DRAG_THRESHOLD_PX) {
                                dragStarted = true
                                cb.onSelectStart(anchor.y)
                            }
                            if (dragStarted) {
                                cb.onSelectUpdate(pos.y)
                                change.consume()
                            }
                        }

                        DragMode.NONE -> {}
                    }

                    lastPos = pos

                    if (released) break
                }
            } finally {
                autoScrollJob?.cancel()
                if (cursorChanged) {
                    setGlobalCursor(Cursor.getDefaultCursor())
                }
                if (mode == DragMode.LEFT_SELECT && dragStarted) {
                    selectionCallbacks?.onSelectEnd?.invoke()
                }
            }
        }
    }
}

private fun setGlobalCursor(cursor: Cursor) {
    try {
        val pi = MouseInfo.getPointerInfo() ?: return
        // Применяем курсор к окну под мышью
        SwingUtilities.invokeLater {
            try {
                val windows = java.awt.Window.getWindows()
                for (w in windows) {
                    if (w.isShowing) {
                        w.cursor = cursor
                    }
                }
            } catch (_: Exception) {}
        }
    } catch (_: Exception) {}
}

private suspend fun LazyListState.scrollBy(deltaPx: Float) {
    if (deltaPx == 0f) return
    scroll {
        scrollBy(deltaPx)
    }
}

private fun startAutoScroll(
    scope: CoroutineScope,
    listState: LazyListState,
    deltaProvider: () -> Float,
): Job = scope.launch {
    while (isActive) {
        val dy = deltaProvider()
        val absDy = abs(dy)
        if (absDy > AUTO_SCROLL_DEAD_ZONE_PX) {
            val raw = (absDy - AUTO_SCROLL_DEAD_ZONE_PX) / AUTO_SCROLL_DIVIDER
            val speed = raw.coerceAtMost(AUTO_SCROLL_MAX_SPEED_PX_PER_FRAME)
            // Инверсия для reverseLayout: курсор вверх (dy<0) → к старым сообщениям (scroll вверх → положительный delta)
            val signed = -speed * sign(dy)
            try {
                listState.scrollBy(signed)
            } catch (_: Exception) {}
        }
        delay(AUTO_SCROLL_FRAME_MS)
    }
}