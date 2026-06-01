package org.messenger.app.ui.chatlist

import org.messenger.app.ui.chat.dayKeyFromIso
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Форматирует ISO-таймстамп для отображения в списке чатов:
 * - сегодня → HH:MM
 * - текущий год → DD.MM
 * - иначе → DD.MM.YY
 */
fun formatChatListTime(iso: String): String {
    return try {
        if (iso.isBlank()) return ""
        val instant = Instant.parse(normalizeIso(iso))
        val tz = TimeZone.currentSystemDefault()
        val local = instant.toLocalDateTime(tz)

        val nowMs = currentChatListTimeMillis()
        val nowInstant = Instant.fromEpochMilliseconds(nowMs)
        val nowLocal = nowInstant.toLocalDateTime(tz)

        val isToday = local.year == nowLocal.year &&
                local.monthNumber == nowLocal.monthNumber &&
                local.dayOfMonth == nowLocal.dayOfMonth

        when {
            isToday -> {
                val h = local.hour.toString().padStart(2, '0')
                val m = local.minute.toString().padStart(2, '0')
                "$h:$m"
            }
            local.year == nowLocal.year -> {
                "${local.dayOfMonth.toString().padStart(2, '0')}.${local.monthNumber.toString().padStart(2, '0')}"
            }
            else -> {
                val yy = (local.year % 100).toString().padStart(2, '0')
                "${local.dayOfMonth.toString().padStart(2, '0')}.${local.monthNumber.toString().padStart(2, '0')}.$yy"
            }
        }
    } catch (_: Exception) {
        ""
    }
}

private fun normalizeIso(iso: String): String {
    if (iso.isBlank()) return iso
    if (iso.endsWith("Z") || iso.endsWith("z")) return iso
    val tIdx = iso.indexOf('T')
    val searchFrom = if (tIdx >= 0) tIdx else 10
    val plus = iso.indexOf('+', startIndex = searchFrom)
    val minus = iso.indexOf('-', startIndex = searchFrom)
    val hasTz = plus >= 0 || minus >= 0
    return if (hasTz) iso else "${iso}Z"
}


internal expect fun currentChatListTimeMillis(): Long