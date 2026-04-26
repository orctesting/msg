package org.messenger.app.shared.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MeDto(
    val id: String,
    val phone: String,
    val username: String,
    @SerialName("display_name")
    val displayName: String,
    @SerialName("first_name")
    val firstName: String? = null,
    @SerialName("last_name")
    val lastName: String? = null,
    @SerialName("birth_date")
    val birthDate: String? = null,
    val bio: String? = null,
    val email: String? = null,
    val role: String,
    @SerialName("primary_avatar_attachment_id")
    val primaryAvatarAttachmentId: String? = null,
    @SerialName("primary_avatar_thumb_attachment_id")
    val primaryAvatarThumbAttachmentId: String? = null,
    @SerialName("primary_avatar_url")
    val primaryAvatarUrl: String? = null,
    @SerialName("primary_avatar_thumb_url")
    val primaryAvatarThumbUrl: String? = null,
    @SerialName("created_at")
    val createdAt: String,
)

@Serializable
data class UpdateMeBody(
    val username: String? = null,
    @SerialName("display_name")
    val displayName: String? = null,
    @SerialName("first_name")
    val firstName: String? = null,
    @SerialName("last_name")
    val lastName: String? = null,
    @SerialName("birth_date")
    val birthDate: String? = null,
    val bio: String? = null,
    val email: String? = null,
)

@Serializable
data class AvatarDto(
    val id: String,
    @SerialName("full_attachment_id")
    val fullAttachmentId: String,
    @SerialName("crop_attachment_id")
    val cropAttachmentId: String,
    @SerialName("full_url")
    val fullUrl: String? = null,
    @SerialName("crop_url")
    val cropUrl: String? = null,
    @SerialName("created_at")
    val createdAt: String,
)

@Serializable
data class AvatarListResponse(
    val avatars: List<AvatarDto>,
    @SerialName("primary_avatar_id")
    val primaryAvatarId: String? = null,
)

@Serializable
data class CreateAvatarBody(
    @SerialName("source_attachment_id")
    val sourceAttachmentId: String,
    @SerialName("crop_x")
    val cropX: Int,
    @SerialName("crop_y")
    val cropY: Int,
    @SerialName("crop_size")
    val cropSize: Int,
)

@Serializable
data class SetPrimaryAvatarBody(
    @SerialName("avatar_id")
    val avatarId: String,
)

@Serializable
data class PublicUserDto(
    val id: String,
    val username: String,
    @SerialName("display_name")
    val displayName: String,
    @SerialName("first_name")
    val firstName: String? = null,
    @SerialName("last_name")
    val lastName: String? = null,
    val bio: String? = null,
    @SerialName("primary_avatar_url")
    val primaryAvatarUrl: String? = null,
    @SerialName("primary_avatar_thumb_url")
    val primaryAvatarThumbUrl: String? = null,
    @SerialName("created_at")
    val createdAt: String,
)