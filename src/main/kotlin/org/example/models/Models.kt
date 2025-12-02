package org.example.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val jsonParser = Json { ignoreUnknownKeys = true }

@Serializable
data class ApiResponse(
    val status: String,
    @SerialName("userMessage") val userMessage: String,
    val answer: String
) {
    fun toJsonString(): String = jsonParser.encodeToString(serializer(), this)

    companion object {
        private val JSON_PATTERN = Regex("""\{[^{}]*"status"[^{}]*"userMessage"[^{}]*"answer"[^{}]*\}""")

        fun success(userMessage: String, answer: String): ApiResponse =
            ApiResponse(status = "success", userMessage = userMessage, answer = answer)

        fun error(userMessage: String, answer: String): ApiResponse =
            ApiResponse(status = "error", userMessage = userMessage, answer = answer)

        fun parseFromString(jsonString: String): ApiResponse? = runCatching {
            jsonParser.decodeFromString<ApiResponse>(jsonString)
        }.getOrNull()

        fun extractJsonFromText(text: String): String? = JSON_PATTERN.find(text)?.value
    }
}

@Serializable
data class ChatResponse(
    val response: String,
    val toolCalls: List<ToolCallResult> = emptyList(),
    val apiResponse: ApiResponse? = null
)

@Serializable
data class ToolCallResult(
    val toolName: String,
    val arguments: Map<String, String>,
    val result: String
)
