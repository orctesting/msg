package org.messenger.app.shared.data.local

import com.russhwolf.settings.Settings

class TokenStorage(private val settings: Settings = Settings()) {

    companion object {
        private const val KEY_ACCESS = "access_token"
        private const val KEY_REFRESH = "refresh_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_PHONE = "user_phone"
        private const val KEY_USER_NAME = "user_display_name"
        private const val KEY_SERVER_URL = "server_url"
    }

    fun saveTokens(accessToken: String, refreshToken: String) {
        settings.putString(KEY_ACCESS, accessToken)
        settings.putString(KEY_REFRESH, refreshToken)
    }

    fun getToken(): String? = getAccessToken()
    fun getAccessToken(): String? = settings.getStringOrNull(KEY_ACCESS)
    fun getRefreshToken(): String? = settings.getStringOrNull(KEY_REFRESH)

    fun saveUser(id: String, phone: String, displayName: String) {
        settings.putString(KEY_USER_ID, id)
        settings.putString(KEY_USER_PHONE, phone)
        settings.putString(KEY_USER_NAME, displayName)
    }

    fun getUserId(): String? = settings.getStringOrNull(KEY_USER_ID)
    fun getUserPhone(): String? = settings.getStringOrNull(KEY_USER_PHONE)
    fun getUserDisplayName(): String? = settings.getStringOrNull(KEY_USER_NAME)

    fun saveServerUrl(url: String) {
        settings.putString(KEY_SERVER_URL, url)
    }

    fun getServerUrl(): String? = settings.getStringOrNull(KEY_SERVER_URL)

    fun isLoggedIn(): Boolean = getAccessToken() != null

    fun clear() {
        settings.remove(KEY_ACCESS)
        settings.remove(KEY_REFRESH)
        settings.remove(KEY_USER_ID)
        settings.remove(KEY_USER_PHONE)
        settings.remove(KEY_USER_NAME)
        // НЕ удаляем server_url — пусть остаётся после логаута
    }
}