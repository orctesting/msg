package org.messenger.app

import org.messenger.app.shared.di.AppModule

actual fun updateCurrentChatId(chatId: String?) {}
actual fun onLoginSuccessCallback(appModule: AppModule) {}
actual fun syncAppModule(appModule: AppModule) {}

actual fun observeDeepLinks(): kotlinx.coroutines.flow.Flow<Pair<String, String>>? = null