package org.messenger.app.updates

object UpdateConfig {
    const val INITIAL_DELAY_MS = 60_000L          // первая проверка через 1 мин после входа
    const val PERIODIC_INTERVAL_MS = 60 * 60_000L // далее раз в час (если в сети)
    const val REMIND_INTERVAL_USER_MS = 24 * 60 * 60_000L // повтор предложения раз в сутки
    const val REMIND_INTERVAL_ADMIN_MS = 5 * 60_000L      // для админа раз в 5 минут
}