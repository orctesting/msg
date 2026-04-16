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
import org.messenger.app.ui.settings.SettingsScreen
import org.messenger.app.ui.theme.AppTheme

sealed class Screen {
    data object Auth : Screen()
    data object ChatList : Screen()
    data object Settings : Screen()
    data class Chat(val chatId: String, val chatName: String) : Screen()
}

@Composable
fun App(
    appModule: AppModule,
    deepLinkChatId: String? = null,
    deepLinkChatName: String? = null
) {
    val isLoggedIn = appModule.tokenStorage.isLoggedIn()

    var appModuleRevision by remember { mutableStateOf(0) }
    var currentAppModule by remember { mutableStateOf(appModule) }

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
                    // Пересоздаём ViewModel при смене модуля, чтобы requestOtp шёл на правильный baseUrl
                    val viewModel = remember(appModuleRevision) {
                        AuthViewModel(
                            authRepository = currentAppModule.authRepository,
                            deviceInfo = DeviceInfo(
                                deviceId = "device-${getPlatformName()}",
                                platform = getPlatformName()
                            ),
                            initialServerAddress = currentAppModule.tokenStorage.getServerUrl() ?: ""
                        )
                    }
                    val state by viewModel.state.collectAsState()

                    LaunchedEffect(state.isAuthenticated) {
                        if (state.isAuthenticated) {
                            val addr = state.serverAddress.trim()
                            if (addr.isNotBlank()) {
                                currentAppModule.tokenStorage.saveServerUrl(addr)
                            }
                            currentScreen = Screen.ChatList
                            onLoginSuccessCallback(currentAppModule)
                        }
                    }

                    AuthScreen(
                        viewModel = viewModel,
                        onRequestOtp = { addr ->
                            val newBase = AppModule.buildBaseUrl(addr)
                            if (newBase != currentAppModule.baseUrl) {
                                // Пересоздаём модуль, сохраняем набранный телефон
                                val keptPhone = viewModel.state.value.phone
                                val newModule = AppModule(
                                    baseUrl = newBase,
                                    wsBaseUrl = AppModule.buildWsUrl(addr)
                                )
                                newModule.tokenStorage.saveServerUrl(addr)
                                currentAppModule = newModule
                                appModuleRevision++
                                syncAppModule(newModule)
                                // Запросить OTP будет уже новая ViewModel через LaunchedEffect ниже
                                pendingPhoneForOtp = keptPhone
                                pendingServerAddress = addr
                            } else {
                                // Адрес не менялся — сразу запрашиваем
                                viewModel.requestOtp()
                            }
                        }
                    )

                    // Если было пересоздание модуля — запросить OTP на новой VM
                    LaunchedEffect(appModuleRevision) {
                        val phone = pendingPhoneForOtp
                        val addr = pendingServerAddress
                        if (phone != null && addr != null && appModuleRevision > 0) {
                            viewModel.onServerAddressChanged(addr)
                            viewModel.onPhoneChanged(phone)
                            viewModel.requestOtp()
                            pendingPhoneForOtp = null
                            pendingServerAddress = null
                        }
                    }
                }

                is Screen.ChatList -> {
                    ChatListScreen(
                        appModule = currentAppModule,
                        onChatClick = { chatId, chatName ->
                            currentScreen = Screen.Chat(chatId, chatName)
                        },
                        onOpenSettings = { currentScreen = Screen.Settings }
                    )
                }

                is Screen.Settings -> {
                    SettingsScreen(
                        appModule = currentAppModule,
                        onBack = { currentScreen = Screen.ChatList },
                        onLogout = {
                            currentAppModule.tokenStorage.clear()
                            currentAppModule.wsService.disconnect()
                            currentScreen = Screen.Auth
                        }
                    )
                }

                is Screen.Chat -> {
                    updateCurrentChatId(screen.chatId)
                    ChatScreen(
                        chatId = screen.chatId,
                        chatName = screen.chatName,
                        appModule = currentAppModule,
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

// Временное хранилище данных между пересозданием AppModule/ViewModel
private var pendingPhoneForOtp: String? = null
private var pendingServerAddress: String? = null

expect fun updateCurrentChatId(chatId: String?)
expect fun onLoginSuccessCallback(appModule: AppModule)
expect fun syncAppModule(appModule: AppModule)