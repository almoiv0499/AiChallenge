package org.example.chatai.domain.usecase

import android.util.Log
import org.example.chatai.data.model.ChatMessage
import org.example.chatai.data.model.MessageRole
import org.example.chatai.data.repository.ChatRepository
import org.example.chatai.domain.api.OpenRouterApiService

private const val TAG = "SendMessageUseCase"

/**
 * Use case для отправки сообщения пользователя и получения ответа от AI.
 * Инкапсулирует бизнес-логику взаимодействия с API и сохранения истории.
 * 
 * Теперь использует SendMessageWithToolsUseCase для поддержки tool calling.
 */
class SendMessageUseCase(
    private val chatRepository: ChatRepository,
    private val apiService: OpenRouterApiService,
    private val sendMessageWithToolsUseCase: SendMessageWithToolsUseCase? = null
) {
    
    /**
     * Отправляет сообщение пользователя и получает ответ от AI.
     * Использует tool calling, если доступно.
     * 
     * @param userMessage Текст сообщения пользователя
     * @param ragEnabled Включен ли RAG поиск
     * @return Результат операции
     */
    suspend fun execute(userMessage: String, ragEnabled: Boolean = false): Result<String> {
        // Если доступен SendMessageWithToolsUseCase, используем его для поддержки tool calling
        return if (sendMessageWithToolsUseCase != null) {
            sendMessageWithToolsUseCase.execute(userMessage, ragEnabled = ragEnabled)
        } else {
            // Fallback на старый метод без tool calling
            try {
                // Сохраняем сообщение пользователя
                val userChatMessage = ChatMessage(
                    role = MessageRole.USER,
                    content = userMessage
                )
                chatRepository.insertMessage(userChatMessage)
                
                // Получаем историю для контекста
                val history = chatRepository.getLastMessages(50)
                
                // Отправляем запрос в API
                val response = apiService.sendMessage(userMessage, history)
                
                // Сохраняем ответ ассистента
                val assistantMessage = ChatMessage(
                    role = MessageRole.ASSISTANT,
                    content = response
                )
                chatRepository.insertMessage(assistantMessage)
                
                Result.success(response)
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка при отправке сообщения (fallback метод)", e)
                Result.failure(e)
            }
        }
    }
}
