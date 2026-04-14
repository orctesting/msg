package org.messenger.app

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.*
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
        // App moved to foreground
        isInForeground = true
        disconnectJob?.cancel()
        disconnectJob = null

        // Reconnect WS
        wsScope?.cancel()
        wsScope = CoroutineScope(SupervisorJob() + Dispatchers.Main).also {
            appModule.wsService.connect(it)
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        // App moved to background — disconnect WS after 30 sec
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