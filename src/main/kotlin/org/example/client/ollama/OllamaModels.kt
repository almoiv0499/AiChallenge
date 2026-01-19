package org.example.client.ollama

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Модели для работы с Ollama API
 * Документация: https://docs.ollama.com/api/introduction
 */

// ==================== Chat API Models ====================

@Serializable
data class OllamaChatRequest(
    val model: String,
    val messages: List<OllamaMessage>,
    val stream: Boolean = false,
    val format: String? = null, // "json" or JSON schema
    val options: OllamaOptions? = null,
    val tools: List<OllamaTool>? = null,
    val keepAlive: String? = null, // e.g. "5m"
    val think: Boolean? = null, // or "high", "medium", "low"
    val logprobs: Boolean? = null,
    @SerialName("top_logprobs") val topLogprobs: Int? = null
)

@Serializable
data class OllamaMessage(
    val role: String, // "user", "assistant", "system"
    val content: String,
    val thinking: String? = null,
    @SerialName("tool_calls") val toolCalls: List<OllamaToolCall>? = null,
    val images: List<String>? = null // Base64-encoded images
)

@Serializable
data class OllamaToolCall(
    val function: OllamaFunction
)

@Serializable
data class OllamaFunction(
    val name: String,
    val description: String? = null,
    val arguments: Map<String, kotlinx.serialization.json.JsonElement>? = null
)

@Serializable
data class OllamaTool(
    val type: String = "function",
    val function: OllamaFunctionDefinition
)

@Serializable
data class OllamaFunctionDefinition(
    val name: String,
    val description: String,
    val parameters: Map<String, kotlinx.serialization.json.JsonElement>? = null
)

@Serializable
data class OllamaChatResponse(
    val model: String,
    @SerialName("created_at") val createdAt: String? = null,
    val message: OllamaChatMessage? = null,
    val done: Boolean = false,
    @SerialName("done_reason") val doneReason: String? = null,
    @SerialName("total_duration") val totalDuration: Long? = null,
    @SerialName("load_duration") val loadDuration: Long? = null,
    @SerialName("prompt_eval_count") val promptEvalCount: Int? = null,
    @SerialName("prompt_eval_duration") val promptEvalDuration: Long? = null,
    @SerialName("eval_count") val evalCount: Int? = null,
    @SerialName("eval_duration") val evalDuration: Long? = null,
    val logprobs: List<OllamaLogProb>? = null
)

@Serializable
data class OllamaChatMessage(
    val role: String,
    val content: String,
    val thinking: String? = null,
    @SerialName("tool_calls") val toolCalls: List<OllamaToolCall>? = null,
    val images: List<String>? = null
)

@Serializable
data class OllamaLogProb(
    val token: String,
    val logprob: Double,
    val bytes: List<Int>? = null,
    @SerialName("top_logprobs") val topLogprobs: List<OllamaTopLogProb>? = null
)

@Serializable
data class OllamaTopLogProb(
    val token: String,
    val logprob: Double,
    val bytes: List<Int>? = null
)

// ==================== Generate API Models ====================

@Serializable
data class OllamaGenerateRequest(
    val model: String,
    val prompt: String,
    val suffix: String? = null,
    val images: List<String>? = null, // Base64-encoded images
    val format: String? = null, // "json" or JSON schema
    val system: String? = null, // System prompt
    val stream: Boolean = false,
    val raw: Boolean = false,
    val think: Boolean? = null, // or "high", "medium", "low"
    val options: OllamaOptions? = null,
    val keepAlive: String? = null, // e.g. "5m"
    val logprobs: Boolean? = null,
    @SerialName("top_logprobs") val topLogprobs: Int? = null
)

@Serializable
data class OllamaGenerateResponse(
    val model: String,
    @SerialName("created_at") val createdAt: String? = null,
    val response: String,
    val thinking: String? = null,
    val done: Boolean = false,
    @SerialName("done_reason") val doneReason: String? = null,
    @SerialName("total_duration") val totalDuration: Long? = null,
    @SerialName("load_duration") val loadDuration: Long? = null,
    @SerialName("prompt_eval_count") val promptEvalCount: Int? = null,
    @SerialName("prompt_eval_duration") val promptEvalDuration: Long? = null,
    @SerialName("eval_count") val evalCount: Int? = null,
    @SerialName("eval_duration") val evalDuration: Long? = null,
    val logprobs: List<OllamaLogProb>? = null
)

// ==================== Options ====================

@Serializable
data class OllamaOptions(
    val temperature: Double? = null,
    @SerialName("top_p") val topP: Double? = null,
    @SerialName("top_k") val topK: Int? = null,
    @SerialName("num_predict") val numPredict: Int? = null, // max tokens
    @SerialName("repeat_penalty") val repeatPenalty: Double? = null,
    @SerialName("seed") val seed: Long? = null,
    @SerialName("num_ctx") val numCtx: Int? = null, // context window size
    @SerialName("num_thread") val numThread: Int? = null
)

// ==================== Models List API ====================

@Serializable
data class OllamaListModelsResponse(
    val models: List<OllamaModelInfo>
)

@Serializable
data class OllamaModelInfo(
    val name: String,
    @SerialName("modified_at") val modifiedAt: String? = null,
    val size: Long? = null,
    val digest: String? = null,
    val details: OllamaModelDetails? = null
)

@Serializable
data class OllamaModelDetails(
    val format: String? = null,
    val family: String? = null,
    val families: List<String>? = null,
    @SerialName("parameter_size") val parameterSize: String? = null,
    @SerialName("quantization_level") val quantizationLevel: String? = null
)

// ==================== Running Models API ====================

@Serializable
data class OllamaRunningModelsResponse(
    val models: List<OllamaRunningModel>
)

@Serializable
data class OllamaRunningModel(
    val model: String,
    val size: Long? = null,
    val digest: String? = null,
    val details: OllamaModelDetails? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("size_vram") val sizeVram: Long? = null,
    @SerialName("context_length") val contextLength: Int? = null
)

// ==================== Error ====================

@Serializable
data class OllamaError(
    val error: String
)
