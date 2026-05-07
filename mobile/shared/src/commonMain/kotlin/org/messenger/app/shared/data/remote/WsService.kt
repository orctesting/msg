package org.messenger.app.shared.data.remote

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.withLock
import kotlin.math.pow
import org.messenger.app.shared.util.JwtUtil
import org.messenger.app.shared.data.local.TokenStorage
import org.messenger.app.shared.data.model.RefreshTokenBody
import org.messenger.app.shared.data.model.RefreshTokenResponse
import org.messenger.app.shared.data.model.WsEvent

class WsService(
    private val client: HttpClient,
    private val tokenStorage: TokenStorage,
    private val wsBaseUrl: String
) {
    private val _events = MutableSharedFlow<WsEvent>(
        replay = 0,
        extraBufferCapacity = 256,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<WsEvent> = _events.asSharedFlow()

    private val _connected = MutableStateFlow(false)
    enum class WsConnectionStatus { CONNECTED, CONNECTING, DISCONNECTED }

    private val _status = MutableStateFlow(WsConnectionStatus.DISCONNECTED)
    val status: StateFlow<WsConnectionStatus> = _status.asStateFlow()
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
                    _status.value = WsConnectionStatus.CONNECTING
                    openSession()
                    reconnectAttempt = 0
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    _connected.value = false
                }
                val delayMs = calculateReconnectDelay()
                reconnectAttempt++
                _status.value = WsConnectionStatus.CONNECTING
                delay(delayMs)
            }
        }
    }

    fun disconnect() {
        job?.cancel()
        job = null
        reconnectAttempt = 0
        _connected.value = false
        _status.value = WsConnectionStatus.DISCONNECTED
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

        if (current != null && !JwtUtil.isExpiredOrExpiringSoon(current, leewaySeconds = 60)) {
            return current
        }

        return refreshAccessToken(forceNetwork = false)
    }

    private suspend fun refreshAccessToken(forceNetwork: Boolean = false): String? {
        return refreshMutex.withLock {
            val existing = tokenStorage.getAccessToken()

            if (
                !forceNetwork &&
                existing != null &&
                !JwtUtil.isExpiredOrExpiringSoon(existing, leewaySeconds = 60)
            ) {
                return@withLock existing
            }

            val refresh = tokenStorage.getRefreshToken() ?: return@withLock null

            try {
                val response = client.post("api/v1/auth/refresh") {
                    attributes.put(io.ktor.client.plugins.auth.AuthCircuitBreaker, Unit)
                    contentType(ContentType.Application.Json)
                    setBody(RefreshTokenBody(refresh))
                }

                if (response.status == HttpStatusCode.OK) {
                    val tokens: RefreshTokenResponse = response.body()
                    tokenStorage.saveTokens(tokens.accessToken, tokens.refreshToken)
                    tokens.accessToken
                } else {
                    null
                }
            } catch (_: Exception) {
                null
            }
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
                _status.value = WsConnectionStatus.CONNECTED
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
                    _status.value = WsConnectionStatus.CONNECTING
                }
            }
        } catch (e: Exception) {
            _connected.value = false

            val message = e.message ?: ""

            if (
                message.contains("403") ||
                message.contains("401") ||
                message.contains("4001") ||
                message.contains("Unauthorized", ignoreCase = true) ||
                message.contains("Forbidden", ignoreCase = true)
            ) {
                refreshAccessToken(forceNetwork = true)
            }

            throw e
        }
    }

    suspend fun sendRaw(text: String) {
        // Заглушка для будущего использования
    }
}