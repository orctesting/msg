package org.messenger.app.shared.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class WsEvent(
    val type: String,
    val data: JsonElement
)

// Конкретные типы событий для удобного парсинга

@Serializable
data class WsNewMessage(
    @SerialName("chat_id")
    val chatId: String,
    val message: MessageDto
)

@Serializable
data class WsMessageRead(
    @SerialName("chat_id")
    val chatId: String,
    @SerialName("user_id")
    val userId: String,
    @SerialName("last_read_message_id")
    val lastReadMessageId: String
)

@Serializable
data class WsUserOnline(
    @SerialName("user_id")
    val userId: String,
    val online: Boolean
)

@Serializable
data class WsTyping(
    @SerialName("chat_id")
    val chatId: String,
    @SerialName("user_id")
    val userId: String
)