package org.messenger.app.shared.data.remote

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.auth.*
import io.ktor.client.plugins.auth.providers.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.messenger.app.shared.data.local.TokenStorage
import org.messenger.app.shared.data.model.RefreshTokenBody
import org.messenger.app.shared.data.model.RefreshTokenResponse

val appJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    isLenient = true
    coerceInputValues = true
}

fun createHttpClient(
    tokenStorage: TokenStorage,
    baseUrl: String
): HttpClient = HttpClient(createPlatformEngine()) {
    defaultRequest {
        url(baseUrl)
        contentType(ContentType.Application.Json)
    }

    install(ContentNegotiation) {
        json(appJson)
    }

    install(Logging) {
        logger = Logger.DEFAULT
        level = LogLevel.BODY
    }

    install(WebSockets)

    install(Auth) {
        bearer {
            loadTokens {
                val access = tokenStorage.getAccessToken()
                val refresh = tokenStorage.getRefreshToken()
                if (access != null && refresh != null) {
                    BearerTokens(access, refresh)
                } else null
            }

            refreshTokens {
                val refresh = tokenStorage.getRefreshToken() ?: return@refreshTokens null
                val response = client.post("api/v1/auth/refresh") {
                    markAsRefreshTokenRequest()
                    setBody(RefreshTokenBody(refresh))
                }
                if (response.status == HttpStatusCode.OK) {
                    val tokens: RefreshTokenResponse = response.body()
                    tokenStorage.saveTokens(tokens.accessToken, tokens.refreshToken)
                    BearerTokens(tokens.accessToken, tokens.refreshToken)
                } else {
                    tokenStorage.clear()
                    null
                }
            }

            sendWithoutRequest { request ->
                !request.url.encodedPath.contains("auth")
            }
        }
    }
}