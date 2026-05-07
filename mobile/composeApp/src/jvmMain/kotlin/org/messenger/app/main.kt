package org.messenger.app

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.russhwolf.settings.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.messenger.app.shared.di.AppModule
import org.messenger.app.util.DesktopBackStack
import java.awt.Dimension
import androidx.compose.ui.input.key.isCtrlPressed

private const val WINDOW_W_KEY = "window_width"
private const val WINDOW_H_KEY = "window_height"
private const val MIN_W = 800
private const val MIN_H = 600

fun main() = application {
    val appModule = AppModule()
    val settings = remember { Settings() }

    val savedW = (settings.getIntOrNull(WINDOW_W_KEY) ?: 1280).coerceAtLeast(MIN_W)
    val savedH = (settings.getIntOrNull(WINDOW_H_KEY) ?: 800).coerceAtLeast(MIN_H)

    val windowState = rememberWindowState(
        width = savedW.dp,
        height = savedH.dp,
    )

    val scope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main) }
    val lifecycle = remember {
        DesktopLifecycle(appModule).also {
            DesktopLifecycleHolder.instance = it
            DesktopLifecycleHolder.scope = scope
        }
    }

    LaunchedEffect(Unit) {
        lifecycle.onAppStart(scope)
    }

    Window(
        onCloseRequest = {
            try {
                settings.putInt(WINDOW_W_KEY, windowState.size.width.value.toInt())
                settings.putInt(WINDOW_H_KEY, windowState.size.height.value.toInt())
            } catch (_: Exception) {}
            lifecycle.onAppStop()
            exitApplication()
        },
        title = "Messenger Oreshnik",
        state = windowState,
        onPreviewKeyEvent = { keyEvent ->
            if (keyEvent.type == KeyEventType.KeyDown) {
                when {
                    keyEvent.key == Key.Escape -> {
                        DesktopBackStack.handleEscape()
                    }
                    keyEvent.key == Key.W && keyEvent.isCtrlPressed -> {
                        // Ctrl+W = back (последний хендлер в стеке)
                        DesktopBackStack.handleEscape()
                    }
                    else -> false
                }
            } else false
        },
    ) {
        // Минимальный размер окна
        LaunchedEffect(Unit) {
            window.minimumSize = Dimension(MIN_W, MIN_H)
        }

        LaunchedEffect(windowState.isMinimized) {
            if (windowState.isMinimized) {
                lifecycle.onWindowMinimized()
            } else {
                lifecycle.onWindowRestored(scope)
            }
        }

        App(appModule = appModule)
    }
}