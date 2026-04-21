package org.messenger.app.ui.chat

import kotlinx.datetime.*

/**
 * Парсит ISO дату из backend и возвращает "ключ дня" в локальной таймзоне.
 * Формат ключа: "YYYY-MM-DD".
 */
fun dayKeyFromIso(iso: String): String {
    return try {
        val instant = Instant.parse(normalizeIso(iso))
        val ld = instant.toLocalDateTime(TimeZone.currentSystemDefault()).date
        "${ld.year.toString().padStart(4, '0')}-${ld.monthNumber.toString().padStart(2, '0')}-${ld.dayOfMonth.toString().padStart(2, '0')}"
    } catch (_: Exception) {
        iso.take(10)
    }
}

/**
 * Форматирует ключ дня в человекочитаемый вид: "Сегодня" / "Вчера" / "15 апреля 2026".
 */
fun formatDateLabel(dayKey: String): String {
    return try {
        val parts = dayKey.split("-")
        val y = parts[0].toInt()
        val m = parts[1].toInt()
        val d = parts[2].toInt()
        val date = LocalDate(y, m, d)

        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val yesterday = today.minus(1, DateTimeUnit.DAY)

        when (date) {
            today -> "Сегодня"
            yesterday -> "Вчера"
            else -> {
                val monthName = when (date.monthNumber) {
                    1 -> "января"; 2 -> "февраля"; 3 -> "марта"
                    4 -> "апреля"; 5 -> "мая"; 6 -> "июня"
                    7 -> "июля"; 8 -> "августа"; 9 -> "сентября"
                    10 -> "октября"; 11 -> "ноября"; 12 -> "декабря"
                    else -> ""
                }
                if (date.year == today.year) {
                    "${date.dayOfMonth} $monthName"
                } else {
                    "${date.dayOfMonth} $monthName ${date.year}"
                }
            }
        }
    } catch (_: Exception) {
        dayKey
    }
}

/**
 * Добавляет суффикс 'Z' если нет зоны, чтобы Instant.parse принял.
 */
private fun normalizeIso(iso: String): String {
    if (iso.isEmpty()) return iso
    // Есть уже таймзона?
    val hasZone = iso.endsWith("Z") ||
            iso.contains("+") ||
            iso.substringAfter("T", "").contains("-")
    return if (hasZone) iso else "${iso}Z"
}