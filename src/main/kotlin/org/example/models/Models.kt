package org.example.models

import kotlinx.serialization.Serializable

@Serializable
data class ChatResponse(
    val response: String,
    val toolCalls: List<ToolCallResult> = emptyList()
)

@Serializable
data class ToolCallResult(
    val toolName: String,
    val arguments: Map<String, String>,
    val result: String
)
