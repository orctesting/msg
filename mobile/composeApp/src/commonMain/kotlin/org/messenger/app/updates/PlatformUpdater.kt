package org.messenger.app.updates

import kotlinx.coroutines.flow.Flow
import org.messenger.app.shared.data.model.UpdateInfo

sealed interface UpdateProgress {
    data object Idle : UpdateProgress
    data class Downloading(val downloaded: Long, val total: Long) : UpdateProgress
    data object Verifying : UpdateProgress
    data object Installing : UpdateProgress
    data class Failed(val message: String) : UpdateProgress
}

expect object PlatformUpdater {
    fun platformId(): String
    fun currentVersionCode(): Int
    fun downloadAndInstall(info: UpdateInfo): Flow<UpdateProgress>
}