package org.messenger.app.shared.ui.chat

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.decodeFromJsonElement
import org.messenger.app.shared.data.model.MessageDto
import org.messenger.app.shared.data.model.WsNewMessage
import org.messenger.app.shared.data.remote.WsService
import org.messenger.app.shared.data.remote.appJson
import org.messenger.app.shared.domain.repository.ChatRepository

data class ChatUiState(
    val chatId: String = "",
    val chatName: String = "",
    val messages: List<MessageDto> = emptyList(),
    val draft: String = "",
    val isLoading: Boolean = false,
    val isSending: Boolean = false,
    val hasMore: Boolean = true,
    val error: String? = null
)

class ChatViewModel(
    private val chatId: String,
    private val chatRepository: ChatRepository,
    private val wsService: WsService
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow(ChatUiState(chatId = chatId))
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    init {
        loadChatInfo()
        loadMessages()
        observeWs()
    }

    private fun loadChatInfo() {
        scope.launch {
            try {
                val chat = chatRepository.getChat(chatId)
                _state.value = _state.value.copy(chatName = chat.name ?: "Чат")
            } catch (_: Exception) {}
        }
    }

    fun loadMessages() {
        if (_state.value.isLoading || !_state.value.hasMore) return
        scope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val before = _state.value.messages.lastOrNull()?.id
                val page = chatRepository.getMessages(chatId, before)
                _state.value = _state.value.copy(
                    messages = _state.value.messages + page.messages,
                    hasMore = page.hasMore,
                    isLoading = false
                )
                page.messages.firstOrNull()?.let { msg ->
                    chatRepository.markRead(chatId, msg.id)
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "Ошибка загрузки"
                )
            }
        }
    }

    fun onDraftChanged(text: String) {
        _state.value = _state.value.copy(draft = text)
    }

    fun send() {
        val text = _state.value.draft.trim()
        if (text.isBlank()) return
        scope.launch {
            _state.value = _state.value.copy(isSending = true)
            try {
                val msg = chatRepository.sendMessage(chatId, text)
                _state.value = _state.value.copy(
                    messages = listOf(msg) + _state.value.messages,
                    draft = "",
                    isSending = false
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isSending = false,
                    error = e.message ?: "Ошибка отправки"
                )
            }
        }
    }

    private fun observeWs() {
        scope.launch {
            wsService.events.collect { event ->
                if (event.type == "new_message") {
                    try {
                        val data = appJson.decodeFromJsonElement<WsNewMessage>(event.data)
                        if (data.chatId == chatId) {
                            val current = _state.value.messages
                            if (current.none { it.id == data.message.id }) {
                                _state.value = _state.value.copy(
                                    messages = listOf(data.message) + current
                                )
                                chatRepository.markRead(chatId, data.message.id)
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
        }
    }
}