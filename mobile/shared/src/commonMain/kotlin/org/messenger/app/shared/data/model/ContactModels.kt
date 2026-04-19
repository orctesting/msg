package org.messenger.app.shared.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ContactDto(
    val id: String,
    val phone: String,
    @SerialName("display_name")
    val displayName: String,
    @SerialName("contact_user_id")
    val contactUserId: String? = null,
    @SerialName("is_registered")
    val isRegistered: Boolean = false,
    @SerialName("created_at")
    val createdAt: String = "",
    @SerialName("updated_at")
    val updatedAt: String = ""
)

@Serializable
data class ContactListResponse(
    val contacts: List<ContactDto>
)

@Serializable
data class CreateContactBody(
    val phone: String,
    @SerialName("display_name")
    val displayName: String
)

@Serializable
data class UpdateContactBody(
    @SerialName("display_name")
    val displayName: String? = null,
    val phone: String? = null
)

@Serializable
data class CreatePersonalChatBody(
    @SerialName("contact_id")
    val contactId: String? = null,
    val phone: String? = null
)

@Serializable
data class CreateGroupChatBody(
    val name: String,
    val type: String = "group",
    @SerialName("member_ids")
    val memberIds: List<String>
)

@Serializable
data class DismissPeerBody(
    @SerialName("peer_user_id")
    val peerUserId: String
)