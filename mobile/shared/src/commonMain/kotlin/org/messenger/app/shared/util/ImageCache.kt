package org.messenger.app.shared.util

expect object ImageCache {
    suspend fun get(key: String): ByteArray?
    suspend fun put(key: String, bytes: ByteArray)
}