package org.messenger.app.shared.ui.profile

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.messenger.app.shared.data.local.TokenStorage
import org.messenger.app.shared.data.model.MeDto
import org.messenger.app.shared.data.remote.ApiException
import org.messenger.app.shared.domain.repository.ProfileRepository

data class ProfileUiState(
    val me: MeDto? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val savedSuccess: Boolean = false,
)

class ProfileViewModel(
    private val repository: ProfileRepository,
    private val tokenStorage: TokenStorage? = null,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    init {
        loadProfile()
    }

    fun loadProfile() {
        scope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val me = repository.getMe()
                _state.update { it.copy(me = me, isLoading = false) }
                syncTokenStorage(me)
            } catch (e: ApiException) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = "Ошибка ${e.statusCode}: ${e.errorBody}",
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isLoading = false, error = e.message ?: "Ошибка загрузки")
                }
            }
        }
    }

    fun updateProfile(
        username: String? = null,
        displayName: String? = null,
        firstName: String? = null,
        lastName: String? = null,
        birthDate: String? = null,
        bio: String? = null,
        email: String? = null,
        onSuccess: () -> Unit = {},
    ) {
        scope.launch {
            _state.update { it.copy(isSaving = true, error = null, savedSuccess = false) }
            try {
                val me = repository.updateMe(
                    username = username,
                    displayName = displayName,
                    firstName = firstName,
                    lastName = lastName,
                    birthDate = birthDate,
                    bio = bio,
                    email = email,
                )
                _state.update { it.copy(me = me, isSaving = false, savedSuccess = true) }
                syncTokenStorage(me)
                onSuccess()
            } catch (e: ApiException) {
                val msg = parseErrorMessage(e.errorBody) ?: "Ошибка ${e.statusCode}"
                _state.update { it.copy(isSaving = false, error = msg) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isSaving = false, error = e.message ?: "Ошибка сохранения")
                }
            }
        }
    }

    fun refreshAfterAvatarChange() {
        loadProfile()
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun clearSavedFlag() {
        _state.update { it.copy(savedSuccess = false) }
    }

    private fun syncTokenStorage(me: MeDto) {
        tokenStorage?.saveUser(
            id = me.id,
            phone = me.phone,
            displayName = me.displayName,
            role = me.role,
        )
    }

    private fun parseErrorMessage(body: String): String? {
        return try {
            val detailRegex = """"detail"\s*:\s*"([^"]+)"""".toRegex()
            detailRegex.find(body)?.groupValues?.getOrNull(1)
        } catch (_: Exception) { null }
    }
}