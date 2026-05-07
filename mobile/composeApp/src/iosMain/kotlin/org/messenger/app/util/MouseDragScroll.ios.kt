package org.messenger.app.util

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier

actual fun Modifier.mouseScrollGestures(
    listState: LazyListState,
    selectionCallbacks: MouseSelectionCallbacks?,
): Modifier = this // мобильный — обычный touch-скролл