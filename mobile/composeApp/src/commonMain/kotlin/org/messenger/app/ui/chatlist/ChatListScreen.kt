package org.messenger.app.ui.chatlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import org.messenger.app.shared.data.remote.WsService
import org.messenger.app.shared.data.model.ChatDto
import org.messenger.app.shared.di.AppModule
import org.messenger.app.shared.ui.chatlist.ChatListViewModel
import org.messenger.app.util.mouseScrollGestures
import org.messenger.app.util.PlatformVerticalScrollbar
import org.messenger.app.ui.chatlist.formatChatListTime

private enum class ChatsTab(val title: String) {
    ALL("Все"),
    PERSONAL("Личные"),
    GROUP("Групповые"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    appModule: AppModule,
    onChatClick: (chatId: String, chatName: String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenProfile: () -> Unit,
) {
    val viewModel = remember(appModule) { appModule.chatListViewModel }
    LaunchedEffect(Unit) {
        org.messenger.app.ChatListResyncBus.events.collect {
            // Lifecycle-resync: список обновится через WS-reconnect автоматически.
            // Принудительная перезагрузка не нужна.
        }
    }
    val state by viewModel.state.collectAsState()

    val userRole = remember { appModule.tokenStorage.getUserRole() }
    val isAdmin = userRole == "admin"

    var selectedTab by remember { mutableStateOf(ChatsTab.ALL) }
    var showPersonalDialog by remember { mutableStateOf(false) }
    var showGroupDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    androidx.compose.foundation.layout.Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Text("Чаты")
                        androidx.compose.foundation.layout.Spacer(
                            modifier = androidx.compose.ui.Modifier.width(8.dp)
                        )
                        ConnectionDot(wsService = appModule.wsService)
                    }
                },
                actions = {
                    IconButton(onClick = onOpenProfile) {
                        Icon(Icons.Default.AccountCircle, contentDescription = "Профиль")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Настройки")
                    }
                }
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                if (isAdmin) {
                    FloatingActionButton(
                        onClick = { showGroupDialog = true },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Icon(Icons.Default.GroupAdd, contentDescription = "Новый групповой чат")
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
                FloatingActionButton(onClick = { showPersonalDialog = true }) {
                    Icon(Icons.Default.PersonAdd, contentDescription = "Новый личный чат")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(selectedTabIndex = selectedTab.ordinal) {
                ChatsTab.values().forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = { Text(tab.title) }
                    )
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                val filtered = when (selectedTab) {
                    ChatsTab.ALL -> state.chats
                    ChatsTab.PERSONAL -> state.chats.filter { it.type == "personal" }
                    ChatsTab.GROUP -> state.chats.filter { it.type != "personal" }
                }

                when {
                    state.isLoading && state.chats.isEmpty() -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    state.error != null && state.chats.isEmpty() -> {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(state.error ?: "Ошибка")
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = { viewModel.loadChats() }) {
                                Text("Повторить")
                            }
                        }
                    }
                    filtered.isEmpty() -> {
                        Text(
                            text = when (selectedTab) {
                                ChatsTab.ALL -> "Нет чатов"
                                ChatsTab.PERSONAL -> "Нет личных чатов"
                                ChatsTab.GROUP -> "Нет групповых чатов"
                            },
                            modifier = Modifier.align(Alignment.Center),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    else -> {
                        val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                        Box(modifier = Modifier.fillMaxSize()) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .mouseScrollGestures(listState),
                            ) {
                                items(filtered, key = { it.id }) { chat ->
                                    ChatItem(
                                        chat = chat,
                                        showTypeIcon = selectedTab == ChatsTab.ALL,
                                        onClick = { onChatClick(chat.id, chat.name ?: "Чат") }
                                    )
                                }
                            }
                            PlatformVerticalScrollbar(
                                state = listState,
                                modifier = Modifier.align(Alignment.CenterEnd),
                                reverseLayout = false,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showPersonalDialog) {
        CreatePersonalChatDialog(
            appModule = appModule,
            onDismiss = { showPersonalDialog = false },
            onChatReady = { chat ->
                showPersonalDialog = false
                onChatClick(chat.id, chat.name ?: "Чат")
            }
        )
    }

    if (showGroupDialog && isAdmin) {
        CreateGroupChatDialog(
            appModule = appModule,
            onDismiss = { showGroupDialog = false },
            onCreated = { chat ->
                showGroupDialog = false
                viewModel.loadChats()
                onChatClick(chat.id, chat.name ?: "Чат")
            }
        )
    }
}

@Composable
private fun ChatItem(
    chat: ChatDto,
    showTypeIcon: Boolean,
    onClick: () -> Unit,
) {
    val isPersonal = chat.type == "personal"
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showTypeIcon) {
                    Icon(
                        imageVector = if (isPersonal) Icons.Default.Person else Icons.Default.Group,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = chat.name ?: "Чат",
                    fontWeight = if (chat.unreadCount > 0) FontWeight.Bold else FontWeight.Normal
                )
            }
        },
        supportingContent = {
            chat.lastMessage?.let { msg ->
                Text(
                    text = msg.content.ifBlank { "Вложение" },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        leadingContent = {
            Surface(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape),
                color = if (isPersonal)
                    MaterialTheme.colorScheme.tertiaryContainer
                else
                    MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = (chat.name ?: "?").take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        },
        trailingContent = {
            val ts = chat.lastMessage?.createdAt
            if (!ts.isNullOrBlank() || chat.unreadCount > 0) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (!ts.isNullOrBlank()) {
                        Text(
                            text = formatChatListTime(ts),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (chat.unreadCount > 0) {
                        Badge { Text("${chat.unreadCount}") }
                    }
                }
            }
        }
    )
    HorizontalDivider()
}

@Composable
private fun ConnectionDot(wsService: WsService) {
    val status by wsService.status.collectAsState()
    val color = when (status) {
        WsService.WsConnectionStatus.CONNECTED -> Color(0xFF2ECC71)
        WsService.WsConnectionStatus.CONNECTING -> Color(0xFFF1C40F)
        WsService.WsConnectionStatus.DISCONNECTED -> Color(0xFFE74C3C)
    }
    Box(
        modifier = Modifier
            .size(10.dp)
            .background(color = color, shape = CircleShape)
    )
}