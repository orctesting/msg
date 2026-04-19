package org.messenger.app.shared.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatListResponse(
    val chats: List<ChatDto>
)

@Serializable
data class PinnedMessageDto(
    val id: String,
    @SerialName("chat_id")
    val chatId: String,
    @SerialName("sender_id")
    val senderId: String? = null,
    @SerialName("sender_name")
    val senderName: String? = null,
    val content: String,
    @SerialName("message_type")
    val messageType: String = "text",
    @SerialName("created_at")
    val createdAt: String = "",
    @SerialName("pinned_by_user_id")
    val pinnedByUserId: String? = null,
    @SerialName("pinned_at")
    val pinnedAt: String? = null
)

@Serializable
data class PeerUserDto(
    val id: String,
    val phone: String,
    @SerialName("display_name")
    val displayName: String
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
    @SerialName("pinned_message")
    val pinnedMessage: PinnedMessageDto? = null,
    @SerialName("peer_user")
    val peerUser: PeerUserDto? = null,
    @SerialName("peer_is_in_contacts")
    val peerIsInContacts: Boolean? = null,
    @SerialName("peer_dismissed")
    val peerDismissed: Boolean? = null,
    @SerialName("created_at")
    val createdAt: String? = null
)

@Serializable
data class PinMessageBody(
    @SerialName("message_id")
    val messageId: String
)