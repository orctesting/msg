package org.messenger.app.shared.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ReplyPreviewDto(
    val id: String,
    @SerialName("sender_id")
    val senderId: String? = null,
    @SerialName("sender_name")
    val senderName: String? = null,
    val content: String,
    @SerialName("message_type")
    val messageType: String = "text",
    @SerialName("is_deleted")
    val isDeleted: Boolean = false
)

@Serializable
data class ForwardedInfoDto(
    @SerialName("original_message_id")
    val originalMessageId: String? = null,
    @SerialName("sender_name")
    val senderName: String? = null,
    @SerialName("is_deleted")
    val isDeleted: Boolean = false
)

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
    val createdAt: String = "",
    @SerialName("edited_at")
    val editedAt: String? = null,
    @SerialName("reply_to")
    val replyTo: ReplyPreviewDto? = null,
    @SerialName("forwarded_from")
    val forwardedFrom: ForwardedInfoDto? = null
)

@Serializable
data class SendMessageBody(
    val content: String,
    @SerialName("message_type")
    val messageType: String = "text",
    @SerialName("idempotency_key")
    val idempotencyKey: String? = null,
    @SerialName("reply_to_message_id")
    val replyToMessageId: String? = null,
    @SerialName("forwarded_from_message_id")
    val forwardedFromMessageId: String? = null
)

@Serializable
data class EditMessageBody(
    val content: String
)

@Serializable
data class BulkDeleteBody(
    @SerialName("message_ids")
    val messageIds: List<String>
)

@Serializable
data class ForwardMessageBody(
    @SerialName("source_chat_id")
    val sourceChatId: String,
    @SerialName("message_id")
    val messageId: String,
    @SerialName("target_chat_id")
    val targetChatId: String,
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