package org.messenger.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import com.russhwolf.settings.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

import org.messenger.app.shared.di.AppModule
import org.messenger.app.ui.chat.ChatScreen
import org.messenger.app.ui.chatlist.ChatListScreen
import org.messenger.app.ui.contacts.ContactsScreen
import org.messenger.app.ui.profile.AvatarsScreen
import org.messenger.app.ui.profile.PeerProfileScreen
import org.messenger.app.ui.profile.ProfileScreen
import org.messenger.app.ui.settings.SettingsScreen
import java.awt.Cursor

private const val SPLIT_RATIO_KEY = "desktop_split_ratio"
private const val DEFAULT_RATIO = 0.30f
private const val MIN_RATIO = 0.18f
private const val MAX_RATIO = 0.55f
private const val SPLITTER_WIDTH_DP = 4

private sealed class RightPane {
    data object Empty : RightPane()
    data class Chat(val chatId: String, val chatName: String) : RightPane()
    data object Settings : RightPane()
    data object Contacts : RightPane()
    data object Profile : RightPane()
    data object Avatars : RightPane()
    data class PeerProfile(val userId: String) : RightPane()
}

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
    val settings = remember { Settings() }
    var ratio by remember {
        mutableStateOf(
            (settings.getFloatOrNull(SPLIT_RATIO_KEY) ?: DEFAULT_RATIO)
                .coerceIn(MIN_RATIO, MAX_RATIO)
        )
    }

    var rightPane by remember {
        mutableStateOf<RightPane>(
            if (initialChatId != null) RightPane.Chat(initialChatId, initialChatName ?: "")
            else RightPane.Empty
        )
    }

    // Реакция на результат forward'а: открыть выбранный чат справа
    LaunchedEffect(forwardResultChatId) {
        val id = forwardResultChatId ?: return@LaunchedEffect
        rightPane = RightPane.Chat(id, "")
        onForwardResultConsumed()
    }

    val forwardScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main) }

    var totalWidth by remember { mutableStateOf(0) }
    val density = LocalDensity.current

    // Текущий выбранный чат — для подсветки в списке (если решим добавить позже)
    val currentChatId = (rightPane as? RightPane.Chat)?.chatId

    // Обновляем currentChatId для FCM-фильтра/lifecycle
    LaunchedEffect(currentChatId) {
        org.messenger.app.updateCurrentChatId(currentChatId)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { totalWidth = it.width },
    ) {
        if (totalWidth > 0) {
            val leftWidthPx = (totalWidth * ratio).toInt()
            val splitterPx = with(density) { SPLITTER_WIDTH_DP.dp.toPx() }.toInt()
            val rightWidthPx = (totalWidth - leftWidthPx - splitterPx).coerceAtLeast(0)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { totalWidth = it.width },
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    // Левая панель
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(ratio),
                    ) {
                        ChatListScreen(
                            appModule = appModule,
                            onChatClick = { id, name ->
                                rightPane = RightPane.Chat(id, name)
                            },
                            onOpenSettings = { rightPane = RightPane.Settings },
                            onOpenProfile = { rightPane = RightPane.Profile },
                        )
                    }

                    // Сплиттер
                    SplitterHandle(
                        onDrag = { deltaPx ->
                            val width = totalWidth
                            if (width <= 0) return@SplitterHandle
                            val newRatio = (ratio + deltaPx / width.toFloat())
                                .coerceIn(MIN_RATIO, MAX_RATIO)
                            ratio = newRatio
                        },
                        onDragEnd = {
                            try { settings.putFloat(SPLIT_RATIO_KEY, ratio) } catch (_: Exception) {}
                        },
                    )

                    // Правая панель
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(1f - ratio),
                    ) {
                        RightPaneContent(
                            pane = rightPane,
                            appModule = appModule,
                            onClose = { rightPane = RightPane.Empty },
                            onOpenSettings = { rightPane = RightPane.Settings },
                            onOpenContacts = { rightPane = RightPane.Contacts },
                            onOpenProfile = { rightPane = RightPane.Profile },
                            onOpenAvatars = { rightPane = RightPane.Avatars },
                            onOpenPeerProfile = { userId -> rightPane = RightPane.PeerProfile(userId) },
                            onOpenChat = { id, name -> rightPane = RightPane.Chat(id, name) },
                            onLogout = onLogout,
                            onPickForwardTarget = { sourceChatId, ids ->
                                val sourceName = (rightPane as? RightPane.Chat)?.chatName ?: ""
                                onOpenForwardPicker(sourceChatId, sourceName, ids)
                            },
                            onContactClick = { contact ->
                                forwardScope.launch {
                                    try {
                                        val chat = appModule.chatRepository
                                            .createPersonalChat(contactId = contact.id)
                                        rightPane = RightPane.Chat(
                                            chat.id,
                                            chat.name ?: contact.displayName,
                                        )
                                    } catch (_: Exception) {}
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SplitterHandle(
    onDrag: (deltaPx: Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    val dragState = rememberDraggableState { delta -> onDrag(delta) }
    Box(
        modifier = Modifier
            .width(SPLITTER_WIDTH_DP.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.outlineVariant)
            .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR)))
            .draggable(
                state = dragState,
                orientation = Orientation.Horizontal,
                onDragStopped = { onDragEnd() },
            )
    )
}

@Composable
private fun RightPaneContent(
    pane: RightPane,
    appModule: AppModule,
    onClose: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenContacts: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenAvatars: () -> Unit,
    onOpenPeerProfile: (String) -> Unit,
    onOpenChat: (String, String) -> Unit,
    onLogout: () -> Unit,
    onPickForwardTarget: (sourceChatId: String, messageIds: List<String>) -> Unit,
    onContactClick: (org.messenger.app.shared.data.model.ContactDto) -> Unit,
) {
    when (pane) {
        is RightPane.Empty -> EmptyPanePlaceholder()

        is RightPane.Chat -> {
            // key по chatId — чтобы пересоздавать ViewModel при смене чата
            key(pane.chatId) {
                ChatScreen(
                    chatId = pane.chatId,
                    chatName = pane.chatName,
                    appModule = appModule,
                    onBack = onClose,
                    onPickForwardTarget = { sourceChatId, ids ->
                        onPickForwardTarget(sourceChatId, ids)
                    },
                    onOpenPeerProfile = { userId -> onOpenPeerProfile(userId) },
                )
            }
        }

        is RightPane.Settings -> SettingsScreen(
            appModule = appModule,
            onBack = onClose,
            onOpenContacts = onOpenContacts,
            onOpenProfile = onOpenProfile,
            onLogout = onLogout,
        )

        is RightPane.Contacts -> ContactsScreen(
            appModule = appModule,
            onBack = onOpenSettings,
            onContactClick = onContactClick,
        )

        is RightPane.Profile -> ProfileScreen(
            appModule = appModule,
            onBack = onOpenSettings,
            onOpenAvatars = onOpenAvatars,
        )

        is RightPane.Avatars -> AvatarsScreen(
            appModule = appModule,
            onBack = onOpenProfile,
        )

        is RightPane.PeerProfile -> PeerProfileScreen(
            appModule = appModule,
            userId = pane.userId,
            onBack = onClose,
        )
    }
}

@Composable
private fun EmptyPanePlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Выберите чат",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}