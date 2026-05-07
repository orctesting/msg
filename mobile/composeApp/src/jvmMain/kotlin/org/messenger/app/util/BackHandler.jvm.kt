package org.messenger.app.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

/**
 * На Desktop "back" привязан к клавише Escape.
 * Используется единый стек: последний зарегистрированный enabled-коллбэк
 * перехватывает нажатие Esc.
 */
internal object DesktopBackStack {
    // Используем CopyOnWriteArrayList для безопасной итерации в Swing-потоке
    val handlers = java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()

    fun handleEscape(): Boolean {
        val last = handlers.lastOrNull() ?: return false
        try { last() } catch (_: Exception) {}
        return true
    }
}

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    DisposableEffect(enabled, onBack) {
        if (!enabled) return@DisposableEffect onDispose { }
        DesktopBackStack.handlers.add(onBack)
        onDispose {
            DesktopBackStack.handlers.remove(onBack)
        }
    }
}