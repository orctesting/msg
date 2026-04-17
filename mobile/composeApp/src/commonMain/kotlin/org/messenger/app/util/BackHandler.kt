package org.messenger.app.util

import androidx.compose.runtime.Composable

@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)
