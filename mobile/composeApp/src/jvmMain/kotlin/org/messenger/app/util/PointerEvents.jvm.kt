package org.messenger.app.util

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput

actual fun Modifier.onRightClick(onClick: () -> Unit): Modifier = this.pointerInput(onClick) {
    awaitEachGesture {
        val event = awaitPointerEvent(PointerEventPass.Main)
        if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
            event.changes.forEach { it.consume() }
            onClick()
        }
    }
}