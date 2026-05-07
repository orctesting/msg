package org.messenger.app.shared.util

actual object ImageCache {
    private val mem = mutableMapOf<String, ByteArray>()
    actual suspend fun get(key: String): ByteArray? = mem[key]
    actual suspend fun put(key: String, bytes: ByteArray) { mem[key] = bytes }
}