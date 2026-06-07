package org.messenger.app.updates

actual fun currentTimeMillis(): Long =
    (kotlinx.datetime.Clock.System.now().toEpochMilliseconds())