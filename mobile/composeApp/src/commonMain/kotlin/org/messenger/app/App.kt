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
    data object Profile : Screen()
    data object Avatars : Screen()
    data class PeerProfile(val userId: String) : Screen()
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

    var forwardResultChatId by remember { mutableStateOf<String?>(null) }

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
                    // ... оставить КАК БЫЛО (без изменений)
                }

                is Screen.ForwardPicker -> {
                    setActiveChatId(null)
                    ForwardTargetScreen(
                        appModule = currentAppModule,
                        sourceChatId = screen.sourceChatId,
                        messageIds = screen.messageIds,
                        onCancel = {
                            currentScreen = if (isDesktop) {
                                Screen.ChatList
                            } else {
                                Screen.Chat(screen.sourceChatId, screen.sourceChatName)
                            }
                        },
                        onPicked = { targetChatId ->
                            forwardScope.launch {
                                screen.messageIds.forEach { msgId ->
                                    try {
                                        currentAppModule.chatRepository.forwardMessage(
                                            sourceChatId = screen.sourceChatId,
                                            messageId = msgId,
                                            targetChatId = targetChatId,
                                        )
                                    } catch (_: Exception) {}
                                }
                            }
                            if (isDesktop) {
                                forwardResultChatId = targetChatId
                                currentScreen = Screen.ChatList
                            } else {
                                currentScreen = Screen.Chat(targetChatId, "")
                            }
                        }
                    )
                }

                else -> {
                    if (isDesktop) {
                        // Desktop: единый split-pane экран после авторизации
                        val initialChatScreen = screen as? Screen.Chat
                        org.messenger.app.ui.PlatformMainScreen(
                            appModule = currentAppModule,
                            initialChatId = initialChatScreen?.chatId,
                            initialChatName = initialChatScreen?.chatName,
                            onLogout = {
                                currentAppModule.tokenStorage.clear()
                                currentAppModule.wsService.disconnect()
                                currentScreen = Screen.Auth
                            },
                            onOpenForwardPicker = { srcId, srcName, ids ->
                                currentScreen = Screen.ForwardPicker(srcId, srcName, ids)
                            },
                            forwardResultChatId = forwardResultChatId,
                            onForwardResultConsumed = { forwardResultChatId = null },
                        )
                    } else {
                        // Mobile: оригинальная стековая навигация
                        MobileNavigation(
                            screen = screen,
                            currentAppModule = currentAppModule,
                            forwardScope = forwardScope,
                            onScreenChange = { currentScreen = it },
                        )
                    }
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
fun setActiveChatId(chatId: String?) {
    ActiveChatHolder.set(chatId)
    updateCurrentChatId(chatId)
}

expect fun observeDeepLinks(): kotlinx.coroutines.flow.Flow<Pair<String, String>>?

@Composable
private fun MobileNavigation(
    screen: Screen,
    currentAppModule: AppModule,
    forwardScope: CoroutineScope,
    onScreenChange: (Screen) -> Unit,
) {
    when (screen) {
        is Screen.ChatList -> {
            ChatListScreen(
                appModule = currentAppModule,
                onChatClick = { chatId, chatName ->
                    onScreenChange(Screen.Chat(chatId, chatName))
                },
                onOpenSettings = { onScreenChange(Screen.Settings) },
                onOpenProfile = { onScreenChange(Screen.Profile) },
            )
        }
        is Screen.Settings -> {
            PlatformBackHandler(enabled = true) { onScreenChange(Screen.ChatList) }
            SettingsScreen(
                appModule = currentAppModule,
                onBack = { onScreenChange(Screen.ChatList) },
                onOpenContacts = { onScreenChange(Screen.Contacts) },
                onOpenProfile = { onScreenChange(Screen.Profile) },
                onLogout = {
                    currentAppModule.tokenStorage.clear()
                    currentAppModule.wsService.disconnect()
                    onScreenChange(Screen.Auth)
                }
            )
        }
        is Screen.Contacts -> {
            PlatformBackHandler(enabled = true) { onScreenChange(Screen.Settings) }
            ContactsScreen(
                appModule = currentAppModule,
                onBack = { onScreenChange(Screen.Settings) },
                onContactClick = { contact ->
                    forwardScope.launch {
                        try {
                            val chat = currentAppModule.chatRepository
                                .createPersonalChat(contactId = contact.id)
                            onScreenChange(Screen.Chat(chat.id, chat.name ?: contact.displayName))
                        } catch (_: Exception) {}
                    }
                }
            )
        }
        is Screen.Profile -> {
            PlatformBackHandler(enabled = true) { onScreenChange(Screen.Settings) }
            org.messenger.app.ui.profile.ProfileScreen(
                appModule = currentAppModule,
                onBack = { onScreenChange(Screen.Settings) },
                onOpenAvatars = { onScreenChange(Screen.Avatars) },
            )
        }
        is Screen.Avatars -> {
            PlatformBackHandler(enabled = true) { onScreenChange(Screen.Profile) }
            org.messenger.app.ui.profile.AvatarsScreen(
                appModule = currentAppModule,
                onBack = { onScreenChange(Screen.Profile) },
            )
        }
        is Screen.PeerProfile -> {
            PlatformBackHandler(enabled = true) { onScreenChange(Screen.ChatList) }
            org.messenger.app.ui.profile.PeerProfileScreen(
                appModule = currentAppModule,
                userId = screen.userId,
                onBack = { onScreenChange(Screen.ChatList) },
            )
        }
        is Screen.Chat -> {
            setActiveChatId(screen.chatId)
            PlatformBackHandler(enabled = true) {
                setActiveChatId(null)
                onScreenChange(Screen.ChatList)
            }
            ChatScreen(
                chatId = screen.chatId,
                chatName = screen.chatName,
                appModule = currentAppModule,
                onBack = {
                    setActiveChatId(null)
                    onScreenChange(Screen.ChatList)
                },
                onPickForwardTarget = { sourceChatId, messageIds ->
                    onScreenChange(
                        Screen.ForwardPicker(
                            sourceChatId = sourceChatId,
                            sourceChatName = screen.chatName,
                            messageIds = messageIds,
                        )
                    )
                },
                onOpenPeerProfile = { userId ->
                    onScreenChange(Screen.PeerProfile(userId))
                },
            )
        }
        // Auth и ForwardPicker обрабатываются выше, до вызова MobileNavigation
        is Screen.Auth, is Screen.ForwardPicker -> Unit
    }
}