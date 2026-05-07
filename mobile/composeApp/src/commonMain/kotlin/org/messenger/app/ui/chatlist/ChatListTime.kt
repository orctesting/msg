package org.messenger.app.ui.chatlist

import org.messenger.app.ui.chat.dayKeyFromIso

/**
 * Форматирует ISO-таймстамп для отображения в списке чатов:
 * - сегодня → HH:MM
 * - текущий год → DD.MM
 * - иначе → DD.MM.YY
 */
fun formatChatListTime(iso: String): String {
    return try {
        if (iso.length < 10) return ""
        val day = dayKeyFromIso(iso) // YYYY-MM-DD
        val parts = day.split("-")
        if (parts.size != 3) return ""
        val y = parts[0].toInt()
        val m = parts[1].toInt()
        val d = parts[2].toInt()

        val nowMs = currentChatListTimeMillis()
        val today = millisToDayKey(nowMs)
        val todayParts = today.split("-").map { it.toInt() }
        val isToday = y == todayParts[0] && m == todayParts[1] && d == todayParts[2]

        if (isToday) {
            // HH:MM из ISO
            val timePart = if (iso.contains("T")) {
                iso.substringAfter("T").substringBefore(".")
                    .substringBefore("+").substringBefore("Z")
            } else iso
            timePart.take(5)
        } else if (y == todayParts[0]) {
            "${d.toString().padStart(2, '0')}.${m.toString().padStart(2, '0')}"
        } else {
            val yy = (y % 100).toString().padStart(2, '0')
            "${d.toString().padStart(2, '0')}.${m.toString().padStart(2, '0')}.$yy"
        }
    } catch (_: Exception) {
        ""
    }
}

private fun millisToDayKey(ms: Long): String {
    val daysSinceEpoch = ms / (24L * 60 * 60 * 1000)
    var days = daysSinceEpoch + 719468
    val era = if (days >= 0) days / 146097 else (days - 146096) / 146097
    val doe = (days - era * 146097).toInt()
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
    val y = yoe + (era * 400).toInt()
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val d = doy - (153 * mp + 2) / 5 + 1
    val m = if (mp < 10) mp + 3 else mp - 9
    val year = if (m <= 2) y + 1 else y
    return "${year.toString().padStart(4, '0')}-${m.toString().padStart(2, '0')}-${d.toString().padStart(2, '0')}"
}

internal expect fun currentChatListTimeMillis(): Long