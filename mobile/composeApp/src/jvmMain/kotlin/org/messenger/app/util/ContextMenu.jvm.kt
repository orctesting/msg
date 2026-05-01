package org.messenger.app.util

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.window.PopupProperties

@Composable
actual fun PlatformContextMenu(
    itemsProvider: () -> List<ContextMenuItem>,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var anchorOffset by remember { mutableStateOf(Offset.Zero) }
    var items by remember { mutableStateOf<List<ContextMenuItem>>(emptyList()) }
    val density = LocalDensity.current

    Box(
        modifier = modifier.pointerInput(Unit) {
            awaitEachGesture {
                val event = awaitPointerEvent(PointerEventPass.Main)
                if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                    val change = event.changes.firstOrNull() ?: return@awaitEachGesture
                    anchorOffset = change.position
                    items = itemsProvider()
                    if (items.isNotEmpty()) {
                        expanded = true
                        event.changes.forEach { it.consume() }
                    }
                }
            }
        }
    ) {
        content()

        if (expanded) {
            val xDp = with(density) { anchorOffset.x.toDp() }
            val yDp = with(density) { anchorOffset.y.toDp() }
            DropdownMenu(
                expanded = true,
                onDismissRequest = { expanded = false },
                offset = DpOffset(xDp, yDp),
                properties = PopupProperties(focusable = true),
            ) {
                items.forEach { item ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                item.label,
                                color = if (item.isDestructive)
                                    MaterialTheme.colorScheme.error
                                else
                                    MaterialTheme.colorScheme.onSurface,
                            )
                        },
                        leadingIcon = item.icon?.let {
                            {
                                Icon(
                                    it,
                                    contentDescription = null,
                                    tint = if (item.isDestructive)
                                        MaterialTheme.colorScheme.error
                                    else
                                        MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        },
                        onClick = {
                            expanded = false
                            item.onClick()
                        },
                    )
                }
            }
        }
    }
}