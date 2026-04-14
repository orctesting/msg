package org.messenger.app.shared.data.remote

import io.ktor.client.engine.*
import io.ktor.client.engine.java.*

actual fun createPlatformEngine(): HttpClientEngine = Java.create()