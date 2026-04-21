package org.messenger.app

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class DeepLink(val chatId: String, val chatName: String)

object DeepLinkHandler {
    private val _deepLinks = MutableSharedFlow<DeepLink>(extraBufferCapacity = 1)
    val deepLinks = _deepLinks.asSharedFlow()

    fun onDeepLink(chatId: String, chatName: String) {
        _deepLinks.tryEmit(DeepLink(chatId, chatName))
    }
}