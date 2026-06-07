package org.messenger.app.updates

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.messenger.app.shared.data.model.UpdateInfo

actual object PlatformUpdater {
    actual fun platformId(): String = "ios"
    actual fun currentVersionCode(): Int = 0
    actual fun downloadAndInstall(info: UpdateInfo): Flow<UpdateProgress> = flow {
        emit(UpdateProgress.Failed("iOS updates via App Store"))
    }
}