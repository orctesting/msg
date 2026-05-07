package org.messenger.app

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Шина deep-link событий от тапа по push-уведомлению.
 * `observeDeepLinks()` actual подписывается на этот Flow.
 */
object IosDeepLinkBus {
    private val _events = MutableSharedFlow<Pair<String, String?>>(extraBufferCapacity = 4)
    val events = _events.asSharedFlow()

    fun emit(chatId: String, chatName: String?) {
        _events.tryEmit(chatId to chatName)
    }
}