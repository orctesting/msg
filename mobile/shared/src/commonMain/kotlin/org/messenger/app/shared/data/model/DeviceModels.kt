package org.messenger.app.shared.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PushTokenBody(
    val token: String,
    @SerialName("token_type")
    val tokenType: String               // "fcm" | "apns" | "web_push"
)

@Serializable
data class PushTokenResponse(
    val id: String
)