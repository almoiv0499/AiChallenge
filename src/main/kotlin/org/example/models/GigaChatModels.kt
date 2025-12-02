package org.example.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class GigaChatTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("expires_at") val expiresAt: Long
)

@Serializable
data class GigaChatRequest(
    val model: String = "GigaChat",
    val messages: List<GigaChatMessage>,
    val functions: List<GigaChatFunction>? = null,
    @SerialName("function_call") val functionCall: String? = null,
    val temperature: Double? = null,
    @SerialName("max_tokens") val maxTokens: Int? = null,
    val stream: Boolean = false
)

@Serializable
data class GigaChatMessage(
    val role: String,
    val content: String? = null,
    @SerialName("function_call") val functionCall: GigaChatFunctionCallResult? = null,
    @SerialName("functions_state_id") val functionsStateId: String? = null
)

@Serializable
data class GigaChatFunctionCallResult(
    val name: String,
    val arguments: JsonObject
)

@Serializable
data class GigaChatFunction(
    val name: String,
    val description: String,
    val parameters: GigaChatFunctionParameters,
    @SerialName("few_shot_examples") val fewShotExamples: List<GigaChatFewShotExample>? = null,
    @SerialName("return_parameters") val returnParameters: GigaChatFunctionParameters? = null
)

@Serializable
data class GigaChatFunctionParameters(
    val type: String = "object",
    val properties: Map<String, GigaChatPropertyDefinition> = emptyMap(),
    val required: List<String> = emptyList()
)

@Serializable
data class GigaChatPropertyDefinition(
    val type: String,
    val description: String,
    val enum: List<String>? = null
)

@Serializable
data class GigaChatFewShotExample(
    val request: String,
    val params: JsonObject
)

@Serializable
data class GigaChatResponse(
    val choices: List<GigaChatChoice>,
    val created: Long,
    val model: String,
    val usage: GigaChatUsage? = null,
    val `object`: String? = null
)

@Serializable
data class GigaChatChoice(
    val message: GigaChatMessageResponse,
    val index: Int,
    @SerialName("finish_reason") val finishReason: String?
)

@Serializable
data class GigaChatMessageResponse(
    val role: String,
    val content: String? = null,
    @SerialName("function_call") val functionCall: GigaChatFunctionCallResult? = null,
    @SerialName("functions_state_id") val functionsStateId: String? = null
)

@Serializable
data class GigaChatUsage(
    @SerialName("prompt_tokens") val promptTokens: Int,
    @SerialName("completion_tokens") val completionTokens: Int,
    @SerialName("total_tokens") val totalTokens: Int,
    @SerialName("precached_prompt_tokens") val precachedPromptTokens: Int? = null
)
