package org.messenger.app

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

object ChatListResyncBus {
    private val _events = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val events = _events.asSharedFlow()

    fun requestResync() {
        _events.tryEmit(Unit)
    }
}

/**
 * Единый holder «какой чат сейчас открыт» — общий для mobile и desktop.
 * Обновляется из ChatScreen / split-pane.
 */
object ActiveChatHolder {
    private val _activeChatId = MutableStateFlow<String?>(null)
    val activeChatId = _activeChatId.asStateFlow()

    fun set(chatId: String?) {
        _activeChatId.value = chatId
    }

    fun get(): String? = _activeChatId.value
}