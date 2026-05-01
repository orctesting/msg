package org.messenger.app.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.layout.onGloballyPositioned
import java.awt.Component
import java.awt.Window
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetAdapter
import java.awt.dnd.DropTargetDragEvent
import java.awt.dnd.DropTargetDropEvent
import java.io.File
import java.nio.file.Files

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
        val window = findActiveWindow()
        var oldTarget: DropTarget? = null
        if (window != null) {
            oldTarget = window.dropTarget
            val dt = object : DropTargetAdapter() {
                override fun dragEnter(dtde: DropTargetDragEvent) {
                    if (!enabledState.value) return
                    if (dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                        dtde.acceptDrag(DnDConstants.ACTION_COPY)
                        dragCb.value(true)
                    } else {
                        dtde.rejectDrag()
                    }
                }

                override fun dragOver(dtde: DropTargetDragEvent) {
                    if (!enabledState.value) {
                        dtde.rejectDrag(); return
                    }
                    if (dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                        dtde.acceptDrag(DnDConstants.ACTION_COPY)
                    }
                }

                override fun dragExit(dte: java.awt.dnd.DropTargetEvent) {
                    dragCb.value(false)
                }

                override fun drop(dtde: DropTargetDropEvent) {
                    dragCb.value(false)
                    if (!enabledState.value) {
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
                        if (items.isNotEmpty()) dropCb.value(items)
                        dtde.dropComplete(true)
                    } catch (_: Exception) {
                        try { dtde.dropComplete(false) } catch (_: Exception) {}
                    }
                }
            }
            window.dropTarget = DropTarget(window, DnDConstants.ACTION_COPY, dt, true)
        }
        onDispose {
            if (window != null) {
                window.dropTarget = oldTarget
            }
        }
    }
    this
}

private fun findActiveWindow(): Window? {
    return try {
        Window.getWindows().firstOrNull { it.isShowing && it.isFocused }
            ?: Window.getWindows().firstOrNull { it.isShowing }
    } catch (_: Exception) { null }
}