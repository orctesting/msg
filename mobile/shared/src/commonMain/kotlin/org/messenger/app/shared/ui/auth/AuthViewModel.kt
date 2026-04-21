package org.messenger.app.shared.ui.auth

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.messenger.app.shared.data.model.DeviceInfo
import org.messenger.app.shared.data.remote.ApiException
import org.messenger.app.shared.domain.repository.AuthRepository

data class AuthUiState(
    val step: AuthStep = AuthStep.PHONE,
    val phone: String = "",
    val code: String = "",
    val serverAddress: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val isAuthenticated: Boolean = false
)

enum class AuthStep { PHONE, CODE }

class AuthViewModel(
    private val authRepository: AuthRepository,
    private val deviceInfo: DeviceInfo,
    initialServerAddress: String = ""
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow(AuthUiState(
        isAuthenticated = authRepository.isLoggedIn(),
        serverAddress = initialServerAddress
    ))
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun onPhoneChanged(phone: String) {
        _state.value = _state.value.copy(phone = phone, error = null)
    }

    fun onCodeChanged(code: String) {
        _state.value = _state.value.copy(code = code, error = null)
    }

    fun onServerAddressChanged(address: String) {
        _state.value = _state.value.copy(serverAddress = address, error = null)
    }

    fun requestOtp() {
        val phone = _state.value.phone.trim()
        if (phone.isBlank()) {
            _state.value = _state.value.copy(error = "Введите номер телефона")
            return
        }
        if (_state.value.serverAddress.isBlank()) {
            _state.value = _state.value.copy(error = "Введите адрес сервера")
            return
        }
        scope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                authRepository.requestOtp(phone)
                _state.value = _state.value.copy(
                    isLoading = false,
                    step = AuthStep.CODE
                )
            } catch (e: ApiException) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Ошибка ${e.statusCode}: ${e.errorBody}"
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "${e::class.simpleName}: ${e.message}"
                )
            }
        }
    }

    fun verifyOtp() {
        val s = _state.value
        if (s.code.isBlank()) {
            _state.value = s.copy(error = "Введите код")
            return
        }
        scope.launch {
            _state.value = s.copy(isLoading = true, error = null)
            try {
                authRepository.verifyOtp(s.phone.trim(), s.code.trim(), deviceInfo)
                _state.value = _state.value.copy(
                    isLoading = false,
                    isAuthenticated = true
                )
            } catch (e: ApiException) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Ошибка ${e.statusCode}: ${e.errorBody}"
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "${e::class.simpleName}: ${e.message}"
                )
            }
        }
    }

    fun backToPhone() {
        _state.value = _state.value.copy(
            step = AuthStep.PHONE,
            code = "",
            error = null
        )
    }

    fun logout() {
        scope.launch {
            try { authRepository.logout() } catch (_: Exception) {}
            _state.value = AuthUiState()
        }
    }
}