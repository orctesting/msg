package org.messenger.app.ui

import androidx.compose.runtime.Composable
import org.messenger.app.shared.di.AppModule

@Composable
actual fun PlatformMainScreen(
    appModule: AppModule,
    initialChatId: String?,
    initialChatName: String?,
    onLogout: () -> Unit,
    onOpenForwardPicker: (sourceChatId: String, sourceChatName: String, messageIds: List<String>) -> Unit,
    forwardResultChatId: String?,
    onForwardResultConsumed: () -> Unit,
) {
    // На iOS используется MobileNavigation из App.kt — этот actual нужен только для компиляции.
}