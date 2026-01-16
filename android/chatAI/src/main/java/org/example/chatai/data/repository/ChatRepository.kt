package org.example.chatai.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.example.chatai.data.local.ChatMessageDao
import org.example.chatai.data.model.ChatMessage
import org.example.chatai.data.model.MessageRole

/**
 * Репозиторий для управления сообщениями чата.
 * Инкапсулирует логику работы с данными и предоставляет чистый API.
 */
class ChatRepository(
    private val chatMessageDao: ChatMessageDao
) {
    
    /**
     * Получить все сообщения как Flow для реактивного UI
     */
    fun getAllMessages(): Flow<List<ChatMessage>> {
        return chatMessageDao.getAllMessages()
    }
    
    /**
     * Получить сообщения определенной роли
     */
    fun getMessagesByRole(role: MessageRole): Flow<List<ChatMessage>> {
        return chatMessageDao.getMessagesByRole(role.name)
    }
    
    /**
     * Получить последние N сообщений
     */
    suspend fun getLastMessages(limit: Int): List<ChatMessage> {
        return chatMessageDao.getLastMessages(limit)
    }
    
    /**
     * Вставить новое сообщение
     */
    suspend fun insertMessage(message: ChatMessage): Long {
        return chatMessageDao.insertMessage(message)
    }
    
    /**
     * Вставить несколько сообщений (например, при восстановлении истории)
     */
    suspend fun insertMessages(messages: List<ChatMessage>) {
        chatMessageDao.insertMessages(messages)
    }
    
    /**
     * Обновить сообщение
     */
    suspend fun updateMessage(message: ChatMessage) {
        chatMessageDao.updateMessage(message)
    }
    
    /**
     * Удалить сообщение
     */
    suspend fun deleteMessage(message: ChatMessage) {
        chatMessageDao.deleteMessage(message)
    }
    
    /**
     * Очистить всю историю чата
     */
    suspend fun clearAllMessages() {
        chatMessageDao.deleteAllMessages()
    }
    
    /**
     * Очистить историю чата, сохранив системные сообщения
     */
    suspend fun clearUserMessages() {
        chatMessageDao.deleteNonSystemMessages()
    }
    
    /**
     * Получить количество сообщений
     */
    suspend fun getMessageCount(): Int {
        return chatMessageDao.getMessageCount()
    }
    
    /**
     * Восстановить историю из списка сообщений.
     * Используется при инициализации приложения.
     */
    suspend fun restoreHistory(messages: List<ChatMessage>) {
        clearAllMessages()
        insertMessages(messages)
    }
}
