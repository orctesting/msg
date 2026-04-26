package org.messenger.app.shared.ui.profile

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.messenger.app.shared.data.model.AttachmentDto
import org.messenger.app.shared.data.model.AvatarDto
import org.messenger.app.shared.domain.repository.AttachmentsRepository
import org.messenger.app.shared.domain.repository.ProfileRepository

data class AvatarsUiState(
    val avatars: List<AvatarDto> = emptyList(),
    val primaryAvatarId: String? = null,
    val isLoading: Boolean = false,
    val isUploading: Boolean = false,
    val pendingSource: AttachmentDto? = null,
    val pendingSourceBytes: ByteArray? = null,
    val error: String? = null,
)

class AvatarsViewModel(
    private val profileRepository: ProfileRepository,
    private val attachmentsRepository: AttachmentsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow(AvatarsUiState())
    val state: StateFlow<AvatarsUiState> = _state.asStateFlow()

    init {
        loadAvatars()
    }

    fun loadAvatars() {
        scope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val list = profileRepository.listAvatars()
                _state.update {
                    it.copy(
                        avatars = list.avatars,
                        primaryAvatarId = list.primaryAvatarId,
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

    /**
     * Этап 1 загрузки нового аватара: пользователь выбрал файл изображения,
     * мы загружаем его в S3 как обычное вложение и сохраняем во временное состояние,
     * чтобы UI открыл диалог crop'а.
     */
    fun pickSourceImage(filename: String, mimeType: String, bytes: ByteArray) {
        if (!mimeType.startsWith("image/", ignoreCase = true)) {
            _state.update { it.copy(error = "Файл должен быть изображением") }
            return
        }
        scope.launch {
            _state.update { it.copy(isUploading = true, error = null) }
            try {
                val att = attachmentsRepository.uploadFile(
                    filename = filename,
                    mimeType = mimeType,
                    data = bytes,
                    chatId = null,
                )
                _state.update {
                    it.copy(
                        isUploading = false,
                        pendingSource = att,
                        pendingSourceBytes = bytes,
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isUploading = false, error = e.message ?: "Ошибка загрузки файла")
                }
            }
        }
    }

    fun cancelPendingSource() {
        _state.update { it.copy(pendingSource = null, pendingSourceBytes = null) }
    }

    /**
     * Этап 2: пользователь подтвердил рамку crop'а (в координатах исходного изображения).
     */
    fun confirmCrop(cropX: Int, cropY: Int, cropSize: Int, onSuccess: () -> Unit = {}) {
        val source = _state.value.pendingSource ?: return
        scope.launch {
            _state.update { it.copy(isUploading = true, error = null) }
            try {
                profileRepository.createAvatar(
                    sourceAttachmentId = source.id,
                    cropX = cropX,
                    cropY = cropY,
                    cropSize = cropSize,
                )
                _state.update {
                    it.copy(
                        isUploading = false,
                        pendingSource = null,
                        pendingSourceBytes = null,
                    )
                }
                loadAvatars()
                onSuccess()
            } catch (e: Exception) {
                _state.update {
                    it.copy(isUploading = false, error = e.message ?: "Ошибка обрезки")
                }
            }
        }
    }

    fun setPrimary(avatarId: String) {
        scope.launch {
            try {
                profileRepository.setPrimaryAvatar(avatarId)
                loadAvatars()
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Ошибка установки") }
            }
        }
    }

    fun deleteAvatar(avatarId: String) {
        scope.launch {
            try {
                profileRepository.deleteAvatar(avatarId)
                _state.update { st ->
                    st.copy(avatars = st.avatars.filterNot { it.id == avatarId })
                }
                loadAvatars()
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Ошибка удаления") }
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}