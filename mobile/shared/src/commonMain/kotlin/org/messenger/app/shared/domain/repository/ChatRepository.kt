package org.messenger.app.shared.domain.repository

import org.messenger.app.shared.data.model.ChatDto
import org.messenger.app.shared.data.model.MessageDto
import org.messenger.app.shared.data.model.MessagePage
import org.messenger.app.shared.data.remote.ApiService
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class ChatRepository(private val api: ApiService) {

    suspend fun getChats(): List<ChatDto> =
        api.getChats()

    suspend fun getChat(chatId: String): ChatDto =
        api.getChat(chatId)

    suspend fun getMessages(chatId: String, before: String? = null, limit: Int = 50): MessagePage =
        api.getMessages(chatId, before, limit)

    @OptIn(ExperimentalUuidApi::class)
    suspend fun sendMessage(chatId: String, content: String): MessageDto =
        api.sendMessage(chatId, content, Uuid.random().toString())

    suspend fun markRead(chatId: String, lastReadMessageId: String) =
        api.markRead(chatId, lastReadMessageId)
}