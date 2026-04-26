package org.messenger.app.shared.domain.repository

import org.messenger.app.shared.data.model.AvatarDto
import org.messenger.app.shared.data.model.AvatarListResponse
import org.messenger.app.shared.data.model.CreateAvatarBody
import org.messenger.app.shared.data.model.MeDto
import org.messenger.app.shared.data.model.UpdateMeBody
import org.messenger.app.shared.data.remote.ApiService

class ProfileRepository(private val api: ApiService) {

    suspend fun getMe(): MeDto = api.getMe()

    suspend fun updateMe(
        username: String? = null,
        displayName: String? = null,
        firstName: String? = null,
        lastName: String? = null,
        birthDate: String? = null,
        bio: String? = null,
        email: String? = null,
    ): MeDto = api.updateMe(
        UpdateMeBody(
            username = username,
            displayName = displayName,
            firstName = firstName,
            lastName = lastName,
            birthDate = birthDate,
            bio = bio,
            email = email,
        )
    )

    suspend fun listAvatars(): AvatarListResponse = api.listMyAvatars()

    suspend fun createAvatar(
        sourceAttachmentId: String,
        cropX: Int,
        cropY: Int,
        cropSize: Int,
    ): AvatarDto = api.createAvatar(
        CreateAvatarBody(
            sourceAttachmentId = sourceAttachmentId,
            cropX = cropX,
            cropY = cropY,
            cropSize = cropSize,
        )
    )

    suspend fun setPrimaryAvatar(avatarId: String): MeDto =
        api.setPrimaryAvatar(avatarId)

    suspend fun deleteAvatar(avatarId: String) =
        api.deleteAvatar(avatarId)

    suspend fun getPublicUser(userId: String): org.messenger.app.shared.data.model.PublicUserDto =
        api.getPublicUser(userId)
}