package org.messenger.app

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import org.messenger.app.shared.di.AppModule

fun main() = application {
    val appModule = AppModule(
        baseUrl = "http://localhost:8000/",
        wsBaseUrl = "ws://localhost:8000/"
    )
    Window(
        onCloseRequest = ::exitApplication,
        title = "MessengerApp",
    ) {
        App(appModule = appModule)
    }
}