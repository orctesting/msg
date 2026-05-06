package org.messenger.app

import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import org.messenger.app.shared.di.AppModule

fun MainViewController() = ComposeUIViewController {
    val appModule = remember { AppModule().also { IosAppHolder.appModule = it } }
    App(appModule = appModule)
}