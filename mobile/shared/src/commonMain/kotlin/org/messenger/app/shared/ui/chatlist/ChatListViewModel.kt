package org.messenger.app.shared.ui.chatlist

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.datetime.Clock
import org.messenger.app.shared.data.local.TokenStorage
import org.messenger.app.shared.data.model.ChatDto
import org.messenger.app.shared.data.model.WsMessageDeleted
import org.messenger.app.shared.data.model.WsMessageEdited
import org.messenger.app.shared.data.model.WsMessageRead
import org.messenger.app.shared.data.model.WsMessageReadSelf
import org.messenger.app.shared.data.model.WsNewMessage
import org.messenger.app.shared.data.model.WsNewMessageSelf
import org.messenger.app.shared.data.remote.WsService
import org.messenger.app.shared.data.remote.appJson
import org.messenger.app.shared.domain.repository.ChatRepository

data class ChatListUiState(
    val chats: List<ChatDto> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class ChatListViewModel(
    private val chatRepository: ChatRepository,
    private val wsService: WsService,
    private val tokenStorage: TokenStorage? = null,
    private val activeChatIdProvider: () -> String? = { null },
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val currentUserId: String? = tokenStorage?.getUserId()

    private val _state = MutableStateFlow(ChatListUiState())
    val state: StateFlow<ChatListUiState> = _state.asStateFlow()

    init {
        loadChats()
        observeWs()
        observeWsConnection()
    }

    fun loadChats() {
        scope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val chats = chatRepository.getChats()
                _state.value = _state.value.copy(chats = sortChats(chats), isLoading = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "Ошибка загрузки"
                )
            }
        }
    }

    private var lastResyncMs: Long = 0L
    private val resyncCooldownMs: Long = 60_000L // 60 сек

    private fun observeWsConnection() {
        scope.launch {
            wsService.connected.collect { connected ->
                if (connected) {
                    val now = nowMillis()
                    if (now - lastResyncMs >= resyncCooldownMs) {
                        lastResyncMs = now
                        loadChats()
                    }
                }
            }
        }
    }

    private fun nowMillis(): Long = try {
        Clock.System.now().toEpochMilliseconds()
    } catch (_: Throwable) { 0L }

    private fun observeWs() {
        scope.launch {
            wsService.events.collect { event ->
                when (event.type) {
                    "new_message" -> {
                        try {
                            val msg = appJson.decodeFromJsonElement<WsNewMessage>(event.data)
                            updateChatWithNewMessage(
                                chatId = msg.chatId,
                                messageId = msg.message.id,
                                senderId = msg.message.senderId,
                                newLast = msg,
                            )
                        } catch (_: Exception) {}
                    }
                    "new_message_self" -> {
                        // Своё сообщение, пришедшее с другого моего устройства.
                        // Обновляем lastMessage, unread НЕ трогаем.
                        try {
                            val ev = appJson.decodeFromJsonElement<WsNewMessageSelf>(event.data)
                            updateChatWithOwnMessage(
                                chatId = ev.chatId,
                                newLast = WsNewMessage(chatId = ev.chatId, message = ev.message),
                            )
                        } catch (_: Exception) {}
                    }
                    "message_edited" -> {
                        try {
                            val ev = appJson.decodeFromJsonElement<WsMessageEdited>(event.data)
                            updateLastIfMatches(ev.chatId, ev.message.id, ev.message.content)
                        } catch (_: Exception) {}
                    }
                    "message_deleted" -> {
                        try {
                            val ev = appJson.decodeFromJsonElement<WsMessageDeleted>(event.data)
                            val affected = _state.value.chats.any { chat ->
                                chat.id == ev.chatId &&
                                        chat.lastMessage != null &&
                                        ev.messageIds.contains(chat.lastMessage.id)
                            }
                            if (affected) loadChats()
                        } catch (_: Exception) {}
                    }
                    "message_pinned", "message_unpinned" -> {
                        loadChats()
                    }
                    "message_read" -> {
                        // Другой пользователь прочитал — НАС это не касается для unread.
                        // (бэк теперь шлёт message_read другим, не нам)
                        // Но на всякий случай, если событие пришло с нашим user_id — тоже
                        // сбросим (legacy fallback).
                        try {
                            val ev = appJson.decodeFromJsonElement<WsMessageRead>(event.data)
                            if (currentUserId != null && ev.userId == currentUserId) {
                                clearUnreadForChat(ev.chatId)
                            }
                        } catch (_: Exception) {}
                    }
                    "message_read_self" -> {
                        // Другое моё устройство прочитало чат — сбрасываем unread.
                        try {
                            val ev = appJson.decodeFromJsonElement<WsMessageReadSelf>(event.data)
                            clearUnreadForChat(ev.chatId)
                        } catch (_: Exception) {}
                    }
                }
            }
        }
    }

    private fun clearUnreadForChat(chatId: String) {
        val list = _state.value.chats
        val idx = list.indexOfFirst { it.id == chatId }
        if (idx < 0) return
        val current = list[idx]
        if (current.unreadCount == 0) return
        val newList = list.toMutableList().also {
            it[idx] = current.copy(unreadCount = 0)
        }
        _state.value = _state.value.copy(chats = newList)
    }

    private fun updateLastIfMatches(chatId: String, messageId: String, newContent: String) {
        val list = _state.value.chats.toMutableList()
        val idx = list.indexOfFirst {
            it.id == chatId && it.lastMessage?.id == messageId
        }
        if (idx >= 0) {
            val chat = list[idx]
            val updatedLast = chat.lastMessage!!.copy(content = newContent)
            list[idx] = chat.copy(lastMessage = updatedLast)
            _state.value = _state.value.copy(chats = sortChats(list))
        }
    }

    private fun updateChatWithNewMessage(
        chatId: String,
        messageId: String,
        senderId: String?,
        newLast: WsNewMessage,
    ) {
        val current = _state.value.chats.toMutableList()
        val idx = current.indexOfFirst { it.id == chatId }
        if (idx >= 0) {
            val chat = current[idx]
            val isOwn = currentUserId != null && senderId == currentUserId
            current[idx] = chat.copy(
                lastMessage = newLast.message,
                unreadCount = if (isOwn) chat.unreadCount else chat.unreadCount + 1
            )
            _state.value = _state.value.copy(chats = sortChats(current))
        } else {
            loadChats()
        }
    }

    /**
     * Обновление lastMessage для собственного сообщения (пришедшего с другого устройства).
     * unread НЕ меняем.
     */
    private fun updateChatWithOwnMessage(chatId: String, newLast: WsNewMessage) {
        val current = _state.value.chats.toMutableList()
        val idx = current.indexOfFirst { it.id == chatId }
        if (idx >= 0) {
            val chat = current[idx]
            current[idx] = chat.copy(lastMessage = newLast.message)
            _state.value = _state.value.copy(chats = sortChats(current))
        } else {
            loadChats()
        }
    }

    private fun sortChats(input: List<ChatDto>): List<ChatDto> {
        return input.sortedWith(
            compareByDescending<ChatDto> { sortKey(it) }
                .thenByDescending { it.createdAt ?: "" }
        )
    }

    private fun sortKey(chat: ChatDto): String {
        val lastTs = chat.lastMessage?.createdAt
        return if (!lastTs.isNullOrBlank()) lastTs else (chat.createdAt ?: "")
    }
}