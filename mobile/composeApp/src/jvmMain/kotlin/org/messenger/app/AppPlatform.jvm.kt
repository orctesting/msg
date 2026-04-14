package org.messenger.app

import org.messenger.app.shared.di.AppModule

actual fun updateCurrentChatId(chatId: String?) {
    // No-op on desktop
}

actual fun onLoginSuccessCallback(appModule: AppModule) {
    // No-op on desktop — no FCM
}