package org.messenger.app.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import java.awt.Component
import java.awt.Container
import java.awt.Window
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetAdapter
import java.awt.dnd.DropTargetDragEvent
import java.awt.dnd.DropTargetDropEvent
import java.io.File
import java.nio.file.Files

/**
 * Глобальный holder: все активные callback'и для drop.
 * Drop-event в любом окне распространяется на все зарегистрированные callback'и.
 * Это позволяет нескольким компонентам (например, MessageInput в открытом чате)
 * принимать drop, не конфликтуя между собой.
 */
private object DropRegistry {
    data class Entry(
        val enabled: () -> Boolean,
        val onDragState: (Boolean) -> Unit,
        val onFiles: (List<DroppedFile>) -> Unit,
    )

    private val entries = java.util.concurrent.CopyOnWriteArrayList<Entry>()
    private var installed = false

    @Synchronized
    fun register(entry: Entry) {
        entries.add(entry)
        ensureInstalled()
    }

    @Synchronized
    fun unregister(entry: Entry) {
        entries.remove(entry)
    }

    fun hasEnabled(): Boolean = entries.any { it.enabled() }

    fun notifyDragState(active: Boolean) {
        entries.forEach {
            if (it.enabled()) {
                try { it.onDragState(active) } catch (_: Exception) {}
            }
        }
    }

    fun notifyFiles(files: List<DroppedFile>) {
        // Отдаём только первому активному (обычно это открытый чат)
        val first = entries.firstOrNull { it.enabled() } ?: return
        try { first.onFiles(files) } catch (_: Exception) {}
    }

    private fun ensureInstalled() {
        if (installed) return
        installed = true
        // Ждём появления окна и навешиваем DropTarget рекурсивно
        Thread {
            var attempts = 0
            while (attempts < 50) {
                val windows = try { Window.getWindows() } catch (_: Exception) { emptyArray() }
                val mainWindow = windows.firstOrNull { w ->
                    try { w.isShowing && !isAuxiliaryWindow(w) } catch (_: Exception) { false }
                }
                if (mainWindow != null) {
                    try {
                        installDropTargetRecursive(mainWindow)
                    } catch (_: Exception) {}
                    return@Thread
                }
                attempts++
                try { Thread.sleep(100) } catch (_: Exception) {}
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    private fun isAuxiliaryWindow(w: Window): Boolean {
        return try {
            w.isAlwaysOnTop
        } catch (_: Exception) { false }
    }

    private fun installDropTargetRecursive(component: Component) {
        try {
            val listener = object : DropTargetAdapter() {
                override fun dragEnter(dtde: DropTargetDragEvent) {
                    if (!hasEnabled()) {
                        dtde.rejectDrag(); return
                    }
                    if (dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                        dtde.acceptDrag(DnDConstants.ACTION_COPY)
                        notifyDragState(true)
                    } else {
                        dtde.rejectDrag()
                    }
                }

                override fun dragOver(dtde: DropTargetDragEvent) {
                    if (!hasEnabled()) {
                        dtde.rejectDrag(); return
                    }
                    if (dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                        dtde.acceptDrag(DnDConstants.ACTION_COPY)
                    }
                }

                override fun dragExit(dte: java.awt.dnd.DropTargetEvent) {
                    notifyDragState(false)
                }

                override fun drop(dtde: DropTargetDropEvent) {
                    notifyDragState(false)
                    if (!hasEnabled()) {
                        dtde.rejectDrop(); return
                    }
                    try {
                        if (!dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                            dtde.rejectDrop(); return
                        }
                        dtde.acceptDrop(DnDConstants.ACTION_COPY)
                        @Suppress("UNCHECKED_CAST")
                        val list = dtde.transferable
                            .getTransferData(DataFlavor.javaFileListFlavor) as List<File>
                        val items = list.mapNotNull { f ->
                            try {
                                if (!f.exists() || !f.isFile) return@mapNotNull null
                                val mime = try {
                                    Files.probeContentType(f.toPath()) ?: "application/octet-stream"
                                } catch (_: Exception) { "application/octet-stream" }
                                DroppedFile(f.name, mime, f.readBytes())
                            } catch (_: Exception) { null }
                        }
                        if (items.isNotEmpty()) notifyFiles(items)
                        dtde.dropComplete(true)
                    } catch (_: Exception) {
                        try { dtde.dropComplete(false) } catch (_: Exception) {}
                    }
                }
            }

            // Вешаем DropTarget на текущий компонент
            DropTarget(component, DnDConstants.ACTION_COPY, listener, true)

            // Рекурсивно на все дочерние
            if (component is Container) {
                for (child in component.components) {
                    installDropTargetRecursive(child)
                }
            }

            // Слушатель добавления новых компонентов — на случай если Compose
            // пересоздаёт дерево
            if (component is Container) {
                component.addContainerListener(object : java.awt.event.ContainerAdapter() {
                    override fun componentAdded(e: java.awt.event.ContainerEvent) {
                        try { installDropTargetRecursive(e.child) } catch (_: Exception) {}
                    }
                })
            }
        } catch (_: Exception) {}
    }
}

@Composable
actual fun Modifier.fileDropTarget(
    enabled: Boolean,
    onDragStateChange: (Boolean) -> Unit,
    onFilesDropped: (List<DroppedFile>) -> Unit,
): Modifier = composed {
    val dragCb = rememberUpdatedState(onDragStateChange)
    val dropCb = rememberUpdatedState(onFilesDropped)
    val enabledState = rememberUpdatedState(enabled)

    DisposableEffect(Unit) {
        val entry = DropRegistry.Entry(
            enabled = { enabledState.value },
            onDragState = { dragCb.value(it) },
            onFiles = { dropCb.value(it) },
        )
        DropRegistry.register(entry)
        onDispose {
            DropRegistry.unregister(entry)
        }
    }
    this
}