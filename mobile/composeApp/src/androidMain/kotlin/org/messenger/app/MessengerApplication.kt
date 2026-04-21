package org.messenger.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.lifecycle.ProcessLifecycleOwner
import org.messenger.app.shared.di.AppModule

class MessengerApplication : Application() {

    lateinit var appModule: AppModule
        private set

    private lateinit var lifecycleObserver: AppLifecycleObserver

    override fun onCreate() {
        super.onCreate()
        instance = this
        org.messenger.app.shared.util.AndroidClipboardHolder.appContext = applicationContext

        // AppModule will auto-resolve URLs from saved TokenStorage
        appModule = AppModule(context = this)

        createNotificationChannels()

        lifecycleObserver = AppLifecycleObserver(appModule)
        ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver)
    }

    /**
     * Called from App.kt when server address changes (login flow creates new AppModule).
     * Updates the lifecycle observer to use the new module's WS service.
     */
    fun updateAppModule(newModule: AppModule) {
        // Disconnect old WS
        appModule.wsService.disconnect()

        appModule = newModule

        // Re-register lifecycle observer with new module
        ProcessLifecycleOwner.get().lifecycle.removeObserver(lifecycleObserver)
        lifecycleObserver = AppLifecycleObserver(newModule)
        ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            val chatChannel = NotificationChannel(
                CHANNEL_CHAT_MESSAGES,
                "Сообщения чатов",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Уведомления о новых сообщениях"
                enableVibration(true)
                setShowBadge(true)
            }

            val systemChannel = NotificationChannel(
                CHANNEL_SYSTEM,
                "Системные",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Системные уведомления"
            }

            manager.createNotificationChannel(chatChannel)
            manager.createNotificationChannel(systemChannel)
        }
    }

    companion object {
        const val CHANNEL_CHAT_MESSAGES = "chat_messages"
        const val CHANNEL_SYSTEM = "system"

        lateinit var instance: MessengerApplication
            private set
    }
}