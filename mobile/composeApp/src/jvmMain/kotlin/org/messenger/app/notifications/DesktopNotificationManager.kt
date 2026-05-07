package org.messenger.app.notifications

import com.russhwolf.settings.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.coroutines.cancel
import org.messenger.app.ActiveChatHolder
import org.messenger.app.shared.data.local.TokenStorage
import org.messenger.app.shared.data.model.NotificationMode
import org.messenger.app.shared.data.model.NotificationPlatform
import org.messenger.app.shared.data.model.WsMessageDeleted
import org.messenger.app.shared.data.model.WsNewMessage
import org.messenger.app.shared.data.model.WsNotificationDismiss
import org.messenger.app.shared.data.remote.WsService
import org.messenger.app.shared.data.remote.appJson
import org.messenger.app.shared.domain.repository.ChatRepository
import org.messenger.app.shared.domain.repository.NotificationsRepository

private const val TOAST_PLACEMENT_KEY = "desktop_toast_placement"

enum class ToastPlacement { IN_APP, SYSTEM_OVERLAY }

data class DesktopToast(
    val id: Long,
    val chatId: String,
    val chatName: String,
    val senderName: String,
    val text: String,
    val messageId: String,
)

object DesktopNotificationManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var idCounter = 0L

    private val mode: MutableStateFlow<String> = MutableStateFlow(NotificationMode.ALL)
    private val whitelist: MutableStateFlow<Set<String>> = MutableStateFlow(emptySet())

    private val _toasts = MutableStateFlow<List<DesktopToast>>(emptyList())
    val toasts: StateFlow<List<DesktopToast>> = _toasts.asStateFlow()

    private val _placement = MutableStateFlow(loadPlacement())
    val placement: StateFlow<ToastPlacement> = _placement.asStateFlow()

    private var currentUserId: String? = null
    private var notificationsRepoRef: NotificationsRepository? = null
    private var wsScope: CoroutineScope? = null

    private fun loadPlacement(): ToastPlacement {
        return try {
            val raw = Settings().getStringOrNull(TOAST_PLACEMENT_KEY) ?: return ToastPlacement.SYSTEM_OVERLAY
            when (raw) {
                "in_app", "IN_APP" -> ToastPlacement.IN_APP
                "system_overlay", "SYSTEM_OVERLAY" -> ToastPlacement.SYSTEM_OVERLAY
                else -> ToastPlacement.SYSTEM_OVERLAY
            }
        } catch (_: Exception) {
            ToastPlacement.SYSTEM_OVERLAY
        }
    }

    fun setPlacement(p: ToastPlacement) {
        _placement.value = p
        val str = when (p) {
            ToastPlacement.IN_APP -> "in_app"
            ToastPlacement.SYSTEM_OVERLAY -> "system_overlay"
        }
        try { Settings().putString(TOAST_PLACEMENT_KEY, str) } catch (_: Exception) {}
    }

    /**
     * Перезапускается при каждом вызове: отменяет старую WS-подписку,
     * перечитывает настройки и запускает заново. Это нужно потому что
     * WsService после смены AppModule может быть другим инстансом, и
     * настройки могут измениться (из-за логина/смены пользователя).
     */
    fun start(
        wsService: WsService,
        notificationsRepository: NotificationsRepository,
        chatRepository: ChatRepository,
        tokenStorage: TokenStorage,
    ) {
        wsScope?.cancel()
        wsScope = null

        currentUserId = tokenStorage.getUserId()
        notificationsRepoRef = notificationsRepository

        // Перечитываем настройки уведомлений с сервера
        scope.launch {
            try {
                val list = notificationsRepository.getSettings()
                val item = list.items.firstOrNull { it.platform == NotificationPlatform.DESKTOP }
                if (item != null) {
                    mode.value = item.mode
                    whitelist.value = item.whitelistChatIds.toSet()
                } else {
                    mode.value = NotificationMode.ALL
                    whitelist.value = emptySet()
                }
            } catch (_: Exception) {}
        }

        // Подписка на WS
        val newScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        wsScope = newScope
        newScope.launch {
            wsService.events.collect { event ->
                when (event.type) {
                    "new_message" -> handleNewMessage(event.data, chatRepository)
                    "notification_dismiss" -> handleDismiss(event.data)
                    "message_deleted" -> handleDeleted(event.data)
                }
            }
        }
    }

    /** Принудительно перечитать настройки уведомлений с сервера. */
    fun reloadSettings() {
        val repo = notificationsRepoRef ?: return
        scope.launch {
            try {
                val list = repo.getSettings()
                val item = list.items.firstOrNull { it.platform == NotificationPlatform.DESKTOP }
                if (item != null) {
                    mode.value = item.mode
                    whitelist.value = item.whitelistChatIds.toSet()
                } else {
                    mode.value = NotificationMode.ALL
                    whitelist.value = emptySet()
                }
            } catch (_: Exception) {}
        }
    }

    fun dismissToast(id: Long) {
        _toasts.update { list -> list.filterNot { it.id == id } }
    }

    private suspend fun handleNewMessage(data: JsonElement, chatRepository: ChatRepository) {
        val payload = try {
            appJson.decodeFromJsonElement<WsNewMessage>(data)
        } catch (_: Exception) {
            return
        }

        // Не своё сообщение
        val senderId = payload.message.senderId
        if (senderId != null && senderId == currentUserId) return

        // Чат уже открыт
        if (ActiveChatHolder.get() == payload.chatId) return

        // Фильтр по mode
        when (mode.value) {
            NotificationMode.NONE -> return
            NotificationMode.WHITELIST -> {
                if (!whitelist.value.contains(payload.chatId)) return
            }
            NotificationMode.PERSONAL_ONLY -> {
                val chatType = try {
                    chatRepository.getChat(payload.chatId).type
                } catch (_: Exception) { null }
                if (chatType != "personal") return
            }
            // ALL — пропускаем
        }

        val chatName = try {
            chatRepository.getChat(payload.chatId).name ?: "Чат"
        } catch (_: Exception) { "Чат" }

        val senderName = payload.message.senderName ?: "Пользователь"
        val text = payload.message.content.take(200)

        val toast = DesktopToast(
            id = ++idCounter,
            chatId = payload.chatId,
            chatName = chatName,
            senderName = senderName,
            text = text,
            messageId = payload.message.id,
        )
        _toasts.update { list -> (listOf(toast) + list).take(5) }

        scope.launch {
            delay(6000)
            dismissToast(toast.id)
        }
    }

    private fun handleDismiss(data: JsonElement) {
        val payload = try {
            appJson.decodeFromJsonElement<WsNotificationDismiss>(data)
        } catch (_: Exception) { return }
        val ids = payload.messageIds.toSet()
        _toasts.update { list ->
            list.filterNot { it.chatId == payload.chatId && ids.contains(it.messageId) }
        }
    }

    private fun handleDeleted(data: JsonElement) {
        val payload = try {
            appJson.decodeFromJsonElement<WsMessageDeleted>(data)
        } catch (_: Exception) { return }
        val ids = payload.messageIds.toSet()
        _toasts.update { list ->
            list.filterNot { it.chatId == payload.chatId && ids.contains(it.messageId) }
        }
    }
}