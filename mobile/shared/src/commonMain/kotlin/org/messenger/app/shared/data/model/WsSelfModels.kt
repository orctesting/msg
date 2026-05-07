package org.messenger.app.shared.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Событие `new_message_self` — приходит в user-канал при отправке
 * своего сообщения с этого же аккаунта (но с другого устройства).
 * Нужно для синхронизации lastMessage без инкремента unread.
 */
@Serializable
data class WsNewMessageSelf(
    @SerialName("chat_id")
    val chatId: String,
    val message: MessageDto,
)

/**
 * Событие `message_read_self` — другой мой девайс прочитал сообщения.
 * Обнуляем unread на текущем устройстве.
 */
@Serializable
data class WsMessageReadSelf(
    @SerialName("chat_id")
    val chatId: String,
    @SerialName("last_read_message_id")
    val lastReadMessageId: String,
)