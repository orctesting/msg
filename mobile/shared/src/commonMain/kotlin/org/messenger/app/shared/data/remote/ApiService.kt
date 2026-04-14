package org.messenger.app.shared.data.remote

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import org.messenger.app.shared.data.model.*

class ApiException(val statusCode: Int, val errorBody: String) :
    Exception("HTTP $statusCode: $errorBody")

class ApiService(private val client: HttpClient) {

    private suspend inline fun <reified T> requestAndParse(
        block: () -> HttpResponse
    ): T {
        val response = block()
        if (!response.status.isSuccess()) {
            val body = response.bodyAsText()
            throw ApiException(response.status.value, body)
        }
        return response.body()
    }

    // ── Auth ──

    suspend fun requestOtp(phone: String): OtpResponse =
        requestAndParse {
            client.post("api/v1/auth/request-otp") {
                setBody(OtpRequestBody(phone))
            }
        }

    suspend fun verifyOtp(body: OtpVerifyBody): OtpVerifyResponse =
        requestAndParse {
            client.post("api/v1/auth/verify-otp") {
                setBody(body)
            }
        }

    suspend fun refreshToken(refreshToken: String): RefreshTokenResponse =
        requestAndParse {
            client.post("api/v1/auth/refresh") {
                setBody(RefreshTokenBody(refreshToken))
            }
        }

    suspend fun logout(refreshToken: String) {
        val response = client.post("api/v1/auth/logout") {
            setBody(RefreshTokenBody(refreshToken))
        }
        if (!response.status.isSuccess()) {
            val body = response.bodyAsText()
            throw ApiException(response.status.value, body)
        }
    }

    // ── Chats ──

    suspend fun getChats(): List<ChatDto> {
        val response: ChatListResponse = requestAndParse {
            client.get("api/v1/chats")
        }
        return response.chats
    }

    suspend fun getChat(chatId: String): ChatDto =
        requestAndParse { client.get("api/v1/chats/$chatId") }

    // ── Messages ──

    suspend fun getMessages(chatId: String, before: String? = null, limit: Int = 50): MessagePage =
        requestAndParse {
            client.get("api/v1/chats/$chatId/messages") {
                parameter("limit", limit)
                before?.let { parameter("before", it) }
            }
        }

    suspend fun sendMessage(chatId: String, content: String, idempotencyKey: String): MessageDto =
        requestAndParse {
            client.post("api/v1/chats/$chatId/messages") {
                setBody(SendMessageBody(content = content, idempotencyKey = idempotencyKey))
            }
        }

    suspend fun markRead(chatId: String, lastReadMessageId: String) {
        val response = client.post("api/v1/chats/$chatId/read") {
            setBody(MarkReadBody(lastReadMessageId))
        }
        if (!response.status.isSuccess()) {
            throw ApiException(response.status.value, response.bodyAsText())
        }
    }

    // ── Push tokens ──

    suspend fun registerPushToken(token: String, type: String = "fcm"): Boolean {
        return try {
            val response = client.post("api/v1/devices/push-token") {
                contentType(ContentType.Application.Json)
                setBody(PushTokenBody(token = token, tokenType = type))
            }
            response.status.value in 200..299
        } catch (_: Exception) {
            false
        }
    }
}