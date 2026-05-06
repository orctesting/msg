package org.messenger.app.util

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

@OptIn(ExperimentalFoundationApi::class)
@Composable
actual fun PlatformContextMenu(
    itemsProvider: () -> List<ContextMenuItem>,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var items by remember { mutableStateOf<List<ContextMenuItem>>(emptyList()) }

    Box(
        modifier = modifier.combinedClickable(
            onClick = {},
            onLongClick = {
                items = itemsProvider()
                if (items.isNotEmpty()) expanded = true
            },
        )
    ) {
        content()
        if (expanded) {
            DropdownMenu(expanded = true, onDismissRequest = { expanded = false }) {
                items.forEach { item ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                item.label,
                                color = if (item.isDestructive) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurface,
                            )
                        },
                        leadingIcon = item.icon?.let {
                            { Icon(it, contentDescription = null) }
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