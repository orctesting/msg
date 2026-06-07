package org.messenger.app.updates

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.messenger.app.shared.data.model.UpdateInfo

actual object PlatformUpdater {
    actual fun platformId(): String = "android"

    actual fun currentVersionCode(): Int = AndroidVersionProvider.versionCode

    actual fun downloadAndInstall(info: UpdateInfo): Flow<UpdateProgress> = flow {
        // Android: обновление через стор/APK реализуется отдельно.
        emit(UpdateProgress.Failed("Android update not implemented"))
    }
}

object AndroidVersionProvider {
    @Volatile var versionCode: Int = 0
}