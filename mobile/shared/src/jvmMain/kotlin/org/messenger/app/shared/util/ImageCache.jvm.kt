package org.messenger.app.shared.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

actual object ImageCache {
    private val dir: File by lazy {
        val tmp = System.getProperty("java.io.tmpdir") ?: "."
        File(tmp, "messenger_image_cache").apply { mkdirs() }
    }

    actual suspend fun get(key: String): ByteArray? = withContext(Dispatchers.IO) {
        val f = File(dir, key)
        if (f.exists() && f.length() > 0) f.readBytes() else null
    }

    actual suspend fun put(key: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        try { File(dir, key).writeBytes(bytes) } catch (_: Exception) {}
    }
}