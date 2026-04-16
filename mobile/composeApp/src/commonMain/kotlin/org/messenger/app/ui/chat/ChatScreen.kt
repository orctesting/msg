package org.messenger.app.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.messenger.app.shared.data.model.MessageDto
import org.messenger.app.shared.di.AppModule
import org.messenger.app.shared.ui.chat.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chatId: String,
    chatName: String,
    appModule: AppModule,
    onBack: () -> Unit
) {
    val viewModel = remember(chatId) {
        ChatViewModel(
            chatId = chatId,
            chatRepository = appModule.chatRepository,
            wsService = appModule.wsService
        )
    }
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()
    val currentUserId = remember { appModule.tokenStorage.getUserId() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(chatName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        },
        bottomBar = {
            MessageInput(
                draft = state.draft,
                onDraftChanged = viewModel::onDraftChanged,
                onSend = viewModel::send,
                isSending = state.isSending
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (state.isLoading && state.messages.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    reverseLayout = true,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(state.messages, key = { it.id }) { message ->
                        val isOwn = message.senderId == currentUserId
                        val isRead = isOwn && state.readByOthersUpTo != null &&
                                isMessageReadByOthers(message, state.messages, state.readByOthersUpTo)
                        MessageBubble(
                            message = message,
                            isOwnMessage = isOwn,
                            isReadByOthers = isRead
                        )
                    }

                    if (state.hasMore) {
                        item {
                            LaunchedEffect(Unit) {
                                viewModel.loadMessages()
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun isMessageReadByOthers(
    message: MessageDto,
    messages: List<MessageDto>,
    readUpToId: String?
): Boolean {
    if (readUpToId == null) return false
    if (message.id == readUpToId) return true
    val readIdx = messages.indexOfFirst { it.id == readUpToId }
    val msgIdx = messages.indexOfFirst { it.id == message.id }
    if (readIdx == -1 || msgIdx == -1) return false
    return msgIdx >= readIdx
}

private fun formatTime(createdAt: String): String {
    return try {
        val timePart = if (createdAt.contains("T")) {
            createdAt.substringAfter("T").substringBefore(".")
                .substringBefore("+").substringBefore("Z")
        } else createdAt
        timePart.take(5)
    } catch (_: Exception) {
        ""
    }
}

@Composable
private fun MessageInput(
    draft: String,
    onDraftChanged: (String) -> Unit,
    onSend: () -> Unit,
    isSending: Boolean
) {
    Surface(
        tonalElevation = 3.dp,
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChanged,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Сообщение...") },
                maxLines = 4
            )

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = onSend,
                enabled = !isSending && draft.isNotBlank()
            ) {
                if (isSending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.Send, contentDescription = "Отправить")
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: MessageDto,
    isOwnMessage: Boolean,
    isReadByOthers: Boolean = false
) {
    val isSystem = message.messageType == "system"
    val isAdmin = message.senderRole == "admin"

    // System messages centered
    if (isSystem) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = Color(0xFFFFF3E0), // light orange for system
                modifier = Modifier.widthIn(max = 300.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF795548)
                    )
                    Text(
                        text = formatTime(message.createdAt),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = Color(0xFF795548).copy(alpha = 0.6f),
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            }
        }
        return
    }

    // Determine bubble color
    val bubbleColor = when {
        isOwnMessage -> MaterialTheme.colorScheme.primaryContainer
        isAdmin -> Color(0xFFFFCDD2) // light red for admin messages
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    val nameColor = when {
        isAdmin -> Color(0xFFD32F2F) // red for admin name
        else -> MaterialTheme.colorScheme.primary
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isOwnMessage) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = bubbleColor,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                if (!isOwnMessage) {
                    Text(
                        text = when {
                            isAdmin -> "Admin"
                            !message.senderName.isNullOrBlank() -> message.senderName!!
                            else -> "Пользователь"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = nameColor
                    )
                }
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = formatTime(message.createdAt),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    if (isOwnMessage) {
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "✓✓",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = if (isReadByOthers)
                                Color(0xFF2196F3)
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}