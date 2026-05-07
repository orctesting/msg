package org.messenger.app.shared.util

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.*
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class)
actual object ImageCache {
    private val dirPath: String by lazy {
        val paths = NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true)
        val base = paths.firstOrNull() as? String ?: NSTemporaryDirectory()
        val full = "$base/images"
        NSFileManager.defaultManager.createDirectoryAtPath(full, true, null, null)
        full
    }

    actual suspend fun get(key: String): ByteArray? {
        val path = "$dirPath/$key"
        val data = NSData.dataWithContentsOfFile(path) ?: return null
        val len = data.length.toInt()
        if (len == 0) return null
        val arr = ByteArray(len)
        arr.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) }
        return arr
    }

    actual suspend fun put(key: String, bytes: ByteArray) {
        val path = "$dirPath/$key"
        bytes.usePinned {
            val data = NSData.dataWithBytes(it.addressOf(0), bytes.size.toULong())
            data.writeToFile(path, true)
        }
    }
}