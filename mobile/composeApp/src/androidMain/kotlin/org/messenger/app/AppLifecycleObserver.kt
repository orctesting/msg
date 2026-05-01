package org.messenger.app

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.*
import org.messenger.app.service.WsNotificationBridge
import org.messenger.app.service.NotificationSettingsSync
import org.messenger.app.shared.di.AppModule

class AppLifecycleObserver(
    private val appModule: AppModule
) : DefaultLifecycleObserver {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var disconnectJob: Job? = null
    private var wsScope: CoroutineScope? = null

    @Volatile
    var isInForeground: Boolean = false
        private set

    override fun onStart(owner: LifecycleOwner) {
        isInForeground = true
        disconnectJob?.cancel()
        disconnectJob = null

        wsScope?.cancel()
        wsScope = CoroutineScope(SupervisorJob() + Dispatchers.Main).also { s ->
            appModule.wsService.connect(s)
            WsNotificationBridge.observe(
                scope = s,
                context = MessengerApplication.instance,
                wsService = appModule.wsService,
            )
        }

        if (appModule.tokenStorage.isLoggedIn()) {
            scope.launch {
                NotificationSettingsSync.refresh(MessengerApplication.instance, appModule)
            }
            // Принудительный resync списка чатов — ChatListViewModel сам обновит state,
            // если экран открыт. Если другой экран — данные подтянутся при следующем открытии.
            ChatListResyncBus.requestResync()
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        isInForeground = false
        disconnectJob = scope.launch {
            delay(30_000)
            appModule.wsService.disconnect()
            wsScope?.cancel()
            wsScope = null
        }
    }

    companion object {
        @Volatile
        var currentChatId: String? = null
    }
}