package org.messenger.app.shared.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OtpRequestBody(
    val phone: String
)

@Serializable
data class OtpResponse(
    val ok: Boolean = true,
    val detail: String = "",
    @SerialName("otp_session_id")
    val otpSessionId: String
)

@Serializable
data class OtpVerifyBody(
    val phone: String,
    val code: String,
    @SerialName("device_id")
    val deviceId: String,
    val platform: String,
    @SerialName("app_version")
    val appVersion: String? = null,
    @SerialName("os_version")
    val osVersion: String? = null
)

@Serializable
data class DeviceInfo(
    @SerialName("device_id")
    val deviceId: String,
    val platform: String,
    @SerialName("app_version")
    val appVersion: String? = null,
    @SerialName("os_version")
    val osVersion: String? = null
)

@Serializable
data class OtpVerifyResponse(
    val ok: Boolean = true,
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("refresh_token")
    val refreshToken: String,
    @SerialName("user_id")
    val userId: String,
    @SerialName("device_id")
    val deviceId: String
)

@Serializable
data class RefreshTokenBody(
    @SerialName("refresh_token")
    val refreshToken: String
)

@Serializable
data class RefreshTokenResponse(
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("refresh_token")
    val refreshToken: String
)

@Serializable
data class UserDto(
    val id: String,
    val phone: String,
    @SerialName("display_name")
    val displayName: String,
    val role: String,
    @SerialName("is_active")
    val isActive: Boolean = true,
    @SerialName("created_at")
    val createdAt: String? = null
)