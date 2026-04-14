package org.messenger.app.shared.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatListResponse(
    val chats: List<ChatDto>
)

@Serializable
data class ChatDto(
    val id: String,
    val name: String? = null,
    val type: String = "group",
    @SerialName("unread_count")
    val unreadCount: Int = 0,
    @SerialName("last_message")
    val lastMessage: MessageDto? = null,
    @SerialName("created_at")
    val createdAt: String? = null
)