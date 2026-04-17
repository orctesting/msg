package org.messenger.app.shared.util

import kotlinx.browser.window

actual fun copyToClipboard(text: String) {
    try {
        window.navigator.clipboard.writeText(text)
    } catch (_: Throwable) {}
}