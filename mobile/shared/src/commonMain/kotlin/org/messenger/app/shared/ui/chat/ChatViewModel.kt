package org.messenger.app.shared.ui.chat

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.decodeFromJsonElement
import org.messenger.app.shared.data.model.*
import org.messenger.app.shared.data.remote.ApiException
import org.messenger.app.shared.data.remote.WsService
import org.messenger.app.shared.data.remote.appJson
import org.messenger.app.shared.domain.repository.AttachmentsRepository
import org.messenger.app.shared.domain.repository.ChatRepository
import org.messenger.app.shared.domain.repository.ContactsRepository

data class UploadingAttachment(
    val localId: String,
    val filename: String,
    val mimeType: String,
    val sizeBytes: Long,
    val isUploading: Boolean = true,
    val error: String? = null,
    val attachment: AttachmentDto? = null,
    val previewBytes: ByteArray? = null,
)

data class ChatUiState(
    val chatId: String = "",
    val chatName: String = "",
    val chatType: String = "group",
    val messages: List<MessageDto> = emptyList(),
    val pinnedMessage: PinnedMessageDto? = null,
    val peerUser: PeerUserDto? = null,
    val peerIsInContacts: Boolean? = null,
    val peerDismissed: Boolean? = null,
    val readByOthersUpTo: String? = null,
    val draft: String = "",
    val replyTo: MessageDto? = null,
    val editingMessage: MessageDto? = null,
    val selectionMode: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
    val uploadingAttachments: List<UploadingAttachment> = emptyList(),
    val isLoading: Boolean = false,
    val isSending: Boolean = false,
    val hasMore: Boolean = true,
    val error: String? = null,
    val authError: Boolean = false
)

class ChatViewModel(
    private val chatId: String,
    private val chatRepository: ChatRepository,
    private val wsService: WsService,
    private val currentUserId: String? = null,
    private val currentUserRole: String? = null,
    private val contactsRepository: ContactsRepository? = null,
    private val attachmentsRepository: AttachmentsRepository? = null,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow(ChatUiState(chatId = chatId))
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private var loadRetryCount = 0
    private val maxLoadRetries = 3
    private var localIdCounter = 0L

    init {
        loadChatInfo()
        loadMessages()
        observeWs()
    }

    fun loadChatInfo() {
        scope.launch {
            try {
                val chat = chatRepository.getChat(chatId)
                _state.update {
                    it.copy(
                        chatName = chat.name ?: "Чат",
                        chatType = chat.type,
                        pinnedMessage = chat.pinnedMessage,
                        peerUser = chat.peerUser,
                        peerIsInContacts = chat.peerIsInContacts,
                        peerDismissed = chat.peerDismissed,
                    )
                }
            } catch (_: Exception) {}
        }
    }

    fun loadMessages() {
        val current = _state.value
        if (current.isLoading || !current.hasMore || current.authError) return

        scope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val oldestId = _state.value.messages.lastOrNull()?.id
                val page = chatRepository.getMessages(chatId, oldestId)
                val reversed = page.messages.reversed()
                val newReadUpTo = page.readByOthersUpTo ?: _state.value.readByOthersUpTo

                _state.update { st ->
                    val existingIds = st.messages.map { it.id }.toHashSet()
                    val dedup = reversed.filterNot { existingIds.contains(it.id) }
                    st.copy(
                        messages = st.messages + dedup,
                        hasMore = page.hasMore,
                        isLoading = false,
                        readByOthersUpTo = newReadUpTo
                    )
                }
                loadRetryCount = 0

                page.messages.lastOrNull()?.let { msg ->
                    try { chatRepository.markRead(chatId, msg.id) } catch (_: Exception) {}
                }
            } catch (e: ApiException) {
                if (e.statusCode == 401 || e.statusCode == 403) {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            hasMore = false,
                            authError = true,
                            error = "Сессия истекла. Перезайдите в приложение."
                        )
                    }
                } else handleLoadError(e)
            } catch (e: Exception) {
                handleLoadError(e)
            }
        }
    }

    private fun handleLoadError(e: Exception) {
        loadRetryCount++
        _state.update {
            it.copy(
                isLoading = false,
                hasMore = loadRetryCount < maxLoadRetries,
                error = e.message ?: "Ошибка загрузки"
            )
        }
    }

    fun onDraftChanged(text: String) {
        _state.update { it.copy(draft = text) }
    }

    fun setReplyTo(message: MessageDto?) {
        _state.update { it.copy(replyTo = message, editingMessage = null) }
    }

    fun startEdit(message: MessageDto) {
        _state.update {
            it.copy(
                editingMessage = message,
                draft = message.content,
                replyTo = null,
            )
        }
    }

    fun cancelEdit() {
        _state.update { it.copy(editingMessage = null, draft = "") }
    }

    fun enterSelectionMode(messageId: String) {
        _state.update { it.copy(selectionMode = true, selectedIds = setOf(messageId)) }
    }

    fun toggleSelection(messageId: String) {
        _state.update { s ->
            val newSet = if (s.selectedIds.contains(messageId))
                s.selectedIds - messageId
            else s.selectedIds + messageId
            s.copy(
                selectedIds = newSet,
                selectionMode = newSet.isNotEmpty(),
            )
        }
    }

    fun exitSelectionMode() {
        _state.update { it.copy(selectionMode = false, selectedIds = emptySet()) }
    }

    fun canEdit(message: MessageDto): Boolean {
        val isOwn = message.senderId != null && message.senderId == currentUserId
        val isAdmin = currentUserRole == "admin"
        val typeOk = message.messageType.lowercase() in setOf("text", "notification")
        return typeOk && (isOwn || isAdmin)
    }

    fun canDelete(message: MessageDto): Boolean {
        val isOwn = message.senderId != null && message.senderId == currentUserId
        val isAdmin = currentUserRole == "admin"
        return isOwn || isAdmin
    }

    // ── Attachments upload ──

    fun uploadAttachment(filename: String, mimeType: String, bytes: ByteArray) {
        val repo = attachmentsRepository ?: run {
            _state.update { it.copy(error = "Загрузка файлов недоступна") }
            return
        }
        val localId = "local-${++localIdCounter}"
        val previewBytes = if (mimeType.startsWith("image/", ignoreCase = true) && bytes.size <= 10 * 1024 * 1024) {
            bytes
        } else null

        val placeholder = UploadingAttachment(
            localId = localId,
            filename = filename,
            mimeType = mimeType,
            sizeBytes = bytes.size.toLong(),
            previewBytes = previewBytes,
        )
        _state.update { it.copy(uploadingAttachments = it.uploadingAttachments + placeholder) }

        scope.launch {
            try {
                val att = repo.uploadFile(
                    filename = filename,
                    mimeType = mimeType,
                    data = bytes,
                    chatId = chatId,
                )
                _state.update { st ->
                    st.copy(
                        uploadingAttachments = st.uploadingAttachments.map {
                            if (it.localId == localId)
                                it.copy(isUploading = false, attachment = att)
                            else it
                        }
                    )
                }
            } catch (e: Exception) {
                _state.update { st ->
                    st.copy(
                        uploadingAttachments = st.uploadingAttachments.map {
                            if (it.localId == localId)
                                it.copy(isUploading = false, error = e.message ?: "Ошибка загрузки")
                            else it
                        }
                    )
                }
            }
        }
    }

    fun removeUploadingAttachment(localId: String) {
        scope.launch {
            val item = _state.value.uploadingAttachments.firstOrNull { it.localId == localId }
            // Если успели загрузить — пытаемся удалить с сервера
            item?.attachment?.let { att ->
                try { attachmentsRepository?.delete(att.id) } catch (_: Exception) {}
            }
            _state.update { st ->
                st.copy(uploadingAttachments = st.uploadingAttachments.filterNot { it.localId == localId })
            }
        }
    }

    fun send() {
        val s = _state.value
        val text = s.draft.trim()
        val readyAttachments = s.uploadingAttachments.mapNotNull { it.attachment }
        val hasUploadingInProgress = s.uploadingAttachments.any { it.isUploading }

        if (hasUploadingInProgress) {
            _state.update { it.copy(error = "Дождитесь окончания загрузки файлов") }
            return
        }
        if (text.isBlank() && readyAttachments.isEmpty()) return

        val editing = s.editingMessage
        if (editing != null) {
            // edit не поддерживает вложения
            scope.launch {
                _state.update { it.copy(isSending = true) }
                try {
                    val updated = chatRepository.editMessage(chatId, editing.id, text)
                    _state.update { st ->
                        st.copy(
                            messages = st.messages.map { if (it.id == updated.id) updated else it },
                            draft = "",
                            editingMessage = null,
                            isSending = false,
                        )
                    }
                } catch (e: Exception) {
                    _state.update {
                        it.copy(isSending = false, error = e.message ?: "Ошибка редактирования")
                    }
                }
            }
            return
        }

        val replyId = s.replyTo?.id
        scope.launch {
            _state.update { it.copy(isSending = true) }
            try {
                val msg = chatRepository.sendMessage(
                    chatId = chatId,
                    content = text,
                    replyToMessageId = replyId,
                    attachmentIds = readyAttachments.map { it.id },
                )
                val current = _state.value.messages
                if (current.none { it.id == msg.id }) {
                    _state.update {
                        it.copy(
                            messages = listOf(msg) + it.messages,
                            draft = "",
                            replyTo = null,
                            uploadingAttachments = emptyList(),
                            isSending = false,
                        )
                    }
                } else {
                    _state.update {
                        it.copy(
                            draft = "",
                            replyTo = null,
                            uploadingAttachments = emptyList(),
                            isSending = false,
                        )
                    }
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isSending = false, error = e.message ?: "Ошибка отправки")
                }
            }
        }
    }

    fun deleteMessage(messageId: String) {
        scope.launch {
            try {
                chatRepository.deleteMessage(chatId, messageId)
                _state.update { st ->
                    st.copy(messages = st.messages.filterNot { it.id == messageId })
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Ошибка удаления") }
            }
        }
    }

    fun deleteSelected() {
        val ids = _state.value.selectedIds.toList()
        if (ids.isEmpty()) return
        scope.launch {
            try {
                chatRepository.bulkDeleteMessages(chatId, ids)
                _state.update { st ->
                    st.copy(
                        messages = st.messages.filterNot { ids.contains(it.id) },
                        selectionMode = false,
                        selectedIds = emptySet(),
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Ошибка удаления") }
            }
        }
    }

    fun forwardMessage(messageId: String, targetChatId: String) {
        scope.launch {
            try {
                chatRepository.forwardMessage(
                    sourceChatId = chatId,
                    messageId = messageId,
                    targetChatId = targetChatId,
                )
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Ошибка пересылки") }
            }
        }
    }

    fun forwardSelected(targetChatId: String) {
        val ids = _state.value.selectedIds.toList()
        if (ids.isEmpty()) return
        scope.launch {
            try {
                ids.forEach { id ->
                    chatRepository.forwardMessage(
                        sourceChatId = chatId,
                        messageId = id,
                        targetChatId = targetChatId,
                    )
                }
                _state.update { it.copy(selectionMode = false, selectedIds = emptySet()) }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Ошибка пересылки") }
            }
        }
    }

    fun pinMessage(messageId: String) {
        scope.launch {
            try {
                chatRepository.pinMessage(chatId, messageId)
                loadChatInfo()
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Ошибка закрепления") }
            }
        }
    }

    fun unpinMessage(scopeKind: String = "local") {
        scope.launch {
            try {
                chatRepository.unpinMessage(chatId, scopeKind)
                _state.update { it.copy(pinnedMessage = null) }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Ошибка открепления") }
            }
        }
    }

    fun dismissPeerContact() {
        val peerId = _state.value.peerUser?.id ?: return
        val repo = contactsRepository ?: return
        scope.launch {
            try {
                repo.dismissPeer(peerId)
                _state.update { it.copy(peerDismissed = true) }
            } catch (_: Exception) {}
        }
    }

    fun markPeerAddedToContacts() {
        _state.update { it.copy(peerIsInContacts = true) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    private fun observeWs() {
        scope.launch {
            wsService.events.collect { event ->
                when (event.type) {
                    "new_message" -> handleNewMessage(event.data)
                    "message_edited" -> handleEdited(event.data)
                    "message_deleted" -> handleDeleted(event.data)
                    "message_pinned" -> handlePinned(event.data)
                    "message_unpinned" -> handleUnpinned(event.data)
                    "message_read" -> handleRead(event.data)
                    "attachment_ready" -> handleAttachmentReady(event.data)
                }
            }
        }
    }

    private suspend fun handleNewMessage(data: kotlinx.serialization.json.JsonElement) {
        try {
            val payload = appJson.decodeFromJsonElement<WsNewMessage>(data)
            if (payload.chatId != chatId) return
            val current = _state.value.messages
            if (current.none { it.id == payload.message.id }) {
                _state.update { it.copy(messages = listOf(payload.message) + it.messages) }
                try { chatRepository.markRead(chatId, payload.message.id) } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }

    private fun handleEdited(data: kotlinx.serialization.json.JsonElement) {
        try {
            val payload = appJson.decodeFromJsonElement<WsMessageEdited>(data)
            if (payload.chatId != chatId) return
            _state.update { st ->
                st.copy(
                    messages = st.messages.map {
                        if (it.id == payload.message.id) payload.message else it
                    },
                    pinnedMessage = st.pinnedMessage?.let { pin ->
                        if (pin.id == payload.message.id)
                            pin.copy(content = payload.message.content)
                        else pin
                    },
                )
            }
        } catch (_: Exception) {}
    }

    private fun handleDeleted(data: kotlinx.serialization.json.JsonElement) {
        try {
            val payload = appJson.decodeFromJsonElement<WsMessageDeleted>(data)
            if (payload.chatId != chatId) return
            val idSet = payload.messageIds.toSet()
            _state.update { st ->
                st.copy(
                    messages = st.messages.filterNot { idSet.contains(it.id) },
                    pinnedMessage = st.pinnedMessage?.takeUnless { idSet.contains(it.id) },
                    selectedIds = st.selectedIds - idSet,
                )
            }
        } catch (_: Exception) {}
    }

    private fun handlePinned(data: kotlinx.serialization.json.JsonElement) {
        try {
            val payload = appJson.decodeFromJsonElement<WsMessagePinned>(data)
            if (payload.chatId != chatId) return
            scope.launch { loadChatInfo() }
        } catch (_: Exception) {}
    }

    private fun handleUnpinned(data: kotlinx.serialization.json.JsonElement) {
        try {
            val payload = appJson.decodeFromJsonElement<WsMessageUnpinned>(data)
            if (payload.chatId != chatId) return
            _state.update { it.copy(pinnedMessage = null) }
        } catch (_: Exception) {}
    }

    private fun handleRead(data: kotlinx.serialization.json.JsonElement) {
        try {
            val payload = appJson.decodeFromJsonElement<WsMessageRead>(data)
            if (payload.chatId != chatId) return
            if (payload.userId == currentUserId) return
            _state.update { it.copy(readByOthersUpTo = payload.lastReadMessageId) }
        } catch (_: Exception) {}
    }

    private fun handleAttachmentReady(data: kotlinx.serialization.json.JsonElement) {
        try {
            val payload = appJson.decodeFromJsonElement<WsAttachmentReady>(data)
            if (payload.chatId != chatId) return
            // Перезагрузим вложение, чтобы получить thumbnail_url
            scope.launch {
                val updated = try {
                    attachmentsRepository?.getAttachment(payload.attachmentId)
                } catch (_: Exception) { null } ?: return@launch

                _state.update { st ->
                    val newMessages = st.messages.map { msg ->
                        if (msg.id != payload.messageId) msg
                        else msg.copy(
                            attachments = msg.attachments.map {
                                if (it.id == updated.id) updated else it
                            }
                        )
                    }
                    st.copy(messages = newMessages)
                }
            }
        } catch (_: Exception) {}
    }
}