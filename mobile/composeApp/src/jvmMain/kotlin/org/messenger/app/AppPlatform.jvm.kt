package org.messenger.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import org.messenger.app.shared.di.AppModule

/**
 * Holder для lifecycle: позволяет syncAppModule переключать WS на новый AppModule.
 */
internal object DesktopLifecycleHolder {
    @Volatile var instance: DesktopLifecycle? = null
    @Volatile var scope: CoroutineScope? = null
}

actual fun updateCurrentChatId(chatId: String?) {
    DesktopLifecycleHolder.instance?.currentChatId = chatId
}

actual fun onLoginSuccessCallback(appModule: AppModule) {
    // Desktop: push-уведомлений нет — no-op.
}

actual fun syncAppModule(appModule: AppModule) {
    val lc = DesktopLifecycleHolder.instance ?: return
    val sc = DesktopLifecycleHolder.scope ?: return
    lc.updateAppModule(appModule, sc)
}

actual fun observeDeepLinks(): Flow<Pair<String, String>>? = null