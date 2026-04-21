package org.messenger.app.shared.ui.admin

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.messenger.app.shared.data.model.ChatDto
import org.messenger.app.shared.data.model.UserDto
import org.messenger.app.shared.domain.repository.ChatRepository

data class CreateGroupChatUiState(
    val chatName: String = "",
    val searchQuery: String = "",
    val users: List<UserDto> = emptyList(),
    val selectedIds: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val hasMore: Boolean = true,
    val isCreating: Boolean = false,
    val error: String? = null,
    val createdChat: ChatDto? = null,
)

class CreateGroupChatViewModel(
    private val chatRepository: ChatRepository,
    private val currentUserId: String? = null,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var searchJob: Job? = null
    private var offset = 0
    private val pageSize = 30

    private val _state = MutableStateFlow(CreateGroupChatUiState())
    val state: StateFlow<CreateGroupChatUiState> = _state.asStateFlow()

    init {
        loadUsers(reset = true)
    }

    fun onChatNameChanged(name: String) {
        _state.update { it.copy(chatName = name) }
    }

    fun onSearchChanged(query: String) {
        _state.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        searchJob = scope.launch {
            delay(300)
            loadUsers(reset = true)
        }
    }

    fun toggleUser(userId: String) {
        _state.update { s ->
            val newSet = if (s.selectedIds.contains(userId))
                s.selectedIds - userId
            else s.selectedIds + userId
            s.copy(selectedIds = newSet)
        }
    }

    fun loadUsers(reset: Boolean = false) {
        if (reset) {
            offset = 0
            _state.update { it.copy(users = emptyList(), hasMore = true) }
        }
        val s = _state.value
        if (s.isLoading || !s.hasMore) return

        scope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val query = s.searchQuery.trim().takeIf { it.isNotBlank() }
                val page = chatRepository.adminListUsers(
                    offset = offset,
                    limit = pageSize,
                    search = query,
                )
                val filtered = page.filter { it.id != currentUserId }
                offset += page.size
                _state.update {
                    it.copy(
                        users = it.users + filtered,
                        isLoading = false,
                        hasMore = page.size >= pageSize,
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isLoading = false, error = e.message ?: "Ошибка загрузки")
                }
            }
        }
    }

    fun createChat() {
        val s = _state.value
        if (s.chatName.isBlank()) {
            _state.update { it.copy(error = "Введите название чата") }
            return
        }
        if (s.selectedIds.isEmpty()) {
            _state.update { it.copy(error = "Выберите хотя бы одного участника") }
            return
        }
        scope.launch {
            _state.update { it.copy(isCreating = true, error = null) }
            try {
                val chat = chatRepository.adminCreateGroupChat(
                    name = s.chatName.trim(),
                    memberIds = s.selectedIds.toList(),
                )
                _state.update { it.copy(isCreating = false, createdChat = chat) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isCreating = false, error = e.message ?: "Ошибка создания чата")
                }
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}