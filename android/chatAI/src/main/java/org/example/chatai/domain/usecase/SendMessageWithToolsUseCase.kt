package org.example.chatai.domain.usecase

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.example.chatai.data.model.ChatMessage
import org.example.chatai.data.model.MessageRole
import org.example.chatai.data.repository.ChatRepository
import org.example.chatai.domain.api.OpenRouterApiService
import org.example.chatai.domain.tools.ToolRegistry
import org.example.chatai.domain.embedding.RagService

private const val TAG = "SendMessageWithToolsUseCase"

/**
 * Use case для отправки сообщения с поддержкой tool calling.
 * Обрабатывает tool calls в цикле агента, как в терминальной версии.
 */
class SendMessageWithToolsUseCase(
    private val chatRepository: ChatRepository,
    private val apiService: OpenRouterApiService,
    private val toolRegistry: ToolRegistry,
    private val ragService: RagService? = null,
    private val maxIterations: Int = 10
) {
    
    /**
     * Отправляет сообщение с обработкой tool calls в цикле агента.
     * 
     * @param userMessage Сообщение пользователя
     * @param ragEnabled Включен ли RAG поиск для обогащения сообщения
     * @return Flow с результатами выполнения (для отслеживания прогресса)
     */
    suspend fun execute(userMessage: String, ragEnabled: Boolean = false): Result<String> {
        Log.d(TAG, "Отправка сообщения с tool calling: ${userMessage.take(50)}...")
        return try {
            // Сохраняем сообщение пользователя
            val userChatMessage = ChatMessage(
                role = MessageRole.USER,
                content = userMessage
            )
            chatRepository.insertMessage(userChatMessage)
            
            // Обогащаем сообщение контекстом из RAG, если включен
            val enrichedMessage = if (ragEnabled && ragService != null && ragService.hasDocuments()) {
                try {
                    Log.d(TAG, "Поиск релевантного контекста через RAG для: ${userMessage.take(50)}...")
                    val searchResults = ragService.search(userMessage, limit = 2, minSimilarity = 0.3)
                    
                    if (searchResults.isNotEmpty()) {
                        val context = searchResults.joinToString("\n\n") { result ->
                            "${result.title ?: result.source}:\n${result.text.take(300)}"
                        }
                        
                        """
                            Контекст из документации проекта:
                            $context
                            
                            Вопрос пользователя: $userMessage
                        """.trimIndent()
                    } else {
                        userMessage
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Ошибка при поиске RAG контекста", e)
                    userMessage
                }
            } else {
                userMessage
            }
            
            // Выполняем цикл агента с обработкой tool calls
            var iteration = 0
            var finalResponse: String? = null
            
            while (iteration < maxIterations) {
                iteration++
                
                // Получаем историю для контекста
                val history = chatRepository.getLastMessages(50)
                
                // Получаем определения инструментов
                val tools = toolRegistry.getToolDefinitions()
                
                // Отправляем запрос с tools (используем обогащенное сообщение на первой итерации)
                val response = apiService.sendMessageWithTools(
                    message = if (iteration == 1) enrichedMessage else "",
                    history = history,
                    tools = if (tools.isNotEmpty()) tools else null
                )
                
                // Обрабатываем tool calls, если они есть
                if (response.toolCalls.isNotEmpty()) {
                    // Сохраняем вызовы инструментов в историю
                    response.toolCalls.forEach { toolCall ->
                        val toolCallMessage = ChatMessage(
                            role = MessageRole.TOOL,
                            content = "${toolCall.function.name}(${toolCall.function.arguments})"
                        )
                        chatRepository.insertMessage(toolCallMessage)
                    }
                    
                    // Выполняем инструменты
                    val toolResults = response.toolCalls.map { toolCall ->
                        executeTool(toolCall)
                    }
                    
                    // Сохраняем результаты выполнения инструментов
                    toolResults.forEach { (toolName, result) ->
                        val resultMessage = ChatMessage(
                            role = MessageRole.TOOL,
                            content = "Результат $toolName: $result"
                        )
                        chatRepository.insertMessage(resultMessage)
                    }
                    
                    // Продолжаем цикл для получения финального ответа
                    continue
                }
                
                // Если есть финальный ответ, сохраняем и возвращаем
                if (response.content != null) {
                    finalResponse = response.content
                    val assistantMessage = ChatMessage(
                        role = MessageRole.ASSISTANT,
                        content = response.content
                    )
                    chatRepository.insertMessage(assistantMessage)
                    break
                }
            }
            
            if (finalResponse != null) {
                Log.d(TAG, "Сообщение успешно обработано за $iteration итераций")
                Result.success(finalResponse)
            } else {
                val error = Exception("Достигнуто максимальное количество итераций ($maxIterations) без финального ответа")
                Log.e(TAG, error.message ?: "Ошибка: достигнуто максимальное количество итераций", error)
                Result.failure(error)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при выполнении sendMessageWithTools", e)
            Result.failure(e)
        }
    }
    
    /**
     * Выполняет инструмент по tool call
     */
    private fun executeTool(toolCall: org.example.chatai.domain.api.ToolCall): Pair<String, String> {
        val toolName = toolCall.function.name
        val argumentsStr = toolCall.function.arguments
        
        // Парсим аргументы из JSON строки
        val arguments = try {
            parseArguments(argumentsStr)
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка парсинга аргументов для инструмента $toolName: $argumentsStr", e)
            return toolName to "Ошибка парсинга аргументов: ${e.message}"
        }
        
        // Находим и выполняем инструмент
        val tool = toolRegistry.getTool(toolName)
        return if (tool != null) {
            try {
                Log.d(TAG, "Выполнение инструмента: $toolName с аргументами: $arguments")
                val result = tool.execute(arguments)
                Log.d(TAG, "Инструмент $toolName выполнен успешно")
                toolName to result
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка при выполнении инструмента $toolName", e)
                toolName to "Ошибка выполнения: ${e.message}"
            }
        } else {
            Log.e(TAG, "Инструмент '$toolName' не найден в реестре")
            toolName to "Ошибка: инструмент '$toolName' не найден"
        }
    }
    
    /**
     * Парсит JSON аргументы в Map
     */
    private fun parseArguments(argumentsStr: String): Map<String, String> {
        return try {
            val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            val jsonElement = json.parseToJsonElement(argumentsStr)
            if (jsonElement is kotlinx.serialization.json.JsonObject) {
                jsonElement.mapValues { it.value.toString().trim('"') }
            } else {
                emptyMap()
            }
        } catch (e: Exception) {
            emptyMap()
        }
    }
}
