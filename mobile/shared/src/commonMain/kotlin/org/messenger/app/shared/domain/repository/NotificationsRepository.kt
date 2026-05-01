package org.messenger.app.shared.domain.repository

import org.messenger.app.shared.data.model.NotificationSettingsItemDto
import org.messenger.app.shared.data.model.NotificationSettingsListDto
import org.messenger.app.shared.data.remote.ApiService

class NotificationsRepository(private val api: ApiService) {

    suspend fun getSettings(): NotificationSettingsListDto =
        api.getNotificationSettings()

    suspend fun updateSettings(
        platform: String,
        mode: String,
        chatIds: List<String>,
    ): NotificationSettingsItemDto =
        api.updateNotificationSettings(platform, mode, chatIds)
}