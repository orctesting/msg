package org.messenger.app.service

import android.content.Context

object NotificationGroupStore {
    private const val PREFS = "notification_groups"

    data class Entry(
        val messageId: String,
        val senderName: String,
        val text: String,
        val timestamp: Long,
    )

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun addMessage(
        context: Context,
        chatId: String,
        chatName: String,
        entry: Entry,
    ) {
        val p = prefs(context)
        val existingIds = p.getStringSet(idsKey(chatId), emptySet())?.toMutableSet() ?: mutableSetOf()
        existingIds.add(entry.messageId)
        p.edit()
            .putStringSet(idsKey(chatId), existingIds)
            .putString(chatNameKey(chatId), chatName)
            .putString(entryKey(chatId, entry.messageId),
                "${entry.senderName}\u0001${entry.text}\u0001${entry.timestamp}")
            .apply()
    }

    fun getMessageIds(context: Context, chatId: String): List<String> {
        val ids = prefs(context).getStringSet(idsKey(chatId), emptySet()) ?: return emptyList()
        return ids.toList()
    }

    fun getEntries(context: Context, chatId: String): List<Entry> {
        val p = prefs(context)
        val ids = p.getStringSet(idsKey(chatId), emptySet()) ?: return emptyList()
        return ids.mapNotNull { mid ->
            val raw = p.getString(entryKey(chatId, mid), null) ?: return@mapNotNull null
            val parts = raw.split('\u0001')
            if (parts.size < 3) return@mapNotNull null
            Entry(
                messageId = mid,
                senderName = parts[0],
                text = parts[1],
                timestamp = parts[2].toLongOrNull() ?: 0L,
            )
        }.sortedBy { it.timestamp }
    }

    fun getChatName(context: Context, chatId: String): String {
        return prefs(context).getString(chatNameKey(chatId), "") ?: ""
    }

    fun removeMessageIds(context: Context, chatId: String, messageIds: List<String>) {
        val p = prefs(context)
        val existing = p.getStringSet(idsKey(chatId), emptySet())?.toMutableSet() ?: return
        val ed = p.edit()
        messageIds.forEach { mid ->
            existing.remove(mid)
            ed.remove(entryKey(chatId, mid))
        }
        if (existing.isEmpty()) {
            ed.remove(idsKey(chatId)).remove(chatNameKey(chatId))
        } else {
            ed.putStringSet(idsKey(chatId), existing)
        }
        ed.apply()
    }

    fun clearChat(context: Context, chatId: String) {
        val p = prefs(context)
        val ids = p.getStringSet(idsKey(chatId), emptySet()) ?: emptySet()
        val ed = p.edit()
        ids.forEach { mid -> ed.remove(entryKey(chatId, mid)) }
        ed.remove(idsKey(chatId)).remove(chatNameKey(chatId)).apply()
    }

    private fun idsKey(chatId: String) = "ids:$chatId"
    private fun chatNameKey(chatId: String) = "chat_name:$chatId"
    private fun entryKey(chatId: String, messageId: String) = "entry:$chatId:$messageId"
}