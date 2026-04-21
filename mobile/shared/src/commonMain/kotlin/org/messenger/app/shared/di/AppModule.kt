package org.messenger.app.shared.di

import org.messenger.app.shared.data.local.TokenStorage
import org.messenger.app.shared.data.remote.ApiService
import org.messenger.app.shared.data.remote.WsService
import org.messenger.app.shared.data.remote.createHttpClient
import org.messenger.app.shared.domain.repository.AuthRepository
import org.messenger.app.shared.domain.repository.ChatRepository

class AppModule(
    baseUrl: String? = null,
    wsBaseUrl: String? = null,
    context: Any? = null
) {
    val tokenStorage by lazy { TokenStorage() }

    // Resolve URLs: explicit param > saved in storage > fallback
    val baseUrl: String by lazy {
        baseUrl ?: run {
            val saved = tokenStorage.getServerUrl()
            if (!saved.isNullOrBlank()) buildBaseUrl(saved) else DEFAULT_BASE_URL
        }
    }

    val wsBaseUrl: String by lazy {
        wsBaseUrl ?: run {
            val saved = tokenStorage.getServerUrl()
            if (!saved.isNullOrBlank()) buildWsUrl(saved) else DEFAULT_WS_URL
        }
    }

    val httpClient by lazy { createHttpClient(tokenStorage, this.baseUrl) }

    val apiService by lazy { ApiService(httpClient) }

    val wsService by lazy { WsService(httpClient, tokenStorage, this.wsBaseUrl) }

    val authRepository by lazy { AuthRepository(apiService, tokenStorage) }

    val chatRepository by lazy { ChatRepository(apiService) }

    val contactsRepository by lazy {
        org.messenger.app.shared.domain.repository.ContactsRepository(apiService)
    }

    companion object {
        private const val DEFAULT_BASE_URL = "http://10.0.2.2:8000/"
        private const val DEFAULT_WS_URL = "ws://10.0.2.2:8000/"

        fun buildBaseUrl(serverAddress: String): String {
            var addr = serverAddress.trim()
            if (!addr.startsWith("http://") && !addr.startsWith("https://")) {
                addr = "http://$addr"
            }
            if (!addr.endsWith("/")) addr += "/"
            return addr
        }

        fun buildWsUrl(serverAddress: String): String {
            val base = buildBaseUrl(serverAddress)
            return base.replaceFirst("http://", "ws://").replaceFirst("https://", "wss://")
        }
    }
}