package org.messenger.app

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform