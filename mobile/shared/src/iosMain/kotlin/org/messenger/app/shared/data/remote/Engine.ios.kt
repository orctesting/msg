package org.messenger.app.shared.data.remote

import io.ktor.client.engine.*
import io.ktor.client.engine.darwin.*

actual fun createPlatformEngine(): HttpClientEngine = Darwin.create()