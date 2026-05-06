package org.messenger.app.util

import androidx.compose.runtime.Composable

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // На iOS системного back-жеста нет, навигация через UI-кнопки.
}