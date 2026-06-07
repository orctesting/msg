package org.messenger.app.shared.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UpdateInfo(
    @SerialName("version_name") val versionName: String,
    @SerialName("version_code") val versionCode: Int,
    @SerialName("download_url") val downloadUrl: String,
    val sha256: String,
    @SerialName("file_size_bytes") val fileSizeBytes: Long,
    @SerialName("release_notes") val releaseNotes: String? = null,
    @SerialName("is_mandatory") val isMandatory: Boolean = false,
)