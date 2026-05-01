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

        val data = remoteMessage.data

        // ── action=dismiss: служебный сигнал скрытия уведомлений ──
        val action = data["action"]
        if (action == "dismiss") {
            val chatId = data["chat_id"] ?: return
            val ids = (data["message_ids"] ?: "")
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            NotificationDismisser.dismiss(applicationContext, chatId, ids)
            return
        }

        // ── обычное уведомление о новом сообщении ──
        val chatId = data["chat_id"] ?: return
        val messageId = data["message_id"] ?: return

        val title = remoteMessage.notification?.title
            ?: data["title"]
            ?: "Новое сообщение"
        val body = remoteMessage.notification?.body
            ?: data["body"]
            ?: ""

        // Если приложение в foreground и этот чат открыт — не показываем
        if (AppLifecycleObserver.currentChatId == chatId) {
            return
        }

        // Локальная фильтрация по notification_settings (на случай если бэк
        // ещё не успел получить обновлённые настройки)
        if (!shouldShowByLocalSettings(chatId)) {
            return
        }

        showOrUpdateGroupNotification(
            chatId = chatId,
            chatName = title,
            messageId = messageId,
            senderNameOrTitle = title,
            body = body,
        )
    }

    private fun shouldShowByLocalSettings(chatId: String): Boolean {
        val mode = NotificationSettingsCache.getMode(applicationContext)
        return when (mode) {
            "all" -> true
            "none" -> false
            "whitelist" -> NotificationSettingsCache.getWhitelist(applicationContext).contains(chatId)
            // personal_only локально не определить надёжно (чаты не кэшируем),
            // полагаемся на серверный фильтр
            else -> true
        }
    }

    private fun showOrUpdateGroupNotification(
        chatId: String,
        chatName: String,
        messageId: String,
        senderNameOrTitle: String,
        body: String,
    ) {
        // Сохраняем запись для группировки
        NotificationGroupStore.addMessage(
            context = applicationContext,
            chatId = chatId,
            chatName = chatName,
            entry = NotificationGroupStore.Entry(
                messageId = messageId,
                senderName = senderNameOrTitle,
                text = body,
                timestamp = System.currentTimeMillis(),
            ),
        )

        val groupKey = GROUP_CHAT_PREFIX + chatId
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_CHAT_ID, chatId)
            putExtra(EXTRA_CHAT_NAME, chatName)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            chatId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Одиночное уведомление для конкретного сообщения
        val singleNotif = NotificationCompat.Builder(this, MessengerApplication.CHANNEL_CHAT_MESSAGES)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(chatName)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setGroup(groupKey)
            .build()

        // Summary-уведомление с InboxStyle
        val entries = NotificationGroupStore.getEntries(applicationContext, chatId)
        val inboxStyle = NotificationCompat.InboxStyle()
            .setBigContentTitle(chatName)
        entries.takeLast(5).forEach { e ->
            val display = if (e.text.isBlank()) e.senderName else "${e.senderName}: ${e.text}"
            inboxStyle.addLine(display)
        }
        val summaryText = if (entries.size > 1) "${entries.size} новых сообщений" else body
        inboxStyle.setSummaryText(summaryText)

        val summaryNotif = NotificationCompat.Builder(this, MessengerApplication.CHANNEL_CHAT_MESSAGES)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(chatName)
            .setContentText(summaryText)
            .setStyle(inboxStyle)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setGroup(groupKey)
            .setGroupSummary(true)
            .build()

        try {
            val nm = NotificationManagerCompat.from(this)
            nm.notify(messageId.hashCode(), singleNotif)
            nm.notify(NotificationDismisser.summaryIdFor(chatId), summaryNotif)
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