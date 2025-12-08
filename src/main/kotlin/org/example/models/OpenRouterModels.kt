package org.example.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import org.example.config.OpenRouterConfig

@Serializable
data class OpenRouterRequest(
    val model: String,
    val input: List<JsonElement>,
    val tools: List<OpenRouterTool>? = null,
    val temperature: Double? = OpenRouterConfig.Temperature.VERY_HIGH,
    @SerialName("top_p") val topP: Double? = 0.9
)

@Serializable
data class OpenRouterInputMessage(
    val type: String = "message",
    val role: String,
    val content: List<OpenRouterInputContentItem>
)

@Serializable
data class OpenRouterFunctionCallInput(
    val type: String = "function_call",
    val id: String,
    @SerialName("call_id") val callId: String,
    val name: String,
    val arguments: String,
    val status: String = "completed"
)

@Serializable
data class OpenRouterFunctionCallOutput(
    val type: String = "function_call_output",
    @SerialName("call_id") val callId: String,
    val output: String
)

@Serializable
data class OpenRouterInputContentItem(
    val type: String,
    val text: String
)

@Serializable
data class OpenRouterTool(
    val type: String = "function",
    val name: String,
    val description: String,
    val parameters: OpenRouterToolParameters
)

@Serializable
data class OpenRouterToolParameters(
    val type: String = "object",
    val properties: Map<String, OpenRouterPropertyDefinition> = emptyMap(),
    val required: List<String> = emptyList()
)

@Serializable
data class OpenRouterPropertyDefinition(
    val type: String,
    val description: String? = null,
    val enum: List<String>? = null
)

@Serializable
data class OpenRouterResponse(
    val id: String? = null,
    val model: String? = null,
    val output: List<OpenRouterOutputItem>? = null,
    val usage: OpenRouterUsage? = null,
    val error: OpenRouterError? = null
)

@Serializable
data class OpenRouterOutputItem(
    val type: String,
    val id: String? = null,
    val role: String? = null,
    val content: List<OpenRouterContentItem>? = null,
    val status: String? = null,
    val name: String? = null,
    val arguments: String? = null,
    @SerialName("call_id") val callId: String? = null
)

@Serializable
data class OpenRouterContentItem(
    val type: String,
    val text: String? = null
)

@Serializable
data class OpenRouterUsage(
    @SerialName("input_tokens") val inputTokens: Int? = null,
    @SerialName("output_tokens") val outputTokens: Int? = null,
    @SerialName("total_tokens") val totalTokens: Int? = null
)

@Serializable
data class OpenRouterError(
    val code: String? = null,
    val message: String? = null
)


