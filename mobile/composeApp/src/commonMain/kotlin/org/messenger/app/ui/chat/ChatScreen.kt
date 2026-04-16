// mobile/composeApp/src/commonMain/kotlin/org/messenger/app/ui/chat/ChatScreen.kt
package org.messenger.app.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.derivedStateOf
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
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
    val coroutineScope = rememberCoroutineScope()

    // Счётчик непрочитанных: сообщения, пришедшие когда список был прокручен вверх
    var unreadCount by remember { mutableStateOf(0) }
    // id первого непрочитанного (самого старого из новоприбывших, показан как якорь)
    var firstUnreadMessageId by remember { mutableStateOf<String?>(null) }
    var lastKnownTopMessageId by remember { mutableStateOf<String?>(null) }

    // Определяем, виден ли последний (самый новый) элемент — в reverseLayout это index 0
    val isAtBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val firstVisible = info.visibleItemsInfo.firstOrNull()
            firstVisible == null || firstVisible.index == 0
        }
    }

    // Автоскролл вниз + обработка непрочитанных при появлении новых сообщений
    LaunchedEffect(state.messages.firstOrNull()?.id) {
        val topMsg = state.messages.firstOrNull() ?: return@LaunchedEffect
        if (topMsg.id == lastKnownTopMessageId) return@LaunchedEffect
        val isOwn = topMsg.senderId != null && topMsg.senderId == currentUserId

        if (isAtBottom || isOwn) {
            // Автоскролл к низу
            coroutineScope.launch {
                listState.animateScrollToItem(0)
            }
            unreadCount = 0
            firstUnreadMessageId = null
        } else {
            unreadCount += 1
            if (firstUnreadMessageId == null) {
                firstUnreadMessageId = topMsg.id
            }
        }
        lastKnownTopMessageId = topMsg.id
    }

    // Когда пользователь сам доскроллил вниз — сбрасываем счётчик
    LaunchedEffect(isAtBottom) {
        if (isAtBottom) {
            unreadCount = 0
            firstUnreadMessageId = null
        }
    }

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
                onSend = {
                    viewModel.send()
                    coroutineScope.launch {
                        listState.animateScrollToItem(0)
                    }
                },
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
                val messages = state.messages

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    reverseLayout = true,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(
                        items = messages,
                        key = { _, msg -> msg.id }
                    ) { index, message ->
                        val isOwn = message.senderId == currentUserId
                        val isRead = isOwn && state.readByOthersUpTo != null &&
                                isMessageReadByOthers(message, messages, state.readByOthersUpTo)

                        val currentDay = dayKeyFromIso(message.createdAt)
                        val olderMsg = if (index < messages.size - 1) messages[index + 1] else null
                        val olderDay = olderMsg?.let { dayKeyFromIso(it.createdAt) }
                        val showDateHeader = olderDay != currentDay &&
                                (olderMsg != null || !state.hasMore)

                        Column {
                            if (showDateHeader) {
                                DateSeparator(label = formatDateLabel(currentDay))
                            }
                            MessageBubble(
                                message = message,
                                isOwnMessage = isOwn,
                                isReadByOthers = isRead
                            )
                        }
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

                // Sticky header — день самого верхнего видимого сообщения
                val topVisibleDay by remember {
                    derivedStateOf {
                        val visibleItems = listState.layoutInfo.visibleItemsInfo
                        if (visibleItems.isEmpty() || messages.isEmpty()) return@derivedStateOf null
                        val topItem = visibleItems.last()
                        val msg = messages.getOrNull(topItem.index) ?: return@derivedStateOf null
                        dayKeyFromIso(msg.createdAt)
                    }
                }

                topVisibleDay?.let { day ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        DateSeparator(label = formatDateLabel(day))
                    }
                }

                // Кнопка «вниз» с индикатором непрочитанных
                AnimatedVisibility(
                    visible = !isAtBottom,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                ) {
                    Box {
                        FloatingActionButton(
                            onClick = {
                                coroutineScope.launch {
                                    val targetId = firstUnreadMessageId
                                    if (targetId != null && unreadCount > 0) {
                                        val idx = messages.indexOfFirst { it.id == targetId }
                                        if (idx >= 0) {
                                            listState.animateScrollToItem(idx)
                                        } else {
                                            listState.animateScrollToItem(0)
                                        }
                                        firstUnreadMessageId = null
                                        unreadCount = 0
                                    } else {
                                        listState.animateScrollToItem(0)
                                        unreadCount = 0
                                        firstUnreadMessageId = null
                                    }
                                }
                            },
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Вниз")
                        }
                        if (unreadCount > 0) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 4.dp, y = (-4).dp)
                                    .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DateSeparator(label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
        ) {
            Text(
                text = label,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
    val type = message.messageType?.lowercase() ?: "text"
    val isNotification = type == "notification"
    val isSystem = type == "system"
    val isAdmin = message.senderRole?.lowercase() == "admin"

    // Системные уведомления — по центру, красное выделение, без имени/иконки/времени
    if (isNotification) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = Color(0xFFFFEBEE), // светло-красный фон
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF5350)),
                modifier = Modifier.widthIn(max = 320.dp)
            ) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFC62828),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
        return
    }

    // Старый "system" стиль — оставляем как был
    if (isSystem) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = Color(0xFFFFF3E0),
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

    // Обычные text-сообщения (в т.ч. от админа — светло-красные, как раньше)
    val bubbleColor = when {
        isOwnMessage -> MaterialTheme.colorScheme.primaryContainer
        isAdmin -> Color(0xFFFFCDD2)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    val nameColor = when {
        isAdmin -> Color(0xFFD32F2F)
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