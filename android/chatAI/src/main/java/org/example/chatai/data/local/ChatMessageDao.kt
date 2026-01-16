package org.example.chatai.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import org.example.chatai.data.model.ChatMessage

/**
 * Data Access Object для работы с сообщениями чата.
 * Использует Flow для реактивного получения данных.
 */
@Dao
interface ChatMessageDao {
    
    /**
     * Получить все сообщения отсортированные по времени
     */
    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun getAllMessages(): Flow<List<ChatMessage>>
    
    /**
     * Получить сообщения с определенной ролью
     */
    @Query("SELECT * FROM chat_messages WHERE role = :role ORDER BY timestamp ASC")
    fun getMessagesByRole(role: String): Flow<List<ChatMessage>>
    
    /**
     * Получить последние N сообщений
     */
    @Query("SELECT * FROM chat_messages ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getLastMessages(limit: Int): List<ChatMessage>
    
    /**
     * Вставить сообщение
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessage): Long
    
    /**
     * Вставить несколько сообщений
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<ChatMessage>): List<Long>
    
    /**
     * Обновить сообщение
     */
    @Update
    suspend fun updateMessage(message: ChatMessage)
    
    /**
     * Удалить сообщение
     */
    @Delete
    suspend fun deleteMessage(message: ChatMessage)
    
    /**
     * Удалить все сообщения
     */
    @Query("DELETE FROM chat_messages")
    suspend fun deleteAllMessages()
    
    /**
     * Удалить все сообщения кроме системных
     */
    @Query("DELETE FROM chat_messages WHERE role != 'SYSTEM'")
    suspend fun deleteNonSystemMessages()
    
    /**
     * Получить количество сообщений
     */
    @Query("SELECT COUNT(*) FROM chat_messages")
    suspend fun getMessageCount(): Int
}
