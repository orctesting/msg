package org.messenger.app.service

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.*
import org.messenger.app.AppLifecycleObserver
import org.messenger.app.MainActivity
import org.messenger.app.MessengerApplication

class AppFirebaseMessagingService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM token: $token")

        val app = applicationContext as? MessengerApplication ?: return
        val tokenStorage = app.appModule.tokenStorage

        if (tokenStorage.isLoggedIn()) {
            scope.launch {
                try {
                    app.appModule.apiService.registerPushToken(token, "fcm")
                    Log.d(TAG, "FCM token registered on backend")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to register FCM token", e)
                    // Сохраняем для повторной попытки при следующем запуске
                    PendingTokenStore.savePendingToken(applicationContext, token)
                }
            }
        } else {
            PendingTokenStore.savePendingToken(applicationContext, token)
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "FCM message received: ${remoteMessage.data}")

        val chatId = remoteMessage.data["chat_id"] ?: return
        val messageId = remoteMessage.data["message_id"] ?: return
        val senderId = remoteMessage.data["sender_id"]

        val title = remoteMessage.notification?.title
            ?: remoteMessage.data["title"]
            ?: "Новое сообщение"
        val body = remoteMessage.notification?.body
            ?: remoteMessage.data["body"]
            ?: ""

        // Если приложение в foreground и этот чат открыт — не показываем
        if (AppLifecycleObserver.currentChatId == chatId) {
            return
        }

        showNotification(chatId, messageId, title, body)
    }

    private fun showNotification(
        chatId: String,
        messageId: String,
        title: String,
        body: String
    ) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_CHAT_ID, chatId)
            putExtra(EXTRA_CHAT_NAME, title)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            chatId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, MessengerApplication.CHANNEL_CHAT_MESSAGES)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setGroup(GROUP_CHAT_PREFIX + chatId)
            .build()

        try {
            NotificationManagerCompat.from(this)
                .notify(messageId.hashCode(), notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "No notification permission", e)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "FCMService"
        const val EXTRA_CHAT_ID = "extra_chat_id"
        const val EXTRA_CHAT_NAME = "extra_chat_name"
        private const val GROUP_CHAT_PREFIX = "chat_"
    }
}