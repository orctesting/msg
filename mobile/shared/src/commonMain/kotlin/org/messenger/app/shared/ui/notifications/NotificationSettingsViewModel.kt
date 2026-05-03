package org.messenger.app.shared.ui.notifications

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.messenger.app.shared.data.model.ChatDto
import org.messenger.app.shared.data.model.NotificationMode
import org.messenger.app.shared.data.model.NotificationSettingsItemDto
import org.messenger.app.shared.domain.repository.ChatRepository
import org.messenger.app.shared.domain.repository.NotificationsRepository

data class NotificationSettingsUiState(
    val platform: String = "",
    val mode: String = NotificationMode.ALL,
    val whitelistChatIds: Set<String> = emptySet(),
    val allChats: List<ChatDto> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val savedSuccess: Boolean = false,
)

class NotificationSettingsViewModel(
    private val platform: String,
    private val notificationsRepository: NotificationsRepository,
    private val chatRepository: ChatRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow(NotificationSettingsUiState(platform = platform))
    val state: StateFlow<NotificationSettingsUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        scope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val list = notificationsRepository.getSettings()
                val item: NotificationSettingsItemDto = list.items.firstOrNull { it.platform == platform }
                    ?: NotificationSettingsItemDto(
                        platform = platform,
                        mode = NotificationMode.ALL,
                        whitelistChatIds = emptyList(),
                    )
                val chats = try { chatRepository.getChats() } catch (_: Exception) { emptyList() }
                _state.update {
                    it.copy(
                        mode = item.mode,
                        whitelistChatIds = item.whitelistChatIds.toSet(),
                        allChats = chats,
                        isLoading = false,
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isLoading = false, error = e.message ?: "Ошибка загрузки")
                }
            }
        }
    }

    fun setMode(mode: String) {
        _state.update { it.copy(mode = mode, savedSuccess = false) }
    }

    fun toggleChat(chatId: String) {
        _state.update { s ->
            val newSet = if (s.whitelistChatIds.contains(chatId))
                s.whitelistChatIds - chatId
            else s.whitelistChatIds + chatId
            s.copy(whitelistChatIds = newSet, savedSuccess = false)
        }
    }

    fun setWhitelist(ids: Set<String>) {
        _state.update { it.copy(whitelistChatIds = ids, savedSuccess = false) }
    }

    fun save(onSuccess: () -> Unit = {}) {
        scope.launch {
            _state.update { it.copy(isSaving = true, error = null, savedSuccess = false) }
            try {
                val s = _state.value
                val ids = if (s.mode == NotificationMode.WHITELIST)
                    s.whitelistChatIds.toList() else emptyList()
                notificationsRepository.updateSettings(
                    platform = platform,
                    mode = s.mode,
                    chatIds = ids,
                )
                _state.update { it.copy(isSaving = false, savedSuccess = true) }
                onSuccess()
            } catch (e: Exception) {
                _state.update {
                    it.copy(isSaving = false, error = e.message ?: "Ошибка сохранения")
                }
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}