package org.messenger.app.service

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.messenger.app.shared.data.remote.WsService

object WsNotificationBridge {
    fun observe(scope: CoroutineScope, context: Context, wsService: WsService) {
        scope.launch {
            wsService.events.collect { event ->
                if (event.type != "notification_dismiss") return@collect
                try {
                    val obj: JsonObject = event.data.jsonObject
                    val chatId = obj["chat_id"]?.jsonPrimitive?.content ?: return@collect
                    val ids: List<String> = (obj["message_ids"] as? JsonArray)
                        ?.map { it.jsonPrimitive.content }
                        ?: emptyList()
                    NotificationDismisser.dismiss(context, chatId, ids)
                } catch (_: Exception) {}
            }
        }
    }
}