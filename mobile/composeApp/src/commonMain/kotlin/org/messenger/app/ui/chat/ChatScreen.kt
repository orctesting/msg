package org.messenger.app.ui.chat

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Forward
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.launch
import org.messenger.app.shared.data.model.MessageDto
import org.messenger.app.shared.di.AppModule
import org.messenger.app.shared.ui.chat.ChatViewModel
import org.messenger.app.shared.util.copyToClipboard
import org.messenger.app.util.MouseSelectionCallbacks
import org.messenger.app.util.PlatformBackHandler
import org.messenger.app.util.PlatformVerticalScrollbar
import org.messenger.app.util.mouseScrollGestures

private val SELECTION_COLUMN_WIDTH = 36.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun ChatScreen(
    chatId: String,
    chatName: String,
    appModule: AppModule,
    onBack: () -> Unit,
    onPickForwardTarget: ((sourceChatId: String, messageIds: List<String>) -> Unit)? = null,
    onOpenPeerProfile: ((userId: String) -> Unit)? = null,
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
            contactsRepository = appModule.contactsRepository,
            attachmentsRepository = appModule.attachmentsRepository,
        )
    }
    val state by viewModel.state.collectAsState()
    val filePicker = org.messenger.app.util.rememberFilePicker { picked ->
        viewModel.uploadAttachment(
            filename = picked.name,
            mimeType = picked.mimeType,
            bytes = picked.bytes,
        )
    }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    var unreadCount by remember { mutableStateOf(0) }
    var firstUnreadMessageId by remember { mutableStateOf<String?>(null) }
    var lastKnownTopMessageId by remember { mutableStateOf<String?>(null) }
    var showMessageMenuFor by remember { mutableStateOf<MessageDto?>(null) }

    val focusBounds = remember { mutableStateMapOf<String, androidx.compose.ui.geometry.Rect>() }
    var overlayRootCoords by remember {
        mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null)
    }

    // Y-координаты сообщений (стабильная ссылка)
    val messageRowYRanges = remember { mutableStateMapOf<String, ClosedFloatingPointRange<Float>>() }
    var selectAnchorMessageId by remember { mutableStateOf<String?>(null) }

    // Актуальные значения для лямбд (без пересоздания callbacks)
    val currentMessages by rememberUpdatedState(state.messages)
    val currentSelectedIds by rememberUpdatedState(state.selectedIds)
    val currentSelectionMode by rememberUpdatedState(state.selectionMode)

    val isAtBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val firstVisible = info.visibleItemsInfo.firstOrNull()
            firstVisible == null || firstVisible.index == 0
        }
    }

    val stickyDateKey by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val items = info.visibleItemsInfo
            if (items.isEmpty()) return@derivedStateOf null
            val topItem = items.last()
            val key = topItem.key as? String ?: return@derivedStateOf null
            if (!key.startsWith("msg_")) return@derivedStateOf null
            val msgId = key.removePrefix("msg_")
            val msg = state.messages.firstOrNull { it.id == msgId } ?: return@derivedStateOf null
            dayKeyFromIso(msg.createdAt)
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

    PlatformBackHandler(
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

    fun copySelected() {
        val ids = state.selectedIds
        if (ids.isEmpty()) return
        val sortedContents = state.messages
            .filter { ids.contains(it.id) }
            .asReversed()
            .map { it.content }
        copyToClipboard(sortedContents.joinToString("\n\n"))
        viewModel.exitSelectionMode()
    }

    // Стабильные callbacks (создаются один раз)
    val selectionCallbacks = remember {
        MouseSelectionCallbacks(
            onSelectStart = { y ->
                val id = messageRowYRanges.entries.firstOrNull { (_, r) -> y in r }?.key
                if (id != null) {
                    selectAnchorMessageId = id
                    if (!currentSelectionMode) {
                        viewModel.enterSelectionMode(id)
                    } else if (!currentSelectedIds.contains(id)) {
                        viewModel.toggleSelection(id)
                    }
                }
            },
            onSelectUpdate = { y ->
                val anchor = selectAnchorMessageId ?: return@MouseSelectionCallbacks
                val current = messageRowYRanges.entries.firstOrNull { (_, r) -> y in r }?.key
                    ?: return@MouseSelectionCallbacks
                val msgs = currentMessages
                val ai = msgs.indexOfFirst { it.id == anchor }
                val ci = msgs.indexOfFirst { it.id == current }
                if (ai < 0 || ci < 0) return@MouseSelectionCallbacks
                val from = minOf(ai, ci)
                val to = maxOf(ai, ci)
                val rangeIds = msgs.subList(from, to + 1).map { it.id }.toSet()
                val selected = currentSelectedIds
                rangeIds.forEach { mid ->
                    if (!selected.contains(mid)) viewModel.toggleSelection(mid)
                }
                selected.forEach { sid ->
                    if (sid !in rangeIds) viewModel.toggleSelection(sid)
                }
            },
            onSelectEnd = {
                selectAnchorMessageId = null
            },
        )
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
                    title = {
                        val peer = state.peerUser
                        val isPersonalClickable = state.chatType == "personal" &&
                                peer != null &&
                                onOpenPeerProfile != null
                        if (isPersonalClickable) {
                            Text(
                                text = chatName,
                                modifier = Modifier.clickable {
                                    onOpenPeerProfile!!(peer!!.id)
                                }
                            )
                        } else {
                            Text(chatName)
                        }
                    },
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
                    uploadingAttachments = state.uploadingAttachments,
                    onDraftChanged = viewModel::onDraftChanged,
                    onCancelReply = { viewModel.setReplyTo(null) },
                    onCancelEdit = viewModel::cancelEdit,
                    onSend = {
                        viewModel.send()
                        coroutineScope.launch { listState.animateScrollToItem(0) }
                    },
                    onAttachClick = { filePicker.launch("*/*") },
                    onRemoveAttachment = viewModel::removeUploadingAttachment,
                    isSending = state.isSending,
                    onFilesDropped = { files ->
                        files.forEach { f ->
                            viewModel.uploadAttachment(
                                filename = f.name,
                                mimeType = f.mimeType,
                                bytes = f.bytes,
                            )
                        }
                    },
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
                val focusTargetId = state.editingMessage?.id ?: state.replyTo?.id
                val selectionMode = state.selectionMode

                Column(modifier = Modifier.fillMaxSize()) {
                    val peer = state.peerUser
                    if (
                        state.chatType == "personal" &&
                        peer != null &&
                        state.peerIsInContacts == false &&
                        state.peerDismissed != true
                    ) {
                        AddPeerContactPrompt(
                            peer = peer,
                            contactsRepository = appModule.contactsRepository,
                            onDismiss = { viewModel.dismissPeerContact() },
                            onAdded = { viewModel.markPeerAddedToContacts() },
                        )
                    }

                    state.pinnedMessage?.let { pin ->
                        PinnedMessageBar(
                            pin = pin,
                            onClose = { viewModel.unpinMessage("local") },
                            onClick = {
                                val listIdx = computeListIndexForMessage(messages, pin.id, state.hasMore)
                                if (listIdx >= 0) {
                                    coroutineScope.launch { listState.animateScrollToItem(listIdx) }
                                }
                            }
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .onGloballyPositioned { overlayRootCoords = it }
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .mouseScrollGestures(
                                    listState = listState,
                                    selectionCallbacks = selectionCallbacks,
                                ),
                            reverseLayout = true,
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 8.dp,
                                bottom = 8.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            messages.forEachIndexed { index, message ->
                                val isOwn = message.senderId == currentUserId
                                val isRead = isOwn && state.readByOthersUpTo != null &&
                                        isMessageReadByOthers(message, messages, state.readByOthersUpTo)
                                val isSelected = state.selectedIds.contains(message.id)

                                item(key = "msg_${message.id}") {
                                    val isFocused = message.id == focusTargetId
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .onGloballyPositioned { coords ->
                                                val root = overlayRootCoords
                                                if (root != null && coords.isAttached) {
                                                    val pos = root.localPositionOf(
                                                        coords,
                                                        androidx.compose.ui.geometry.Offset.Zero
                                                    )
                                                    val top = pos.y
                                                    val bottom = top + coords.size.height.toFloat()
                                                    messageRowYRanges[message.id] = top..bottom
                                                    if (isFocused) {
                                                        focusBounds[message.id] = androidx.compose.ui.geometry.Rect(
                                                            offset = pos,
                                                            size = androidx.compose.ui.geometry.Size(
                                                                coords.size.width.toFloat(),
                                                                coords.size.height.toFloat()
                                                            )
                                                        )
                                                    }
                                                }
                                            }
                                            .then(
                                                if (selectionMode) {
                                                    Modifier.clickable { viewModel.toggleSelection(message.id) }
                                                } else Modifier
                                            ),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Box(modifier = Modifier.weight(1f)) {
                                            org.messenger.app.util.PlatformContextMenu(
                                                itemsProvider = {
                                                    buildMessageContextMenu(
                                                        message = message,
                                                        canEdit = viewModel.canEdit(message),
                                                        canDelete = viewModel.canDelete(message),
                                                        forwardAvailable = onPickForwardTarget != null,
                                                        onReply = { viewModel.setReplyTo(message) },
                                                        onCopy = {
                                                            org.messenger.app.shared.util.copyToClipboard(message.content)
                                                        },
                                                        onEdit = { viewModel.startEdit(message) },
                                                        onDelete = { viewModel.deleteMessage(message.id) },
                                                        onForward = {
                                                            onPickForwardTarget?.invoke(chatId, listOf(message.id))
                                                        },
                                                        onPin = { viewModel.pinMessage(message.id) },
                                                        onSelect = { viewModel.enterSelectionMode(message.id) },
                                                    )
                                                },
                                            ) {
                                                MessageBubble(
                                                    message = message,
                                                    isOwnMessage = isOwn,
                                                    isReadByOthers = isRead,
                                                    isSelected = isSelected,
                                                    selectionMode = selectionMode,
                                                    attachmentsRepository = appModule.attachmentsRepository,
                                                    onClick = {
                                                        if (selectionMode) {
                                                            viewModel.toggleSelection(message.id)
                                                        }
                                                    },
                                                    onLongClick = {
                                                        if (selectionMode) {
                                                            viewModel.toggleSelection(message.id)
                                                        } else {
                                                            showMessageMenuFor = message
                                                        }
                                                    },
                                                )
                                            }
                                        }
                                        if (selectionMode) {
                                            Box(
                                                modifier = Modifier
                                                    .width(SELECTION_COLUMN_WIDTH)
                                                    .padding(start = 6.dp, end = 6.dp),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                SelectionMark(
                                                    isSelected = isSelected,
                                                    onClick = { viewModel.toggleSelection(message.id) },
                                                )
                                            }
                                        }
                                    }
                                }

                                val currentDay = dayKeyFromIso(message.createdAt)
                                val olderMsg = if (index < messages.size - 1) messages[index + 1] else null
                                val olderDay = olderMsg?.let { dayKeyFromIso(it.createdAt) }
                                val showDateHeader = olderDay != currentDay &&
                                        (olderMsg != null || !state.hasMore)

                                if (showDateHeader) {
                                    item(key = "date_${currentDay}") {
                                        DateSeparator(label = formatDateLabel(currentDay))
                                    }
                                }
                            }

                            if (state.hasMore) {
                                item(key = "loader") {
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

                        // Скроллбар поверх правой кромки
                        PlatformVerticalScrollbar(
                            state = listState,
                            modifier = Modifier.align(Alignment.CenterEnd),
                            reverseLayout = true,
                        )

                        stickyDateKey?.let { key ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.TopCenter)
                            ) {
                                StickyDateHeader(label = formatDateLabel(key))
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
                                                val idx = computeListIndexForMessage(messages, targetId, state.hasMore)
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

                        if (focusTargetId != null) {
                            FocusOverlay(
                                onDismiss = {
                                    if (state.editingMessage != null) viewModel.cancelEdit()
                                    else viewModel.setReplyTo(null)
                                },
                                targetRect = focusBounds[focusTargetId],
                            )
                        }
                    }
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

@Composable
private fun SelectionMark(
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .then(
                if (isSelected) Modifier.background(MaterialTheme.colorScheme.primary)
                else Modifier.border(
                    width = 1.5.dp,
                    color = MaterialTheme.colorScheme.outline,
                    shape = CircleShape,
                )
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (isSelected) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

private fun computeListIndexForMessage(
    messages: List<MessageDto>,
    targetMessageId: String,
    hasMore: Boolean,
): Int {
    var lazyIndex = 0
    messages.forEachIndexed { index, message ->
        if (message.id == targetMessageId) return lazyIndex
        lazyIndex++
        val currentDay = dayKeyFromIso(message.createdAt)
        val olderMsg = if (index < messages.size - 1) messages[index + 1] else null
        val olderDay = olderMsg?.let { dayKeyFromIso(it.createdAt) }
        val showDateHeader = olderDay != currentDay && (olderMsg != null || !hasMore)
        if (showDateHeader) lazyIndex++
    }
    return -1
}

private fun buildMessageContextMenu(
    message: org.messenger.app.shared.data.model.MessageDto,
    canEdit: Boolean,
    canDelete: Boolean,
    forwardAvailable: Boolean,
    onReply: () -> Unit,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onForward: () -> Unit,
    onPin: () -> Unit,
    onSelect: () -> Unit,
): List<org.messenger.app.util.ContextMenuItem> {
    val items = mutableListOf<org.messenger.app.util.ContextMenuItem>()
    items += org.messenger.app.util.ContextMenuItem(
        label = "Ответить",
        icon = androidx.compose.material.icons.Icons.AutoMirrored.Filled.Reply,
        onClick = onReply,
    )
    items += org.messenger.app.util.ContextMenuItem(
        label = "Копировать",
        icon = androidx.compose.material.icons.Icons.Default.ContentCopy,
        onClick = onCopy,
    )
    if (forwardAvailable) {
        items += org.messenger.app.util.ContextMenuItem(
            label = "Переслать",
            icon = androidx.compose.material.icons.Icons.Default.Forward,
            onClick = onForward,
        )
    }
    items += org.messenger.app.util.ContextMenuItem(
        label = "Закрепить",
        icon = androidx.compose.material.icons.Icons.Default.PushPin,
        onClick = onPin,
    )
    if (canEdit) {
        items += org.messenger.app.util.ContextMenuItem(
            label = "Редактировать",
            icon = androidx.compose.material.icons.Icons.Default.Edit,
            onClick = onEdit,
        )
    }
    if (canDelete) {
        items += org.messenger.app.util.ContextMenuItem(
            label = "Удалить",
            icon = androidx.compose.material.icons.Icons.Default.Delete,
            isDestructive = true,
            onClick = onDelete,
        )
    }
    items += org.messenger.app.util.ContextMenuItem(
        label = "Выбрать",
        icon = androidx.compose.material.icons.Icons.Default.Check,
        onClick = onSelect,
    )
    return items
}