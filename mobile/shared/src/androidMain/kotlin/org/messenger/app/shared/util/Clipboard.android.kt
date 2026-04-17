package org.messenger.app.shared.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

// Context holder — инициализируется в MessengerApplication
object AndroidClipboardHolder {
    @Volatile var appContext: Context? = null
}

actual fun copyToClipboard(text: String) {
    val ctx = AndroidClipboardHolder.appContext ?: return
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    cm.setPrimaryClip(ClipData.newPlainText("message", text))
}