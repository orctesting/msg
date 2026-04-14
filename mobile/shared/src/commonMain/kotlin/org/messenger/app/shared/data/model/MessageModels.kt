package org.messenger.app.shared.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MessageDto(
    val id: String,
    @SerialName("chat_id")
    val chatId: String,
    @SerialName("sender_id")
    val senderId: String? = null,
    val content: String,
    @SerialName("message_type")
    val messageType: String = "text",
    @SerialName("created_at")
    val createdAt: String = ""
) {
    // Для совместимости с UI
    val senderName: String? get() = null
}

@Serializable
data class SendMessageBody(
    val content: String,
    @SerialName("message_type")
    val messageType: String = "text",
    @SerialName("idempotency_key")
    val idempotencyKey: String? = null
)

@Serializable
data class MessagePage(
    val messages: List<MessageDto>,
    @SerialName("has_more")
    val hasMore: Boolean
)

@Serializable
data class MarkReadBody(
    @SerialName("last_read_message_id")
    val lastReadMessageId: String
)