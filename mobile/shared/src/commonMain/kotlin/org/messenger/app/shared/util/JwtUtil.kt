package org.messenger.app.shared.util

import io.ktor.util.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object JwtUtil {
    private val json = Json { ignoreUnknownKeys = true }

    fun extractRole(token: String): String? {
        return try {
            val parts = token.split(".")
            if (parts.size < 2) return null
            val payloadBase64 = parts[1]
                .replace('-', '+')
                .replace('_', '/')
                .let {
                    // padding
                    val mod = it.length % 4
                    if (mod == 0) it else it + "=".repeat(4 - mod)
                }
            val decoded = payloadBase64.decodeBase64String()
            val obj = json.parseToJsonElement(decoded).jsonObject
            obj["role"]?.jsonPrimitive?.content
        } catch (_: Exception) {
            null
        }
    }
}