package org.messenger.app.notifications

internal actual fun loadInitialPlacement(): String = "system_overlay"
internal actual fun savePlacement(value: String) {
    // На Android placement не используется — no-op.
}