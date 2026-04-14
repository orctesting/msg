package org.messenger.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import org.messenger.app.shared.di.AppModule
import org.messenger.app.shared.data.model.DeviceInfo
import org.messenger.app.shared.ui.auth.AuthViewModel
import org.messenger.app.ui.auth.AuthScreen
import org.messenger.app.ui.chat.ChatScreen
import org.messenger.app.ui.chatlist.ChatListScreen
import org.messenger.app.ui.theme.AppTheme

sealed class Screen {
    data object Auth : Screen()
    data object ChatList : Screen()
    data class Chat(val chatId: String, val chatName: String) : Screen()
}

@Composable
fun App(
    appModule: AppModule,
    deepLinkChatId: String? = null,
    deepLinkChatName: String? = null
) {
    val isLoggedIn = appModule.tokenStorage.isLoggedIn()

    var currentScreen by remember {
        mutableStateOf<Screen>(
            when {
                !isLoggedIn -> Screen.Auth
                deepLinkChatId != null -> Screen.Chat(deepLinkChatId, deepLinkChatName ?: "")
                else -> Screen.ChatList
            }
        )
    }

    AppTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            when (val screen = currentScreen) {
                is Screen.Auth -> {
                    val viewModel = remember {
                        AuthViewModel(
                            authRepository = appModule.authRepository,
                            deviceInfo = DeviceInfo(
                                deviceId = "device-${getPlatformName()}",
                                platform = getPlatformName()
                            )
                        )
                    }
                    val state by viewModel.state.collectAsState()

                    LaunchedEffect(state.isAuthenticated) {
                        if (state.isAuthenticated) {
                            currentScreen = Screen.ChatList
                            onLoginSuccessCallback(appModule)
                        }
                    }

                    AuthScreen(viewModel = viewModel)
                }

                is Screen.ChatList -> {
                    ChatListScreen(
                        appModule = appModule,
                        onChatClick = { chatId, chatName ->
                            currentScreen = Screen.Chat(chatId, chatName)
                        },
                        onLogout = {
                            appModule.tokenStorage.clear()
                            appModule.wsService.disconnect()
                            currentScreen = Screen.Auth
                        }
                    )
                }

                is Screen.Chat -> {
                    updateCurrentChatId(screen.chatId)
                    ChatScreen(
                        chatId = screen.chatId,
                        chatName = screen.chatName,
                        appModule = appModule,
                        onBack = {
                            updateCurrentChatId(null)
                            currentScreen = Screen.ChatList
                        }
                    )
                }
            }
        }
    }
}

expect fun updateCurrentChatId(chatId: String?)
expect fun onLoginSuccessCallback(appModule: AppModule)