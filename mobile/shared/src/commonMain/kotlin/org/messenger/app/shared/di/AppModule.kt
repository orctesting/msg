package org.messenger.app.shared.di

import org.messenger.app.shared.data.local.TokenStorage
import org.messenger.app.shared.data.remote.ApiService
import org.messenger.app.shared.data.remote.WsService
import org.messenger.app.shared.data.remote.createHttpClient
import org.messenger.app.shared.domain.repository.AuthRepository
import org.messenger.app.shared.domain.repository.ChatRepository

class AppModule(
    val baseUrl: String = "http://10.0.2.2:8000/",
    val wsBaseUrl: String = "ws://10.0.2.2:8000/",
    context: Any? = null  // Android context, ignored on other platforms
) {
    val tokenStorage by lazy { TokenStorage() }

    val httpClient by lazy { createHttpClient(tokenStorage, baseUrl) }

    val apiService by lazy { ApiService(httpClient) }

    val wsService by lazy { WsService(httpClient, tokenStorage, wsBaseUrl) }

    val authRepository by lazy { AuthRepository(apiService, tokenStorage) }

    val chatRepository by lazy { ChatRepository(apiService) }
}