package org.messenger.app

import org.messenger.app.shared.di.AppModule

actual fun updateCurrentChatId(chatId: String?) {
    // No-op on iOS for now
}

actual fun onLoginSuccessCallback(appModule: AppModule) {
    // iOS: APNS registration will be handled separately
}