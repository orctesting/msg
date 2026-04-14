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
                }
            }
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
            // Поднимаем наверх
            val updated = current.removeAt(idx)
            current.add(0, updated)
            _state.value = _state.value.copy(chats = current)
        } else {
            // Новый чат — перезагрузим
            loadChats()
        }
    }
}