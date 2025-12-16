package org.example.notion

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NotionErrorResponse(
    @SerialName("object") val objectType: String = "error",
    val status: Int,
    val code: String,
    val message: String,
    @SerialName("request_id") val requestId: String? = null
)

