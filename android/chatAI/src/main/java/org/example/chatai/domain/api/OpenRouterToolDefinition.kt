package org.example.chatai.domain.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Определение инструмента для OpenRouter API
 */
@Serializable
data class OpenRouterToolDefinition(
    val type: String = "function",
    val name: String,
    val description: String,
    val parameters: OpenRouterToolParameters
)

/**
 * Параметры инструмента
 */
@Serializable
data class OpenRouterToolParameters(
    val type: String = "object",
    val properties: Map<String, OpenRouterPropertyDefinition> = emptyMap(),
    val required: List<String> = emptyList()
)

/**
 * Определение свойства параметра
 */
@Serializable
data class OpenRouterPropertyDefinition(
    val type: String,
    val description: String? = null,
    val enum: List<String>? = null
)
