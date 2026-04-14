package org.messenger.app.service

import android.content.Context
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
import org.messenger.app.shared.data.remote.ApiService

object FcmTokenManager {

    private const val TAG = "FcmTokenManager"

    /**
     * Вызывается после успешной авторизации.
     * Регистрирует текущий FCM-токен на бэкенде.
     */
    suspend fun registerIfNeeded(context: Context, apiService: ApiService) {
        try {
            // Сначала проверяем pending token
            val pending = PendingTokenStore.getPendingToken(context)
            val token = pending ?: FirebaseMessaging.getInstance().token.await()

            apiService.registerPushToken(token, "fcm")
            PendingTokenStore.clearPendingToken(context)
            Log.d(TAG, "FCM token registered successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register FCM token", e)
        }
    }
}