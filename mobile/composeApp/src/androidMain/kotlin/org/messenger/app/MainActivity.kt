package org.messenger.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.messenger.app.service.AppFirebaseMessagingService
import org.messenger.app.service.FcmTokenManager

class MainActivity : ComponentActivity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as MessengerApplication
        val appModule = app.appModule

        val deepLinkChatId = intent.getStringExtra(AppFirebaseMessagingService.EXTRA_CHAT_ID)
        val deepLinkChatName = intent.getStringExtra(AppFirebaseMessagingService.EXTRA_CHAT_NAME)

        requestNotificationPermission()

        if (appModule.tokenStorage.isLoggedIn()) {
            scope.launch(Dispatchers.IO) {
                FcmTokenManager.registerIfNeeded(applicationContext, appModule.apiService)
            }
        }

        setContent {
            App(
                appModule = appModule,
                deepLinkChatId = deepLinkChatId,
                deepLinkChatName = deepLinkChatName
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Обработка нового deep link при singleTop
        val chatId = intent.getStringExtra(AppFirebaseMessagingService.EXTRA_CHAT_ID)
        if (chatId != null) {
            val chatName = intent.getStringExtra(AppFirebaseMessagingService.EXTRA_CHAT_NAME) ?: ""
            DeepLinkHandler.onDeepLink(chatId, chatName)
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}