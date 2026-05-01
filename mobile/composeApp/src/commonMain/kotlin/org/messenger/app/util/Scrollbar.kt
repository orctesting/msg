package org.messenger.app.util

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Накладывает вертикальный скроллбар поверх контента (на Desktop).
 * На Android/iOS — no-op (используются нативные индикаторы LazyColumn).
 */
@Composable
expect fun PlatformVerticalScrollbar(
    state: LazyListState,
    modifier: Modifier = Modifier,
    reverseLayout: Boolean = false,
)