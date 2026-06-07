package org.messenger.app.shared.domain.repository

import org.messenger.app.shared.data.model.UpdateInfo
import org.messenger.app.shared.data.remote.ApiService

class UpdateRepository(private val api: ApiService) {
    suspend fun checkForUpdate(
        platform: String,
        currentVersionCode: Int,
        channel: String = "stable",
    ): UpdateInfo? = api.getLatestRelease(platform, currentVersionCode, channel)
}