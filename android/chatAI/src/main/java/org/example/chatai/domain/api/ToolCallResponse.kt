package org.example.chatai.domain.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Результат выполнения tool call
 */
data class ToolCallResult(
    val toolName: String,
    val arguments: Map<String, String>,
    val result: String
)

/**
 * Ответ от API с возможными tool calls
 */
data class ChatApiResponse(
    val content: String?,
    val toolCalls: List<ToolCall>,
    val finishReason: String?
)

/**
 * Вызов инструмента
 */
@Serializable
data class ToolCall(
    val id: String,
    val type: String = "function",
    val function: FunctionCall
)

@Serializable
data class FunctionCall(
    val name: String,
    val arguments: String
)
