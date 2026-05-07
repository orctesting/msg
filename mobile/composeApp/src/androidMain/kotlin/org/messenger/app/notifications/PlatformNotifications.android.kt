package org.messenger.app.notifications

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.messenger.app.MessengerApplication
import org.messenger.app.service.NotificationSettingsSync

actual fun reloadDesktopNotificationSettings() {
    try {
        val app = MessengerApplication.instance
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            NotificationSettingsSync.refresh(app, app.appModule)
        }
    } catch (_: Exception) {}
}