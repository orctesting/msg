package org.messenger.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.messenger.app.notifications.DesktopNotificationManager
import org.messenger.app.shared.di.AppModule

class DesktopLifecycle(
    @Volatile private var appModule: AppModule
) {
    private val internalScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var disconnectJob: Job? = null
    private var wsScope: CoroutineScope? = null

    @Volatile
    var currentChatId: String? = null

    fun onAppStart(parentScope: CoroutineScope) {
        startWs(parentScope)
        startNotificationManager()
    }

    fun onWindowRestored(parentScope: CoroutineScope) {
        disconnectJob?.cancel()
        disconnectJob = null
        if (wsScope == null) {
            startWs(parentScope)
        }
    }

    fun onWindowMinimized() {
        disconnectJob?.cancel()
        disconnectJob = internalScope.launch {
            delay(30_000)
            stopWs()
        }
    }

    fun onAppStop() {
        disconnectJob?.cancel()
        stopWs()
        internalScope.cancel()
    }

    fun updateAppModule(newModule: AppModule, parentScope: CoroutineScope) {
        try {
            appModule.wsService.disconnect()
        } catch (_: Exception) {}
        wsScope?.cancel()
        wsScope = null

        appModule = newModule
        startWs(parentScope)
        startNotificationManager()
    }

    private fun startNotificationManager() {
        try {
            DesktopNotificationManager.start(
                wsService = appModule.wsService,
                notificationsRepository = appModule.notificationsRepository,
                chatRepository = appModule.chatRepository,
                tokenStorage = appModule.tokenStorage,
            )
        } catch (_: Exception) {}
    }

    private fun startWs(parentScope: CoroutineScope) {
        wsScope?.cancel()
        val newScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        wsScope = newScope
        try {
            appModule.wsService.connect(newScope)
        } catch (_: Exception) {}
    }

    private fun stopWs() {
        try {
            appModule.wsService.disconnect()
        } catch (_: Exception) {}
        wsScope?.cancel()
        wsScope = null
    }
}