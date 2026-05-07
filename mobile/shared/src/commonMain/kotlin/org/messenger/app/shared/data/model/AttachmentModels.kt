package org.messenger.app.shared.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AttachmentDto(
    val id: String,
    @SerialName("original_filename")
    val originalFilename: String,
    @SerialName("mime_type")
    val mimeType: String,
    @SerialName("size_bytes")
    val sizeBytes: Long,
    @SerialName("file_kind")
    val fileKind: String, // image/video/audio/file
    val width: Int? = null,
    val height: Int? = null,
    @SerialName("duration_ms")
    val durationMs: Int? = null,
    @SerialName("has_thumbnail")
    val hasThumbnail: Boolean = false,
    val status: String,
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("download_url")
    val downloadUrl: String? = null,
    @SerialName("thumbnail_url")
    val thumbnailUrl: String? = null,
)

@Serializable
data class PresignUploadBody(
    val filename: String,
    @SerialName("mime_type")
    val mimeType: String,
    @SerialName("size_bytes")
    val sizeBytes: Long,
    @SerialName("chat_id")
    val chatId: String? = null,
)

@Serializable
data class PresignUploadResponse(
    @SerialName("attachment_id")
    val attachmentId: String,
    @SerialName("upload_url")
    val uploadUrl: String,
    @SerialName("storage_key")
    val storageKey: String,
    @SerialName("expires_in")
    val expiresIn: Int,
)

@Serializable
data class DownloadUrlResponse(
    @SerialName("download_url")
    val downloadUrl: String,
    @SerialName("thumbnail_url")
    val thumbnailUrl: String? = null,
    @SerialName("expires_in")
    val expiresIn: Int,
)