package org.example.chatai.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Роль сообщения в чате.
 * Поддерживает роли: system, user, assistant, tool, mcp
 */
enum class MessageRole {
    SYSTEM,
    USER,
    ASSISTANT,
    TOOL,
    MCP
}

/**
 * Сущность сообщения чата для Room.
 * Хранит историю всех сообщений с различными ролями.
 */
@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    /**
     * Роль сообщения: system, user, assistant, tool, mcp
     */
    val role: MessageRole,
    
    /**
     * Содержимое сообщения
     */
    val content: String,
    
    /**
     * Временная метка создания сообщения (Unix timestamp в миллисекундах)
     */
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * Преобразует роль в строковое представление для API
     */
    fun roleToString(): String = role.name.lowercase()
    
    companion object {
        /**
         * Создает сообщение с ролью из строки
         */
        fun fromRoleString(roleString: String, content: String, timestamp: Long = System.currentTimeMillis()): ChatMessage {
            val role = when (roleString.lowercase()) {
                "system" -> MessageRole.SYSTEM
                "user" -> MessageRole.USER
                "assistant" -> MessageRole.ASSISTANT
                "tool" -> MessageRole.TOOL
                "mcp" -> MessageRole.MCP
                else -> MessageRole.USER
            }
            return ChatMessage(role = role, content = content, timestamp = timestamp)
        }
    }
}
