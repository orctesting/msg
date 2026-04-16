package org.messenger.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.messenger.app.service.FcmTokenManager
import org.messenger.app.shared.di.AppModule

actual fun updateCurrentChatId(chatId: String?) {
    AppLifecycleObserver.currentChatId = chatId
}

actual fun onLoginSuccessCallback(appModule: AppModule) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            FcmTokenManager.registerIfNeeded(
                MessengerApplication.instance,
                appModule.apiService
            )
        } catch (_: Exception) {}
    }
}

actual fun syncAppModule(appModule: AppModule) {
    MessengerApplication.instance.updateAppModule(appModule)
}