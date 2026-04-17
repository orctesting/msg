package org.messenger.app.shared.domain.repository

import org.messenger.app.shared.data.model.ChatDto
import org.messenger.app.shared.data.model.MessageDto
import org.messenger.app.shared.data.model.MessagePage
import org.messenger.app.shared.data.remote.ApiService
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class ChatRepository(private val api: ApiService) {

    suspend fun getChats(): List<ChatDto> = api.getChats()

    suspend fun getChat(chatId: String): ChatDto = api.getChat(chatId)

    suspend fun getMessages(chatId: String, before: String? = null, limit: Int = 50): MessagePage =
        api.getMessages(chatId, before, limit)

    @OptIn(ExperimentalUuidApi::class)
    suspend fun sendMessage(
        chatId: String,
        content: String,
        replyToMessageId: String? = null,
        forwardedFromMessageId: String? = null,
    ): MessageDto =
        api.sendMessage(
            chatId = chatId,
            content = content,
            idempotencyKey = Uuid.random().toString(),
            replyToMessageId = replyToMessageId,
            forwardedFromMessageId = forwardedFromMessageId,
        )

    suspend fun editMessage(chatId: String, messageId: String, content: String): MessageDto =
        api.editMessage(chatId, messageId, content)

    suspend fun deleteMessage(chatId: String, messageId: String) =
        api.deleteMessage(chatId, messageId)

    suspend fun bulkDeleteMessages(chatId: String, messageIds: List<String>) =
        api.bulkDeleteMessages(chatId, messageIds)

    @OptIn(ExperimentalUuidApi::class)
    suspend fun forwardMessage(
        sourceChatId: String,
        messageId: String,
        targetChatId: String,
    ): MessageDto =
        api.forwardMessage(
            sourceChatId = sourceChatId,
            messageId = messageId,
            targetChatId = targetChatId,
            idempotencyKey = Uuid.random().toString(),
        )

    suspend fun pinMessage(chatId: String, messageId: String) =
        api.pinMessage(chatId, messageId)

    suspend fun unpinMessage(chatId: String, scope: String = "local") =
        api.unpinMessage(chatId, scope)

    suspend fun markRead(chatId: String, lastReadMessageId: String) =
        api.markRead(chatId, lastReadMessageId)
}