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
    @SerialName("sender_name")
    val senderName: String? = null,
    @SerialName("sender_role")
    val senderRole: String? = null,
    val content: String,
    @SerialName("message_type")
    val messageType: String = "text",
    @SerialName("created_at")
    val createdAt: String = ""
)

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
    val hasMore: Boolean,
    @SerialName("read_by_others_up_to")
    val readByOthersUpTo: String? = null
)

@Serializable
data class MarkReadBody(
    @SerialName("last_read_message_id")
    val lastReadMessageId: String
)