package org.messenger.app.ui.chat

import kotlinx.datetime.toLocalDateTime

/**
 * Парсит ISO дату и возвращает "ключ дня" в формате "YYYY-MM-DD".
 * Простая работа со строкой без зависимостей от datetime-библиотек.
 */
fun dayKeyFromIso(iso: String): String {
    return try {
        if (iso.isBlank()) return iso
        val normalized = run {
            if (iso.endsWith("Z") || iso.endsWith("z")) iso
            else {
                val tIdx = iso.indexOf('T')
                val searchFrom = if (tIdx >= 0) tIdx else 10
                val plus = iso.indexOf('+', startIndex = searchFrom)
                val minus = iso.indexOf('-', startIndex = searchFrom)
                if (plus >= 0 || minus >= 0) iso else "${iso}Z"
            }
        }
        val instant = kotlinx.datetime.Instant.parse(normalized)
        val local = instant.toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
        "${local.year.toString().padStart(4, '0')}-${local.monthNumber.toString().padStart(2, '0')}-${local.dayOfMonth.toString().padStart(2, '0')}"
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
        if (parts.size != 3) return dayKey
        val y = parts[0].toInt()
        val m = parts[1].toInt()
        val d = parts[2].toInt()

        val today = todayKey()
        val todayParts = today.split("-").map { it.toInt() }
        val isToday = y == todayParts[0] && m == todayParts[1] && d == todayParts[2]

        // Вчера: вычитаем 1 день простым способом
        val yesterdayKey = yesterdayKey(todayParts[0], todayParts[1], todayParts[2])
        val isYesterday = dayKey == yesterdayKey

        when {
            isToday -> "Сегодня"
            isYesterday -> "Вчера"
            else -> {
                val monthName = when (m) {
                    1 -> "января"; 2 -> "февраля"; 3 -> "марта"
                    4 -> "апреля"; 5 -> "мая"; 6 -> "июня"
                    7 -> "июля"; 8 -> "августа"; 9 -> "сентября"
                    10 -> "октября"; 11 -> "ноября"; 12 -> "декабря"
                    else -> ""
                }
                if (y == todayParts[0]) "$d $monthName"
                else "$d $monthName $y"
            }
        }
    } catch (_: Exception) {
        dayKey
    }
}

private fun todayKey(): String {
    val nowMs = currentTimeMillis()
    return millisToDayKey(nowMs)
}

private fun yesterdayKey(y: Int, m: Int, d: Int): String {
    val nowMs = currentTimeMillis()
    val yesterdayMs = nowMs - 24L * 60 * 60 * 1000
    return millisToDayKey(yesterdayMs)
}

private fun millisToDayKey(ms: Long): String {
    // Простой алгоритм: дни с эпохи -> Y-M-D (по UTC, для UI достаточно)
    val daysSinceEpoch = ms / (24L * 60 * 60 * 1000)
    return daysToYmd(daysSinceEpoch)
}

private fun daysToYmd(daysSinceEpoch: Long): String {
    // Алгоритм перевода дней с 1970-01-01 в Y-M-D
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

internal expect fun currentTimeMillis(): Long