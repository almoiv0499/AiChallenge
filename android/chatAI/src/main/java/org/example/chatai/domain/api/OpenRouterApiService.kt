package org.example.chatai.domain.api

import android.util.Log
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.example.chatai.data.model.ChatMessage

private const val TAG = "OpenRouterApiService"

/**
 * Сервис для взаимодействия с OpenRouter API.
 * Использует Ktor для сетевых запросов с поддержкой JSON сериализации.
 */
class OpenRouterApiService(
    private val apiKey: String,
    private val baseUrl: String = "https://openrouter.ai/api/v1"
) {
    
    private val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            })
        }
        install(Logging) {
            level = LogLevel.INFO
        }
    }
    
    /**
     * Отправляет сообщение и получает ответ от AI.
     * 
     * @param message Сообщение пользователя
     * @param history История предыдущих сообщений для контекста
     * @return Ответ от AI
     */
    suspend fun sendMessage(message: String, history: List<ChatMessage> = emptyList()): String {
        // Преобразуем историю в формат OpenRouter API
        val messages = buildOpenRouterMessages(history + ChatMessage(
            role = org.example.chatai.data.model.MessageRole.USER,
            content = message
        ))
        
        val request = OpenRouterRequest(
            model = "deepseek/deepseek-v3.2",
            messages = messages
        )
        
        return try {
            val response: OpenRouterResponse = client.post("$baseUrl/chat/completions") {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                header(HttpHeaders.ContentType, ContentType.Application.Json)
                setBody(request)
            }.body()
            
            response.choices.firstOrNull()?.message?.content
                ?: throw Exception("Пустой ответ от API")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при отправке сообщения в API", e)
            throw Exception("Ошибка при отправке сообщения: ${e.message}", e)
        }
    }
    
    /**
     * Отправляет сообщение с поддержкой tool calling.
     * 
     * @param message Сообщение пользователя (может быть пустым при повторных итерациях)
     * @param history История предыдущих сообщений для контекста
     * @param tools Определения инструментов для tool calling
     * @return Ответ с возможными tool calls
     */
    suspend fun sendMessageWithTools(
        message: String,
        history: List<ChatMessage>,
        tools: List<OpenRouterToolDefinition>? = null
    ): ChatApiResponse {
        val messages = if (message.isNotBlank()) {
            buildOpenRouterMessages(history + ChatMessage(
                role = org.example.chatai.data.model.MessageRole.USER,
                content = message
            ))
        } else {
            buildOpenRouterMessages(history)
        }
        
        val request = OpenRouterRequestWithTools(
            model = "deepseek/deepseek-v3.2",
            messages = messages,
            tools = tools?.map { it.toApiFormat() },
            temperature = 0.7,
            maxTokens = 2000
        )
        
        return try {
            val httpResponse = client.post("$baseUrl/chat/completions") {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                header(HttpHeaders.ContentType, ContentType.Application.Json)
                setBody(request)
            }
            
            // Проверяем статус код
            if (!httpResponse.status.isSuccess()) {
                val errorBody = httpResponse.bodyAsText()
                Log.e(TAG, "API вернул ошибку: ${httpResponse.status}, body: $errorBody")
                throw Exception("Ошибка API: ${httpResponse.status} - $errorBody")
            }
            
            // Читаем сырой ответ для логирования
            val responseBody = httpResponse.bodyAsText()
            Log.d(TAG, "Ответ от API: ${responseBody.take(500)}...")
            
            // Пытаемся десериализовать ответ
            val response: OpenRouterResponseWithTools = try {
                httpResponse.body()
            } catch (e: Exception) {
                // Если не удалось десериализовать, возможно это ошибка API
                Log.e(TAG, "Ошибка десериализации ответа: ${e.message}", e)
                Log.e(TAG, "Сырой ответ: $responseBody")
                
                // Пытаемся распарсить как ошибку
                val errorResponse = try {
                    val json = Json { ignoreUnknownKeys = true }
                    json.decodeFromString<OpenRouterErrorResponse>(responseBody)
                } catch (e2: Exception) {
                    null
                }
                
                if (errorResponse?.error != null) {
                    throw Exception("Ошибка API: ${errorResponse.error.message ?: "Неизвестная ошибка"}")
                } else {
                    throw Exception("Ошибка при обработке ответа API: ${e.message}")
                }
            }
            
            val choice = response.choices.firstOrNull()
            if (choice == null) {
                Log.e(TAG, "Пустой ответ от API (нет choices в ответе)")
                throw Exception("Пустой ответ от API")
            }
            
            val messageContent = choice.message.content
            val toolCalls = choice.message.toolCalls?.map { toolCall ->
                ToolCall(
                    id = toolCall.id,
                    type = toolCall.type ?: "function",
                    function = FunctionCall(
                        name = toolCall.function.name,
                        arguments = toolCall.function.arguments
                    )
                )
            } ?: emptyList()
            
            val finishReason = choice.finishReason
            
            ChatApiResponse(
                content = messageContent,
                toolCalls = toolCalls,
                finishReason = finishReason
            )
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при отправке сообщения с tools в API", e)
            throw Exception("Ошибка при отправке сообщения с tools: ${e.message}", e)
        }
    }
    
    /**
     * Преобразует список ChatMessage в формат OpenRouter API
     */
    private fun buildOpenRouterMessages(messages: List<ChatMessage>): List<OpenRouterMessage> {
        return messages.filter { 
            // Фильтруем TOOL сообщения - они будут обрабатываться отдельно через tool_calls
            it.role != org.example.chatai.data.model.MessageRole.TOOL &&
            it.role != org.example.chatai.data.model.MessageRole.MCP
        }.map { chatMessage ->
            OpenRouterMessage(
                role = chatMessage.roleToString(),
                content = chatMessage.content
            )
        }
    }
    
    /**
     * Закрывает HTTP клиент
     */
    fun close() {
        client.close()
    }
}

/**
 * Модель запроса к OpenRouter API
 */
@Serializable
private data class OpenRouterRequest(
    val model: String,
    val messages: List<OpenRouterMessage>,
    val temperature: Double = 0.7,
    @SerialName("max_tokens") val maxTokens: Int = 1000
)

/**
 * Модель сообщения для OpenRouter API
 */
@Serializable
private data class OpenRouterMessage(
    val role: String,
    val content: String
)

/**
 * Модель ответа от OpenRouter API
 */
@Serializable
private data class OpenRouterResponse(
    val choices: List<OpenRouterChoice>
)

/**
 * Модель выбора ответа
 */
@Serializable
private data class OpenRouterChoice(
    val message: OpenRouterMessage
)

/**
 * Модель запроса с поддержкой tools
 */
@Serializable
private data class OpenRouterRequestWithTools(
    val model: String,
    val messages: List<OpenRouterMessage>,
    val tools: List<OpenRouterToolApiFormat>? = null,
    val temperature: Double = 0.7,
    @SerialName("max_tokens") val maxTokens: Int = 2000
)

/**
 * Формат инструмента для API
 */
@Serializable
private data class OpenRouterToolApiFormat(
    val type: String = "function",
    val function: OpenRouterFunctionDefinition
)

@Serializable
private data class OpenRouterFunctionDefinition(
    val name: String,
    val description: String,
    val parameters: OpenRouterToolParameters
)

/**
 * Модель ответа с поддержкой tool_calls
 */
@Serializable
private data class OpenRouterResponseWithTools(
    val choices: List<OpenRouterChoiceWithTools> = emptyList(),
    val error: OpenRouterError? = null
)

@Serializable
private data class OpenRouterChoiceWithTools(
    val message: OpenRouterMessageWithTools,
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
private data class OpenRouterMessageWithTools(
    val role: String,
    val content: String? = null,
    @SerialName("tool_calls") val toolCalls: List<OpenRouterToolCallApiFormat>? = null
)

@Serializable
private data class OpenRouterToolCallApiFormat(
    val id: String,
    val type: String? = "function",
    val function: OpenRouterToolCallFunction
)

@Serializable
private data class OpenRouterToolCallFunction(
    val name: String,
    val arguments: String
)

/**
 * Модель ошибки API
 */
@Serializable
private data class OpenRouterErrorResponse(
    val error: OpenRouterError
)

/**
 * Модель ошибки
 */
@Serializable
private data class OpenRouterError(
    val message: String? = null,
    val type: String? = null,
    val code: String? = null
)

/**
 * Расширение для преобразования OpenRouterToolDefinition в формат API
 */
private fun OpenRouterToolDefinition.toApiFormat(): OpenRouterToolApiFormat {
    return OpenRouterToolApiFormat(
        type = this.type,
        function = OpenRouterFunctionDefinition(
            name = this.name,
            description = this.description,
            parameters = this.parameters
        )
    )
}
