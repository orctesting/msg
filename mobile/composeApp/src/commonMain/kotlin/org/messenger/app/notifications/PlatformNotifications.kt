package org.messenger.app.notifications

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Сообщает платформенному менеджеру уведомлений, что настройки изменились.
 * На Desktop — перечитывает с сервера. На Android/iOS — синхронизирует локальный кэш.
 */
expect fun reloadDesktopNotificationSettings()

/**
 * Мост common↔jvm для placement тостов на desktop.
 * На Android/iOS бридж тоже есть, но там значения никогда не используются (placement только для desktop).
 */
object DesktopPlacementBridge {
    private val _placement = MutableStateFlow(loadInitialPlacement())
    val placement: StateFlow<String> = _placement.asStateFlow()

    fun set(value: String) {
        _placement.value = value
        savePlacement(value)
    }

    /** Вызывается из jvm-стороны при изменении placement через DesktopNotificationManager. */
    fun syncFromPlatform(value: String) {
        if (_placement.value != value) {
            _placement.value = value
        }
    }
}

internal expect fun loadInitialPlacement(): String
internal expect fun savePlacement(value: String)