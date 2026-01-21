package org.example.chat

import org.example.storage.DatabasePathHelper
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException

/**
 * Хранилище истории диалога для чат-сервера
 * Использует SQLite для хранения сообщений
 */
class ChatHistoryStorage(private val dbPath: String = DatabasePathHelper.getDbPath("chat_history.db")) {
    private val dbFile = File(dbPath)
    
    init {
        loadDriver()
        initializeDatabase()
    }
    
    private fun loadDriver() {
        try {
            Class.forName("org.sqlite.JDBC")
        } catch (e: ClassNotFoundException) {
            throw RuntimeException("SQLite JDBC драйвер не найден в classpath", e)
        }
    }
    
    private fun getConnection(): Connection {
        val url = "jdbc:sqlite:$dbPath"
        return try {
            DriverManager.getConnection(url)
        } catch (e: Exception) {
            throw RuntimeException("Не удалось подключиться к БД: ${e.message}", e)
        }
    }
    
    private fun initializeDatabase() {
        try {
            getConnection().use { connection ->
                // Таблица для хранения сообщений
                connection.createStatement().execute("""
                    CREATE TABLE IF NOT EXISTS messages (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        role TEXT NOT NULL,
                        content TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        session_id TEXT DEFAULT 'default'
                    )
                """.trimIndent())
                
                // Индекс для быстрого поиска по сессии
                connection.createStatement().execute("""
                    CREATE INDEX IF NOT EXISTS idx_session_id ON messages(session_id)
                """.trimIndent())
                
                // Индекс для сортировки по времени
                connection.createStatement().execute("""
                    CREATE INDEX IF NOT EXISTS idx_created_at ON messages(created_at)
                """.trimIndent())
            }
        } catch (e: SQLException) {
            throw RuntimeException("Не удалось инициализировать базу данных", e)
        }
    }
    
    /**
     * Сохраняет сообщение в историю
     */
    fun saveMessage(role: String, content: String, sessionId: String = "default"): Long? {
        return try {
            getConnection().use { connection ->
                val sql = "INSERT INTO messages (role, content, created_at, session_id) VALUES (?, ?, ?, ?)"
                val statement = connection.prepareStatement(sql)
                statement.setString(1, role)
                statement.setString(2, content)
                statement.setLong(3, System.currentTimeMillis())
                statement.setString(4, sessionId)
                statement.executeUpdate()
                
                val idResult = connection.createStatement().executeQuery("SELECT last_insert_rowid() as id")
                if (idResult.next()) {
                    idResult.getLong("id")
                } else {
                    null
                }
            }
        } catch (e: SQLException) {
            println("⚠️ Ошибка сохранения сообщения: ${e.message}")
            null
        }
    }
    
    /**
     * Получает историю диалога для сессии
     */
    fun getHistory(sessionId: String = "default", limit: Int = 100): List<ChatMessage> {
        return try {
            getConnection().use { connection ->
                val sql = """
                    SELECT id, role, content, created_at 
                    FROM messages 
                    WHERE session_id = ? 
                    ORDER BY created_at ASC 
                    LIMIT ?
                """.trimIndent()
                val statement = connection.prepareStatement(sql)
                statement.setString(1, sessionId)
                statement.setInt(2, limit)
                val resultSet = statement.executeQuery()
                
                val messages = mutableListOf<ChatMessage>()
                while (resultSet.next()) {
                    messages.add(
                        ChatMessage(
                            id = resultSet.getLong("id"),
                            role = resultSet.getString("role"),
                            content = resultSet.getString("content"),
                            createdAt = resultSet.getLong("created_at")
                        )
                    )
                }
                messages
            }
        } catch (e: SQLException) {
            println("⚠️ Ошибка загрузки истории: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Очищает историю для сессии
     */
    fun clearHistory(sessionId: String = "default"): Boolean {
        return try {
            getConnection().use { connection ->
                val sql = "DELETE FROM messages WHERE session_id = ?"
                val statement = connection.prepareStatement(sql)
                statement.setString(1, sessionId)
                val deleted = statement.executeUpdate()
                deleted > 0
            }
        } catch (e: SQLException) {
            println("⚠️ Ошибка очистки истории: ${e.message}")
            false
        }
    }
    
    /**
     * Получает количество сообщений в сессии
     */
    fun getMessageCount(sessionId: String = "default"): Int {
        return try {
            getConnection().use { connection ->
                val sql = "SELECT COUNT(*) as count FROM messages WHERE session_id = ?"
                val statement = connection.prepareStatement(sql)
                statement.setString(1, sessionId)
                val resultSet = statement.executeQuery()
                if (resultSet.next()) {
                    resultSet.getInt("count")
                } else {
                    0
                }
            }
        } catch (e: SQLException) {
            println("⚠️ Ошибка подсчета сообщений: ${e.message}")
            0
        }
    }
}

/**
 * Модель сообщения для истории
 */
data class ChatMessage(
    val id: Long,
    val role: String,
    val content: String,
    val createdAt: Long
)
