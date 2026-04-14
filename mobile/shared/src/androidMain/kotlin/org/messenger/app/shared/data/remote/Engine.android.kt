package org.messenger.app.shared.data.remote

import io.ktor.client.engine.*
import io.ktor.client.engine.okhttp.*

actual fun createPlatformEngine(): HttpClientEngine = OkHttp.create()