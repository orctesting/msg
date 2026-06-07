package org.messenger.app.updates

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.messenger.app.shared.data.model.UpdateInfo

data class UpdateUiState(
    val available: UpdateInfo? = null,
    val showDialog: Boolean = false,
    val progress: UpdateProgress = UpdateProgress.Idle,
    val installing: Boolean = false,
)

class UpdateViewModel(
    private val checker: UpdateChecker,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow(UpdateUiState())
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    /** Старт периодических проверок: первая через 1 мин, далее раз в час. */
    fun start() {
        scope.launch {
            delay(UpdateConfig.INITIAL_DELAY_MS)
            checkOnce()
            while (isActive) {
                delay(UpdateConfig.PERIODIC_INTERVAL_MS)
                checkOnce()
            }
        }
    }

    private suspend fun checkOnce() {
        if (_state.value.showDialog || _state.value.installing) return
        try {
            val info = checker.check()
            if (info != null) {
                _state.update { it.copy(available = info, showDialog = true) }
            }
        } catch (_: Exception) {}
    }

    fun postpone() {
        // mandatory нельзя отложить
        if (_state.value.available?.isMandatory == true) return
        checker.postpone()
        _state.update { it.copy(showDialog = false) }
    }

    fun startUpdate() {
        val info = _state.value.available ?: return
        _state.update { it.copy(installing = true, progress = UpdateProgress.Downloading(0, info.fileSizeBytes)) }
        scope.launch {
            try {
                PlatformUpdater.downloadAndInstall(info).collect { p ->
                    _state.update { it.copy(progress = p) }
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(progress = UpdateProgress.Failed(e.message ?: "Ошибка обновления"))
                }
            }
        }
    }

    fun dismissError() {
        _state.update { it.copy(installing = false, progress = UpdateProgress.Idle) }
    }
}