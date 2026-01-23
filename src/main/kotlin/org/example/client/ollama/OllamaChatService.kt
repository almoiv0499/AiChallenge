package org.example.client.ollama

import kotlinx.coroutines.runBlocking
import org.example.ui.ConsoleUI
import org.example.models.ChatResponse

/**
 * Сервис для общения с Ollama в терминале
 * Простой чат-интерфейс с поддержкой истории диалога
 */
class OllamaChatService(
    private val ollamaClient: OllamaClient,
    private val model: String = "llama3.2",
    private val systemPrompt: String? = null,
    private val options: OllamaOptions? = null
) {
    private val conversationHistory = mutableListOf<OllamaMessage>()

    /**
     * Обрабатывает сообщение пользователя и возвращает ответ
     */
    suspend fun processMessage(userMessage: String): ChatResponse {
        ConsoleUI.printUserMessage(userMessage)

        val response = if (systemPrompt != null) {
            // Если есть системный промпт, используем его
            if (conversationHistory.isEmpty()) {
                // Первое сообщение - добавляем системный промпт
                conversationHistory.add(OllamaMessage(role = "system", content = systemPrompt))
                ollamaClient.chatWithSystemPrompt(
                    systemPrompt = systemPrompt,
                    userMessage = userMessage,
                    model = model,
                    options = options
                )
            } else {
                // Последующие сообщения
                ollamaClient.chat(
                    message = userMessage,
                    model = model,
                    conversationHistory = conversationHistory,
                    options = options
                )
            }
        } else {
            // Без системного промпта
            ollamaClient.chat(
                message = userMessage,
                model = model,
                conversationHistory = conversationHistory,
                options = options
            )
        }

        // Добавляем сообщения в историю
        conversationHistory.add(OllamaMessage(role = "user", content = userMessage))
        val assistantMessage = response.message?.content ?: "Ошибка: пустой ответ от модели"
        
        // Создаем сообщение ассистента с учетом всех полей
        val assistantMsg = OllamaMessage(
            role = "assistant",
            content = assistantMessage,
            thinking = response.message?.thinking,
            toolCalls = response.message?.toolCalls,
            images = response.message?.images
        )
        conversationHistory.add(assistantMsg)
        
        // Если есть tool_calls, логируем их
        if (response.message?.toolCalls != null && response.message.toolCalls.isNotEmpty()) {
            println("\n🔧 Модель запросила вызов инструментов:")
            response.message.toolCalls.forEach { toolCall ->
                println("   • ${toolCall.function.name}: ${toolCall.function.description ?: "без описания"}")
            }
        }

        return ChatResponse(
            response = assistantMessage,
            toolCalls = emptyList()
        )
    }

    /**
     * Очищает историю диалога
     */
    fun clearHistory() {
        conversationHistory.clear()
        if (systemPrompt != null) {
            conversationHistory.add(OllamaMessage(role = "system", content = systemPrompt))
        }
    }

    /**
     * Получает текущий размер истории
     */
    fun getHistorySize(): Int = conversationHistory.size
}
