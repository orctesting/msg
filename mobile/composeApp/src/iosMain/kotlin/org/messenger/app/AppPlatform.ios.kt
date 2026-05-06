package org.messenger.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.messenger.app.shared.di.AppModule

actual fun updateCurrentChatId(chatId: String?) {
    // На iOS активный чат уже трекается через ActiveChatHolder в commonMain
    // (вызывается из setActiveChatId в App.kt). Дополнительно ничего не нужно.
}

actual fun onLoginSuccessCallback(appModule: AppModule) {
    val token = IosAppHolder.lastApnsToken ?: return
    if (!appModule.tokenStorage.isLoggedIn()) return
    CoroutineScope(Dispatchers.Default).launch {
        try {
            appModule.apiService.registerPushToken(token = token, type = "apns")
        } catch (_: Exception) {}
    }
}

actual fun syncAppModule(appModule: AppModule) {
    IosAppHolder.appModule = appModule
}

actual fun observeDeepLinks(): Flow<Pair<String, String>>? {
    return IosDeepLinkBus.events.map { (id, name) -> id to (name ?: "") }
}