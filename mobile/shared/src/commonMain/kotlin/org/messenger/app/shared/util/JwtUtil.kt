package org.messenger.app.shared.util

import io.ktor.util.*
import io.ktor.util.date.getTimeMillis
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull


object JwtUtil {
    private val json = Json { ignoreUnknownKeys = true }

    private fun payload(token: String): JsonObject? {
        return try {
            val parts = token.split(".")
            if (parts.size < 2) return null

            val payloadBase64 = parts[1]
                .replace('-', '+')
                .replace('_', '/')
                .let {
                    val mod = it.length % 4
                    if (mod == 0) it else it + "=".repeat(4 - mod)
                }

            val decoded = payloadBase64.decodeBase64String()
            json.parseToJsonElement(decoded).jsonObject
        } catch (_: Exception) {
            null
        }
    }

    fun extractRole(token: String): String? {
        return try {
            payload(token)
                ?.get("role")
                ?.jsonPrimitive
                ?.content
        } catch (_: Exception) {
            null
        }
    }

    fun extractExpSeconds(token: String): Long? {
        return try {
            payload(token)
                ?.get("exp")
                ?.jsonPrimitive
                ?.longOrNull
        } catch (_: Exception) {
            null
        }
    }

    @Suppress("DEPRECATION")
    fun isExpiredOrExpiringSoon(token: String, leewaySeconds: Long = 60): Boolean {
        val exp = extractExpSeconds(token) ?: return true
        val now = getTimeMillis() / 1000
        return exp <= now + leewaySeconds
    }
}