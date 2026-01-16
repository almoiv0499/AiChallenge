package org.example.chatai.domain.usecase

import kotlinx.coroutines.flow.Flow
import org.example.chatai.data.model.ChatMessage
import org.example.chatai.data.repository.ChatRepository

/**
 * Use case для загрузки истории чата.
 * Предоставляет реактивный Flow для UI.
 */
class LoadChatHistoryUseCase(
    private val chatRepository: ChatRepository
) {
    /**
     * Получить поток всех сообщений для отображения в UI
     */
    fun execute(): Flow<List<ChatMessage>> {
        return chatRepository.getAllMessages()
    }
}
