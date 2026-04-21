package org.messenger.app.shared.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ApiError(
    val detail: String? = null,
    val code: String? = null
)