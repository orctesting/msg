package org.messenger.app.shared.domain.repository

import org.messenger.app.shared.data.local.TokenStorage
import org.messenger.app.shared.data.model.DeviceInfo
import org.messenger.app.shared.data.model.OtpResponse
import org.messenger.app.shared.data.model.OtpVerifyBody
import org.messenger.app.shared.data.model.OtpVerifyResponse
import org.messenger.app.shared.data.remote.ApiService

class AuthRepository(
    private val api: ApiService,
    private val tokenStorage: TokenStorage
) {
    suspend fun requestOtp(phone: String): OtpResponse =
        api.requestOtp(phone)

    suspend fun verifyOtp(phone: String, code: String, deviceInfo: DeviceInfo): OtpVerifyResponse {
        val body = OtpVerifyBody(
            phone = phone,
            code = code,
            deviceId = deviceInfo.deviceId,
            platform = deviceInfo.platform,
            appVersion = deviceInfo.appVersion,
            osVersion = deviceInfo.osVersion
        )
        val response = api.verifyOtp(body)
        tokenStorage.saveTokens(response.accessToken, response.refreshToken)
        tokenStorage.saveUser(response.userId, phone, "")
        return response
    }

    suspend fun logout() {
        try {
            val refresh = tokenStorage.getRefreshToken()
            if (refresh != null) {
                api.logout(refresh)
            }
        } finally {
            tokenStorage.clear()
        }
    }

    fun isLoggedIn(): Boolean = tokenStorage.isLoggedIn()
    fun getUserId(): String? = tokenStorage.getUserId()
    fun getUserPhone(): String? = tokenStorage.getUserPhone()
    fun getUserDisplayName(): String? = tokenStorage.getUserDisplayName()
}