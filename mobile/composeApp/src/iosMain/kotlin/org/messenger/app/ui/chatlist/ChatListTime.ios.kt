package org.messenger.app.ui.chatlist

import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

actual fun currentChatListTimeMillis(): Long =
    (NSDate().timeIntervalSince1970 * 1000.0).toLong()