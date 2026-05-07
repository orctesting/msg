package org.messenger.app.shared.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

object NotificationMode {
    const val ALL = "all"
    const val PERSONAL_ONLY = "personal_only"
    const val WHITELIST = "whitelist"
    const val NONE = "none"
}

object NotificationPlatform {
    const val ANDROID = "android"
    const val IOS = "ios"
    const val DESKTOP = "desktop"
    const val WEB = "web"
}

@Serializable
data class NotificationSettingsItemDto(
    val platform: String,
    val mode: String,
    @SerialName("whitelist_chat_ids")
    val whitelistChatIds: List<String> = emptyList(),
)

@Serializable
data class NotificationSettingsListDto(
    val items: List<NotificationSettingsItemDto>,
)

@Serializable
data class UpdateNotificationSettingsBody(
    val mode: String,
    @SerialName("chat_ids")
    val chatIds: List<String> = emptyList(),
)

@Serializable
data class WsNotificationDismiss(
    @SerialName("chat_id")
    val chatId: String,
    @SerialName("message_ids")
    val messageIds: List<String>,
    val reason: String, // "read" | "deleted" | "manual"
)