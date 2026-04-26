package org.messenger.app.navigation

import androidx.compose.runtime.*
import org.messenger.app.shared.di.AppModule
import org.messenger.app.ui.chatlist.ChatListScreen
import org.messenger.app.ui.chat.ChatScreen

enum class NavScreen {
    CHAT_LIST,
    CHAT
}

@Composable
fun AppNavHost(
    appModule: AppModule,
    onOpenSettings: () -> Unit
) {
    var currentScreen by remember { mutableStateOf(NavScreen.CHAT_LIST) }
    var selectedChatId by remember { mutableStateOf("") }
    var selectedChatName by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        appModule.wsService.connect(this)
    }

    when (currentScreen) {
        NavScreen.CHAT_LIST -> {
            ChatListScreen(
                appModule = appModule,
                onChatClick = { chatId, chatName ->
                    selectedChatId = chatId
                    selectedChatName = chatName
                    currentScreen = NavScreen.CHAT
                },
                onOpenSettings = onOpenSettings,
                onOpenProfile = { /* TODO navigation to profile */ },
            )
        }
        NavScreen.CHAT -> {
            ChatScreen(
                chatId = selectedChatId,
                chatName = selectedChatName,
                appModule = appModule,
                onBack = { currentScreen = NavScreen.CHAT_LIST }
            )
        }
    }
}