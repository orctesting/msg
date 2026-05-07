package org.messenger.app.ui

import androidx.compose.runtime.Composable
import org.messenger.app.shared.di.AppModule

@Composable
expect fun PlatformMainScreen(
    appModule: AppModule,
    initialChatId: String?,
    initialChatName: String?,
    onLogout: () -> Unit,
    onOpenForwardPicker: (sourceChatId: String, sourceChatName: String, messageIds: List<String>) -> Unit,
    forwardResultChatId: String?,
    onForwardResultConsumed: () -> Unit,
)