package org.messenger.app.ui.chatlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.messenger.app.shared.data.model.ChatDto
import org.messenger.app.shared.di.AppModule
import org.messenger.app.shared.ui.chatlist.ChatListViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    appModule: AppModule,
    onChatClick: (chatId: String, chatName: String) -> Unit,
    onLogout: () -> Unit
) {
    val viewModel = remember {
        ChatListViewModel(
            chatRepository = appModule.chatRepository,
            wsService = appModule.wsService
        )
    }
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Чаты") },
                actions = {
                    IconButton(onClick = onLogout) {
                        Icon(
                            Icons.Default.ExitToApp,
                            contentDescription = "Выйти"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
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
                state.chats.isEmpty() -> {
                    Text(
                        text = "Нет чатов",
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(state.chats, key = { it.id }) { chat ->
                            ChatItem(
                                chat = chat,
                                onClick = { onChatClick(chat.id, chat.name ?: "Чат") }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatItem(chat: ChatDto, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = {
            Text(
                text = chat.name ?: "Чат",
                fontWeight = if (chat.unreadCount > 0) FontWeight.Bold else FontWeight.Normal
            )
        },
        supportingContent = {
            chat.lastMessage?.let { msg ->
                Text(
                    text = msg.content,
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
                color = MaterialTheme.colorScheme.primaryContainer
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
            if (chat.unreadCount > 0) {
                Badge { Text("${chat.unreadCount}") }
            }
        }
    )
    HorizontalDivider()
}