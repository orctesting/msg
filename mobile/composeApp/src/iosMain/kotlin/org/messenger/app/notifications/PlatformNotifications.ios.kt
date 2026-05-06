package org.messenger.app.notifications

import com.russhwolf.settings.Settings

private const val TOAST_PLACEMENT_KEY = "ios_toast_placement"

actual fun reloadDesktopNotificationSettings() {
    // На iOS push-уведомления показывает APNs/UNUserNotificationCenter автоматически —
    // ничего перечитывать не нужно. No-op.
}

internal actual fun loadInitialPlacement(): String {
    return try {
        Settings().getStringOrNull(TOAST_PLACEMENT_KEY) ?: "system_overlay"
    } catch (_: Exception) {
        "system_overlay"
    }
}

internal actual fun savePlacement(value: String) {
    try {
        Settings().putString(TOAST_PLACEMENT_KEY, value)
    } catch (_: Exception) {}
}