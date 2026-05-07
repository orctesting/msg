package org.messenger.app.service

import android.content.Context
import android.util.Log
import androidx.core.app.NotificationManagerCompat

object NotificationDismisser {
    private const val TAG = "NotificationDismisser"

    fun dismiss(context: Context, chatId: String, messageIds: List<String>) {
        val nm = NotificationManagerCompat.from(context)
        try {
            val store = NotificationGroupStore
            if (messageIds.isEmpty() || messageIds.contains("all")) {
                // Снимаем все уведомления чата
                val ids = store.getMessageIds(context, chatId)
                ids.forEach { mid ->
                    nm.cancel(mid.hashCode())
                }
                store.clearChat(context, chatId)
                // Summary
                nm.cancel(summaryIdFor(chatId))
            } else {
                messageIds.forEach { mid ->
                    nm.cancel(mid.hashCode())
                }
                store.removeMessageIds(context, chatId, messageIds)
                val remaining = store.getMessageIds(context, chatId)
                if (remaining.isEmpty()) {
                    nm.cancel(summaryIdFor(chatId))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "dismiss error", e)
        }
    }

    fun summaryIdFor(chatId: String): Int {
        // Стабильный отдельный id для summary, отличный от любого messageId.hashCode()
        return ("summary_$chatId").hashCode()
    }
}