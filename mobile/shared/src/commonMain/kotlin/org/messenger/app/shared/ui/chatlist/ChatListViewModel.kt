package org.messenger.app.shared.ui.chatlist

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.decodeFromJsonElement
import org.messenger.app.shared.data.model.ChatDto
import org.messenger.app.shared.data.model.WsMessageDeleted
import org.messenger.app.shared.data.model.WsMessageEdited
import org.messenger.app.shared.data.model.WsNewMessage
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
    private val wsService: WsService
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow(ChatListUiState())
    val state: StateFlow<ChatListUiState> = _state.asStateFlow()

    init {
        loadChats()
        observeWs()
    }

    fun loadChats() {
        scope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val chats = chatRepository.getChats()
                _state.value = _state.value.copy(chats = chats, isLoading = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "Ошибка загрузки"
                )
            }
        }
    }

    private fun observeWs() {
        scope.launch {
            wsService.events.collect { event ->
                when (event.type) {
                    "new_message" -> {
                        try {
                            val msg = appJson.decodeFromJsonElement<WsNewMessage>(event.data)
                            updateChatWithNewMessage(msg)
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
                            // Если удалено last_message — проще перезагрузить список
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
                }
            }
        }
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
            _state.value = _state.value.copy(chats = list)
        }
    }

    private fun updateChatWithNewMessage(msg: WsNewMessage) {
        val current = _state.value.chats.toMutableList()
        val idx = current.indexOfFirst { it.id == msg.chatId }
        if (idx >= 0) {
            val chat = current[idx]
            current[idx] = chat.copy(
                lastMessage = msg.message,
                unreadCount = chat.unreadCount + 1
            )
            val updated = current.removeAt(idx)
            current.add(0, updated)
            _state.value = _state.value.copy(chats = current)
        } else {
            loadChats()
        }
    }
}