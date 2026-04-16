package org.messenger.app.shared.data.remote

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.math.pow
import org.messenger.app.shared.data.local.TokenStorage
import org.messenger.app.shared.data.model.RefreshTokenBody
import org.messenger.app.shared.data.model.RefreshTokenResponse
import org.messenger.app.shared.data.model.WsEvent

class WsService(
    private val client: HttpClient,
    private val tokenStorage: TokenStorage,
    private val wsBaseUrl: String
) {
    private val _events = MutableSharedFlow<WsEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<WsEvent> = _events.asSharedFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private var job: Job? = null
    private var reconnectAttempt = 0

    companion object {
        private const val INITIAL_DELAY_MS = 1000L
        private const val MAX_DELAY_MS = 30000L
        private const val MULTIPLIER = 2.0
        private const val PING_INTERVAL_MS = 30000L
    }

    fun connect(scope: CoroutineScope) {
        disconnect()
        job = scope.launch {
            while (isActive) {
                try {
                    openSession()
                    reconnectAttempt = 0
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    _connected.value = false
                }
                val delayMs = calculateReconnectDelay()
                reconnectAttempt++
                delay(delayMs)
            }
        }
    }

    fun disconnect() {
        job?.cancel()
        job = null
        reconnectAttempt = 0
        _connected.value = false
    }

    private fun calculateReconnectDelay(): Long {
        val delay = (INITIAL_DELAY_MS * MULTIPLIER.pow(reconnectAttempt)).toLong()
        return delay.coerceAtMost(MAX_DELAY_MS)
    }

    /**
     * Ensures we have a valid access token before connecting WS.
     * If current token is missing, tries to refresh.
     * Returns a valid token or null if impossible.
     */
    private suspend fun getValidToken(): String? {
        val current = tokenStorage.getAccessToken()
        if (current != null) return current

        // Token is null — try refresh
        return tryRefreshToken()
    }

    /**
     * Called when WS connection is rejected (e.g. 403).
     * Refreshes the token so next reconnect attempt uses a fresh one.
     */
    private suspend fun tryRefreshToken(): String? {
        val refresh = tokenStorage.getRefreshToken() ?: return null
        return try {
            val response = client.post("api/v1/auth/refresh") {
                contentType(ContentType.Application.Json)
                setBody(RefreshTokenBody(refresh))
            }
            if (response.status == HttpStatusCode.OK) {
                val tokens: RefreshTokenResponse = response.body()
                tokenStorage.saveTokens(tokens.accessToken, tokens.refreshToken)
                tokens.accessToken
            } else {
                tokenStorage.clear()
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun openSession() {
        val token = getValidToken() ?: run {
            delay(5000)
            return
        }
        val urlString = "${wsBaseUrl}api/v1/ws?token=$token"

        try {
            client.webSocket(urlString) {
                _connected.value = true
                reconnectAttempt = 0

                val pingJob = launch {
                    while (isActive) {
                        delay(PING_INTERVAL_MS)
                        try {
                            send(Frame.Ping(byteArrayOf()))
                        } catch (_: Exception) {
                            break
                        }
                    }
                }

                try {
                    for (frame in incoming) {
                        when (frame) {
                            is Frame.Text -> {
                                val text = frame.readText()
                                try {
                                    val event = appJson.decodeFromString<WsEvent>(text)
                                    _events.emit(event)
                                } catch (_: Exception) {}
                            }
                            is Frame.Pong -> { /* alive */ }
                            else -> {}
                        }
                    }
                } finally {
                    pingJob.cancel()
                    _connected.value = false
                }
            }
        } catch (e: Exception) {
            _connected.value = false
            // If connection was rejected (likely 403 due to expired token),
            // try refreshing so next attempt has a fresh token
            val message = e.message ?: ""
            if (message.contains("403") || message.contains("401")) {
                tryRefreshToken()
            }
            throw e
        }
    }

    suspend fun sendRaw(text: String) {
        // Заглушка для будущего использования
    }
}