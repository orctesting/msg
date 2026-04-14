package org.messenger.app.shared.data.remote

import io.ktor.client.engine.*
import io.ktor.client.engine.js.*

actual fun createPlatformEngine(): HttpClientEngine = Js.create()