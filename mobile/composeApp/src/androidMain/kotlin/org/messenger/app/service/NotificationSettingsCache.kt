package org.messenger.app.service

import android.content.Context

/**
 * Локальный кэш notification_settings для платформы android.
 * Используется в FCM-сервисе для фильтрации показа уведомлений.
 * Default (нет записи) = ALL.
 */
object NotificationSettingsCache {
    private const val PREFS = "notification_settings_cache"
    private const val KEY_MODE = "mode"
    private const val KEY_WHITELIST = "whitelist"

    fun saveSettings(context: Context, mode: String, whitelistChatIds: List<String>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODE, mode)
            .putStringSet(KEY_WHITELIST, whitelistChatIds.toSet())
            .apply()
    }

    fun getMode(context: Context): String {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_MODE, "all") ?: "all"
    }

    fun getWhitelist(context: Context): Set<String> {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY_WHITELIST, emptySet()) ?: emptySet()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().clear().apply()
    }
}