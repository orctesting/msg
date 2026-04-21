package org.messenger.app.ui.chat

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.geometry.Rect

class FocusTargetRegistry {
    val bounds = mutableStateMapOf<String, Rect>()

    fun update(id: String, rect: Rect) {
        bounds[id] = rect
    }

    fun remove(id: String) {
        bounds.remove(id)
    }
}