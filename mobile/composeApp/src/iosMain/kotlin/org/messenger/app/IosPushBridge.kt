package org.messenger.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.messenger.app.shared.di.AppModule

/**
 * Мост между Swift (AppDelegate) и Kotlin.
 * Обращение из Swift: `IosPushBridge.shared.onApnsToken(tokenHex:)` и
 * `IosPushBridge.shared.onOpenChat(chatId:)`.
 */
class IosPushBridge {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Вызывается когда APNs выдал device token (hex-строка). */
    fun onApnsToken(tokenHex: String) {
        IosAppHolder.lastApnsToken = tokenHex
        val module = IosAppHolder.appModule ?: return
        if (!module.tokenStorage.isLoggedIn()) return
        scope.launch(Dispatchers.Default) {
            try {
                module.apiService.registerPushToken(token = tokenHex, type = "apns")
            } catch (_: Exception) {}
        }
    }

    /**
     * Вызывается при тапе по push-уведомлению.
     * Swift передаёт chatId как Int64 — приводим к строке (бэк хранит UUID,
     * но контракт между AppDelegate.swift и нами фиксирован: пробрасываем как есть).
     */
    fun onOpenChat(chatId: Long) {
        // Swift передаёт numeric chatId; на сервере id — UUID-строка.
        // На самом деле AppDelegate должен передавать строку. Перепишем ниже.
        val asString = chatId.toString()
        IosDeepLinkBus.emit(asString, null)
    }

    /** Перегрузка для случая, когда Swift сможет прислать строковый id. */
    fun onOpenChatString(chatId: String, chatName: String?) {
        IosDeepLinkBus.emit(chatId, chatName)
    }

    companion object {
        val shared = IosPushBridge()
    }
}