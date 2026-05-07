package org.messenger.app

import org.messenger.app.shared.di.AppModule
import kotlin.concurrent.Volatile

/**
 * Singleton-хранилище текущего AppModule для iOS, чтобы Swift-сторона
 * (AppDelegate) и actual-функции (`syncAppModule`, `onLoginSuccessCallback`)
 * могли достучаться до DI без передачи параметров.
 */
object IosAppHolder {
    @Volatile
    var appModule: AppModule? = null

    @Volatile
    var lastApnsToken: String? = null
}