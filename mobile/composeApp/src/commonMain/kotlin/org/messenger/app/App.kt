package org.messenger.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.messenger.app.shared.di.AppModule
import org.messenger.app.shared.data.model.DeviceInfo
import org.messenger.app.shared.ui.auth.AuthStep
import org.messenger.app.shared.ui.auth.AuthViewModel
import org.messenger.app.util.PlatformBackHandler
import org.messenger.app.ui.auth.AuthScreen
import org.messenger.app.ui.chat.ChatScreen
import org.messenger.app.ui.chatlist.ChatListScreen
import org.messenger.app.ui.forward.ForwardTargetScreen
import org.messenger.app.ui.settings.SettingsScreen
import org.messenger.app.ui.theme.AppTheme
import org.messenger.app.ui.contacts.ContactsScreen

sealed class Screen {
    data object Auth : Screen()
    data object ChatList : Screen()
    data object Settings : Screen()
    data object Contacts : Screen()
    data class Chat(val chatId: String, val chatName: String) : Screen()
    data class ForwardPicker(
        val sourceChatId: String,
        val sourceChatName: String,
        val messageIds: List<String>,
    ) : Screen()
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

    // Скоуп для пересылок (forward) — живёт на уровне App, не привязан к ChatScreen
    val forwardScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main) }
    // Deep links (FCM tap на Android). На других платформах возвращает null.
    LaunchedEffect(Unit) {
        val flow = observeDeepLinks() ?: return@LaunchedEffect
        flow.collect { (chatId, chatName) ->
            if (currentAppModule.tokenStorage.isLoggedIn()) {
                currentScreen = Screen.Chat(chatId, chatName)
            }
        }
    }

    AppTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            when (val screen = currentScreen) {
                is Screen.Auth -> {
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

                    // Системная кнопка "назад": на шаге CODE возвращает к PHONE,
                    // на шаге PHONE — не перехватывает (даёт системе свернуть приложение)
                    PlatformBackHandler(
                        enabled = state.step == AuthStep.CODE
                    ) {
                        viewModel.backToPhone()
                    }

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
                                val keptPhone = viewModel.state.value.phone
                                val newModule = AppModule(
                                    baseUrl = newBase,
                                    wsBaseUrl = AppModule.buildWsUrl(addr)
                                )
                                newModule.tokenStorage.saveServerUrl(addr)
                                currentAppModule = newModule
                                appModuleRevision++
                                syncAppModule(newModule)
                                pendingPhoneForOtp = keptPhone
                                pendingServerAddress = addr
                            } else {
                                viewModel.requestOtp()
                            }
                        }
                    )

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
                    // На списке чатов системная "назад" не перехватывается —
                    // пусть Android сворачивает приложение как обычно
                    ChatListScreen(
                        appModule = currentAppModule,
                        onChatClick = { chatId, chatName ->
                            currentScreen = Screen.Chat(chatId, chatName)
                        },
                        onOpenSettings = { currentScreen = Screen.Settings }
                    )
                }

                is Screen.Settings -> {
                    // "Назад" из настроек → список чатов
                    PlatformBackHandler(enabled = true) {
                        currentScreen = Screen.ChatList
                    }

                    SettingsScreen(
                        appModule = currentAppModule,
                        onBack = { currentScreen = Screen.ChatList },
                        onOpenContacts = { currentScreen = Screen.Contacts },
                        onLogout = {
                            currentAppModule.tokenStorage.clear()
                            currentAppModule.wsService.disconnect()
                            currentScreen = Screen.Auth
                        }
                    )
                }

                is Screen.Contacts -> {
                    PlatformBackHandler(enabled = true) {
                        currentScreen = Screen.Settings
                    }
                    ContactsScreen(
                        appModule = currentAppModule,
                        onBack = { currentScreen = Screen.Settings },
                        onContactClick = { contact ->
                            // Создать / открыть личный чат с контактом
                            forwardScope.launch {
                                try {
                                    val chat = currentAppModule.chatRepository
                                        .createPersonalChat(contactId = contact.id)
                                    currentScreen = Screen.Chat(chat.id, chat.name ?: contact.displayName)
                                } catch (_: Exception) {
                                    // silently ignore; пользователь увидит через state ошибки если расширим
                                }
                            }
                        }
                    )
                }

                is Screen.Chat -> {
                    updateCurrentChatId(screen.chatId)
                    // BackHandler внутри самого ChatScreen уже обрабатывает:
                    // - закрытие actions sheet
                    // - выход из selection mode
                    // - отмену edit / reply
                    // - и в последнюю очередь — вызов onBack(), который здесь ведёт в ChatList
                    ChatScreen(
                        chatId = screen.chatId,
                        chatName = screen.chatName,
                        appModule = currentAppModule,
                        onBack = {
                            updateCurrentChatId(null)
                            currentScreen = Screen.ChatList
                        },
                        onPickForwardTarget = { sourceChatId, messageIds ->
                            currentScreen = Screen.ForwardPicker(
                                sourceChatId = sourceChatId,
                                sourceChatName = screen.chatName,
                                messageIds = messageIds,
                            )
                        }
                    )
                }

                is Screen.ForwardPicker -> {
                    updateCurrentChatId(null)
                    // BackHandler внутри ForwardTargetScreen сам обрабатывает диалог подтверждения
                    // и отмену выбора, возвращаясь в исходный чат
                    ForwardTargetScreen(
                        appModule = currentAppModule,
                        sourceChatId = screen.sourceChatId,
                        messageIds = screen.messageIds,
                        onCancel = {
                            currentScreen = Screen.Chat(screen.sourceChatId, screen.sourceChatName)
                        },
                        onPicked = { targetChatId ->
                            // Форвардим все выбранные сообщения по одному
                            forwardScope.launch {
                                screen.messageIds.forEach { msgId ->
                                    try {
                                        currentAppModule.chatRepository.forwardMessage(
                                            sourceChatId = screen.sourceChatId,
                                            messageId = msgId,
                                            targetChatId = targetChatId,
                                        )
                                    } catch (_: Exception) {
                                        // ошибки форварда глотаем, пользователь увидит отсутствие сообщения
                                    }
                                }
                            }
                            // Открываем целевой чат (имя подтянется из loadChatInfo внутри ChatScreen)
                            currentScreen = Screen.Chat(targetChatId, "")
                        }
                    )
                }
            }
        }
    }
}

private var pendingPhoneForOtp: String? = null
private var pendingServerAddress: String? = null

expect fun updateCurrentChatId(chatId: String?)
expect fun onLoginSuccessCallback(appModule: AppModule)
expect fun syncAppModule(appModule: AppModule)

expect fun observeDeepLinks(): kotlinx.coroutines.flow.Flow<Pair<String, String>>?