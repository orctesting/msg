package org.messenger.app.notifications

import com.russhwolf.settings.Settings

private const val TOAST_PLACEMENT_KEY = "desktop_toast_placement"

actual fun reloadDesktopNotificationSettings() {
    DesktopNotificationManager.reloadSettings()
}

internal actual fun loadInitialPlacement(): String {
    return try {
        val raw = Settings().getStringOrNull(TOAST_PLACEMENT_KEY) ?: return "system_overlay"
        // Поддержка старых значений в формате enum.name
        when (raw) {
            "SYSTEM_OVERLAY", "system_overlay" -> "system_overlay"
            "IN_APP", "in_app" -> "in_app"
            else -> "system_overlay"
        }
    } catch (_: Exception) {
        "system_overlay"
    }
}

internal actual fun savePlacement(value: String) {
    try {
        Settings().putString(TOAST_PLACEMENT_KEY, value)
    } catch (_: Exception) {}
    val placement = if (value == "in_app") ToastPlacement.IN_APP else ToastPlacement.SYSTEM_OVERLAY
    DesktopNotificationManager.setPlacement(placement)
}