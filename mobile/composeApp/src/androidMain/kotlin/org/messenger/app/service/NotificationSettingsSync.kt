package org.messenger.app.service

import android.content.Context
import org.messenger.app.shared.di.AppModule

object NotificationSettingsSync {

    suspend fun refresh(context: Context, appModule: AppModule) {
        try {
            val list = appModule.notificationsRepository.getSettings()
            val androidItem = list.items.firstOrNull { it.platform == "android" }
            if (androidItem != null) {
                NotificationSettingsCache.saveSettings(
                    context = context,
                    mode = androidItem.mode,
                    whitelistChatIds = androidItem.whitelistChatIds,
                )
            } else {
                NotificationSettingsCache.saveSettings(
                    context = context,
                    mode = "all",
                    whitelistChatIds = emptyList(),
                )
            }
        } catch (_: Exception) {
            // Игнорируем — на следующем запуске попробуем снова
        }
    }
}