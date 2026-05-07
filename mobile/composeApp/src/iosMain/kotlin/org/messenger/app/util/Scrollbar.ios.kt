package org.messenger.app.util

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun PlatformVerticalScrollbar(
    state: LazyListState,
    modifier: Modifier,
    reverseLayout: Boolean
) {
    // На iOS системный скроллбар встроен в LazyColumn — ничего не рендерим.
}