package org.messenger.app.ui.chat

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.messenger.app.shared.data.model.MessageDto
import org.messenger.app.shared.di.AppModule
import org.messenger.app.shared.ui.chat.ChatViewModel
import org.messenger.app.shared.util.copyToClipboard

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun ChatScreen(
    chatId: String,
    chatName: String,
    appModule: AppModule,
    onBack: () -> Unit,
    onPickForwardTarget: ((sourceChatId: String, messageIds: List<String>) -> Unit)? = null,
) {
    val currentUserId = remember { appModule.tokenStorage.getUserId() }
    val currentUserRole = remember { null as String? }

    val viewModel = remember(chatId) {
        ChatViewModel(
            chatId = chatId,
            chatRepository = appModule.chatRepository,
            wsService = appModule.wsService,
            currentUserId = currentUserId,
            currentUserRole = currentUserRole,
        )
    }
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    var unreadCount by remember { mutableStateOf(0) }
    var firstUnreadMessageId by remember { mutableStateOf<String?>(null) }
    var lastKnownTopMessageId by remember { mutableStateOf<String?>(null) }
    var showMessageMenuFor by remember { mutableStateOf<MessageDto?>(null) }

    val isAtBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val firstVisible = info.visibleItemsInfo.firstOrNull()
            firstVisible == null || firstVisible.index == 0
        }
    }

    LaunchedEffect(state.messages.firstOrNull()?.id) {
        val topMsg = state.messages.firstOrNull() ?: return@LaunchedEffect
        if (topMsg.id == lastKnownTopMessageId) return@LaunchedEffect
        val isOwn = topMsg.senderId != null && topMsg.senderId == currentUserId

        if (isAtBottom || isOwn) {
            coroutineScope.launch { listState.animateScrollToItem(0) }
            unreadCount = 0
            firstUnreadMessageId = null
        } else {
            unreadCount += 1
            if (firstUnreadMessageId == null) firstUnreadMessageId = topMsg.id
        }
        lastKnownTopMessageId = topMsg.id
    }

    LaunchedEffect(isAtBottom) {
        if (isAtBottom) {
            unreadCount = 0
            firstUnreadMessageId = null
        }
    }

    // Обработка системного "назад"
    org.messenger.app.util.PlatformBackHandler(
        enabled = true,
        onBack = {
            when {
                showMessageMenuFor != null -> showMessageMenuFor = null
                state.selectionMode -> viewModel.exitSelectionMode()
                state.editingMessage != null -> viewModel.cancelEdit()
                state.replyTo != null -> viewModel.setReplyTo(null)
                else -> onBack()
            }
        }
    )

    // Helper: копирование выделенных сообщений
    fun copySelected() {
        val ids = state.selectedIds
        if (ids.isEmpty()) return
        // Сохраняем порядок по списку messages (сверху вниз по времени)
        val sortedContents = state.messages
            .filter { ids.contains(it.id) }
            .asReversed() // messages в state идут от новых к старым; reversed => от старых к новым
            .map { it.content }
        copyToClipboard(sortedContents.joinToString("\n\n"))
        viewModel.exitSelectionMode()
    }

    Scaffold(
        topBar = {
            if (state.selectionMode) {
                SelectionTopBar(
                    count = state.selectedIds.size,
                    onClose = viewModel::exitSelectionMode,
                    onDelete = viewModel::deleteSelected,
                    onCopy = { copySelected() },
                    onForward = if (onPickForwardTarget != null) {
                        {
                            onPickForwardTarget(chatId, state.selectedIds.toList())
                            viewModel.exitSelectionMode()
                        }
                    } else null
                )
            } else {
                TopAppBar(
                    title = { Text(chatName) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (state.selectionMode) {
                SelectionBottomBar(
                    selectedCount = state.selectedIds.size,
                    onReply = if (state.selectedIds.size == 1) {
                        {
                            val id = state.selectedIds.first()
                            val msg = state.messages.firstOrNull { it.id == id }
                            if (msg != null) {
                                viewModel.setReplyTo(msg)
                                viewModel.exitSelectionMode()
                            }
                        }
                    } else null,
                    onForward = {
                        if (onPickForwardTarget != null) {
                            onPickForwardTarget(chatId, state.selectedIds.toList())
                            viewModel.exitSelectionMode()
                        }
                    },
                )
            } else {
                MessageInput(
                    draft = state.draft,
                    replyTo = state.replyTo,
                    editing = state.editingMessage,
                    onDraftChanged = viewModel::onDraftChanged,
                    onCancelReply = { viewModel.setReplyTo(null) },
                    onCancelEdit = viewModel::cancelEdit,
                    onSend = {
                        viewModel.send()
                        coroutineScope.launch { listState.animateScrollToItem(0) }
                    },
                    isSending = state.isSending
                )
            }
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

                Column(modifier = Modifier.fillMaxSize()) {
                    state.pinnedMessage?.let { pin ->
                        PinnedMessageBar(
                            pin = pin,
                            onClose = { viewModel.unpinMessage("local") },
                            onClick = {
                                val idx = messages.indexOfFirst { it.id == pin.id }
                                if (idx >= 0) {
                                    coroutineScope.launch { listState.animateScrollToItem(idx) }
                                }
                            }
                        )
                    }

                    Box(modifier = Modifier.weight(1f)) {
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
                                val isSelected = state.selectedIds.contains(message.id)

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
                                        isReadByOthers = isRead,
                                        isSelected = isSelected,
                                        selectionMode = state.selectionMode,
                                        onClick = {
                                            if (state.selectionMode) {
                                                viewModel.toggleSelection(message.id)
                                            }
                                        },
                                        onLongClick = {
                                            if (state.selectionMode) {
                                                viewModel.toggleSelection(message.id)
                                            } else {
                                                showMessageMenuFor = message
                                            }
                                        },
                                    )
                                }
                            }

                            if (state.hasMore) {
                                item {
                                    LaunchedEffect(Unit) { viewModel.loadMessages() }
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                    }
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp)
                        ) {
                            androidx.compose.animation.AnimatedVisibility(
                                visible = !isAtBottom,
                                enter = fadeIn(),
                                exit = fadeOut(),
                            ) {
                                ScrollToBottomButton(
                                    unreadCount = unreadCount,
                                    onClick = {
                                        coroutineScope.launch {
                                            val targetId = firstUnreadMessageId
                                            if (targetId != null && unreadCount > 0) {
                                                val idx = messages.indexOfFirst { it.id == targetId }
                                                if (idx >= 0) listState.animateScrollToItem(idx)
                                                else listState.animateScrollToItem(0)
                                            } else listState.animateScrollToItem(0)
                                            firstUnreadMessageId = null
                                            unreadCount = 0
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
                // Затемнение + фокус при edit/reply
                val focusTargetId = state.editingMessage?.id ?: state.replyTo?.id
                if (focusTargetId != null) {
                    LaunchedEffect(focusTargetId) {
                        val idx = state.messages.indexOfFirst { it.id == focusTargetId }
                        if (idx >= 0) {
                            coroutineScope.launch { listState.animateScrollToItem(idx) }
                        }
                    }
                    FocusOverlay(
                        onDismiss = {
                            if (state.editingMessage != null) viewModel.cancelEdit()
                            else viewModel.setReplyTo(null)
                        },
                        excludeMessageId = focusTargetId,
                        listState = listState,
                    )
                }
            }

            showMessageMenuFor?.let { msg ->
                MessageActionsSheet(
                    message = msg,
                    canEdit = viewModel.canEdit(msg),
                    canDelete = viewModel.canDelete(msg),
                    onDismiss = { showMessageMenuFor = null },
                    onReply = {
                        viewModel.setReplyTo(msg)
                        showMessageMenuFor = null
                    },
                    onCopy = {
                        copyToClipboard(msg.content)
                        showMessageMenuFor = null
                    },
                    onEdit = {
                        viewModel.startEdit(msg)
                        showMessageMenuFor = null
                    },
                    onDelete = {
                        viewModel.deleteMessage(msg.id)
                        showMessageMenuFor = null
                    },
                    onForward = {
                        onPickForwardTarget?.invoke(chatId, listOf(msg.id))
                        showMessageMenuFor = null
                    },
                    onPin = {
                        viewModel.pinMessage(msg.id)
                        showMessageMenuFor = null
                    },
                    onSelect = {
                        viewModel.enterSelectionMode(msg.id)
                        showMessageMenuFor = null
                    },
                    forwardAvailable = onPickForwardTarget != null,
                )
            }
        }
    }
}