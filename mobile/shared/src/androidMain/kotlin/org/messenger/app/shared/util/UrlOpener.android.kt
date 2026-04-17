package org.messenger.app.shared.util

import android.content.Intent
import android.net.Uri

actual fun openUrl(url: String) {
    val ctx = AndroidClipboardHolder.appContext ?: return
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        ctx.startActivity(intent)
    } catch (_: Exception) {}
}