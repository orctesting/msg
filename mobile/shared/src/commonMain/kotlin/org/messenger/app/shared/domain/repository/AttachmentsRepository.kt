package org.messenger.app.shared.domain.repository

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.auth.AuthCircuitBreaker
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import org.messenger.app.shared.data.model.AttachmentDto
import org.messenger.app.shared.data.model.DownloadUrlResponse
import org.messenger.app.shared.data.model.PresignUploadBody
import org.messenger.app.shared.data.remote.ApiService

class AttachmentsRepository(
    private val api: ApiService,
    private val httpClient: HttpClient,
) {
    /**
     * Полный цикл загрузки: presign → PUT в S3 → complete.
     */
    suspend fun uploadFile(
        filename: String,
        mimeType: String,
        data: ByteArray,
        chatId: String?,
    ): AttachmentDto {
        val presign = api.presignUpload(
            PresignUploadBody(
                filename = filename,
                mimeType = mimeType,
                sizeBytes = data.size.toLong(),
                chatId = chatId,
            )
        )
        api.uploadToS3(presign.uploadUrl, data, mimeType)
        return api.completeAttachment(presign.attachmentId)
    }

    suspend fun getAttachment(attachmentId: String): AttachmentDto =
        api.getAttachment(attachmentId)

    suspend fun getDownloadUrl(attachmentId: String): DownloadUrlResponse =
        api.getAttachmentDownloadUrl(attachmentId)

    suspend fun delete(attachmentId: String) =
        api.deleteAttachment(attachmentId)

    /**
     * Загружает байты по presigned URL без Authorization-заголовка.
     */
    suspend fun fetchBytes(url: String): ByteArray? {
        return try {
            val resp = httpClient.get(url) {
                headers.remove(HttpHeaders.Authorization)
                attributes.put(AuthCircuitBreaker, Unit)
            }
            if (resp.status.isSuccess()) resp.body<ByteArray>() else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Загружает байты вложения с авто-обновлением URL при истечении.
     */
    suspend fun loadImageBytes(
        attachmentId: String,
        thumbnailUrl: String?,
        downloadUrl: String?,
        thumb: Boolean,
    ): ByteArray? {
        val initialUrl = if (thumb) thumbnailUrl ?: downloadUrl else downloadUrl
        if (initialUrl != null) {
            val bytes = fetchBytes(initialUrl)
            if (bytes != null) return bytes
        }
        val fresh = try { getDownloadUrl(attachmentId) } catch (_: Exception) { return null }
        val freshUrl = if (thumb) fresh.thumbnailUrl ?: fresh.downloadUrl else fresh.downloadUrl
        return fetchBytes(freshUrl)
    }
}