package org.messenger.app.util

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun PlatformVerticalScrollbar(
    state: LazyListState,
    modifier: Modifier,
    reverseLayout: Boolean,
) {
    VerticalScrollbar(
        adapter = rememberScrollbarAdapter(state),
        modifier = modifier.fillMaxHeight(),
        reverseLayout = reverseLayout,
    )
}