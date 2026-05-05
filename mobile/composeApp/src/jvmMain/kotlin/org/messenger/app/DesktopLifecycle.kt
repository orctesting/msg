package org.messenger.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.messenger.app.notifications.DesktopNotificationManager
import org.messenger.app.shared.di.AppModule

class DesktopLifecycle(
    @Volatile private var appModule: AppModule
) {
    private val internalScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var wsScope: CoroutineScope? = null

    @Volatile
    var currentChatId: String? = null

    fun onAppStart(parentScope: CoroutineScope) {
        startWs(parentScope)
        startNotificationManager()
        ChatListResyncBus.requestResync()
    }

    fun onWindowRestored(parentScope: CoroutineScope) {
        // Окно вернули: WS уже работает; страхуемся на случай если по сети рвалось,
        // и просим список чатов сделать resync (no-op если cooldown ещё активен).
        if (wsScope == null) {
            startWs(parentScope)
        }
        ChatListResyncBus.requestResync()
    }

    fun onWindowMinimized() {
        // Сворачивание больше не отключает WS.
        // Опционально в будущем: послать presence=away.
    }

    fun onAppStop() {
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