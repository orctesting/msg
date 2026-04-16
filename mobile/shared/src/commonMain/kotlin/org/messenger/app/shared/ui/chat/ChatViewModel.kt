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
import org.messenger.app.shared.data.model.MessageDto
import org.messenger.app.shared.data.model.WsNewMessage
import org.messenger.app.shared.data.model.WsMessageRead
import org.messenger.app.shared.data.remote.ApiException
import org.messenger.app.shared.data.remote.WsService
import org.messenger.app.shared.data.remote.appJson
import org.messenger.app.shared.domain.repository.ChatRepository

data class ChatUiState(
    val chatId: String = "",
    val chatName: String = "",
    val messages: List<MessageDto> = emptyList(),
    val readByOthersUpTo: String? = null,
    val draft: String = "",
    val isLoading: Boolean = false,
    val isSending: Boolean = false,
    val hasMore: Boolean = true,
    val error: String? = null,
    val authError: Boolean = false
)

class ChatViewModel(
    private val chatId: String,
    private val chatRepository: ChatRepository,
    private val wsService: WsService
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow(ChatUiState(chatId = chatId))
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private var loadRetryCount = 0
    private val maxLoadRetries = 3

    init {
        loadChatInfo()
        loadMessages()
        observeWs()
    }

    private fun loadChatInfo() {
        scope.launch {
            try {
                val chat = chatRepository.getChat(chatId)
                _state.update { it.copy(chatName = chat.name ?: "Чат") }
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

                _state.update {
                    it.copy(
                        messages = it.messages + reversed,
                        hasMore = page.hasMore,
                        isLoading = false,
                        readByOthersUpTo = newReadUpTo
                    )
                }
                loadRetryCount = 0

                page.messages.lastOrNull()?.let { msg ->
                    try {
                        chatRepository.markRead(chatId, msg.id)
                    } catch (_: Exception) {}
                }
            } catch (e: ApiException) {
                if (e.statusCode == 401 || e.statusCode == 403) {
                    // Auth failed even after Ktor's auto-refresh — stop retrying
                    _state.update {
                        it.copy(
                            isLoading = false,
                            hasMore = false,
                            authError = true,
                            error = "Сессия истекла. Перезайдите в приложение."
                        )
                    }
                } else {
                    handleLoadError(e)
                }
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
                // After max retries, stop trying to load more
                hasMore = loadRetryCount < maxLoadRetries,
                error = e.message ?: "Ошибка загрузки"
            )
        }
    }

    fun onDraftChanged(text: String) {
        _state.update { it.copy(draft = text) }
    }

    fun send() {
        val text = _state.value.draft.trim()
        if (text.isBlank()) return
        scope.launch {
            _state.update { it.copy(isSending = true) }
            try {
                val msg = chatRepository.sendMessage(chatId, text)
                val current = _state.value.messages
                if (current.none { it.id == msg.id }) {
                    _state.update {
                        it.copy(
                            messages = listOf(msg) + it.messages,
                            draft = "",
                            isSending = false
                        )
                    }
                } else {
                    _state.update { it.copy(draft = "", isSending = false) }
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isSending = false,
                        error = e.message ?: "Ошибка отправки"
                    )
                }
            }
        }
    }

    private fun observeWs() {
        scope.launch {
            wsService.events.collect { event ->
                when (event.type) {
                    "new_message" -> {
                        try {
                            val data = appJson.decodeFromJsonElement<WsNewMessage>(event.data)
                            if (data.chatId == chatId) {
                                val current = _state.value.messages
                                if (current.none { it.id == data.message.id }) {
                                    _state.update {
                                        it.copy(messages = listOf(data.message) + it.messages)
                                    }
                                    try {
                                        chatRepository.markRead(chatId, data.message.id)
                                    } catch (_: Exception) {}
                                }
                            }
                        } catch (_: Exception) {}
                    }
                    "message_read" -> {
                        try {
                            val data = appJson.decodeFromJsonElement<WsMessageRead>(event.data)
                            if (data.chatId == chatId) {
                                _state.update {
                                    it.copy(readByOthersUpTo = data.lastReadMessageId)
                                }
                            }
                        } catch (_: Exception) {}
                    }
                }
            }
        }
    }
}