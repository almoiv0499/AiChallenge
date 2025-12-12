package org.example.storage

import org.example.ui.ConsoleUI
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException

class HistoryStorage(private val dbPath: String = "conversation_history.db") {
    private val dbFile = File(dbPath)
    
    init {
        loadDriver()
        initializeDatabase()
    }
    
    private fun loadDriver() {
        try {
            Class.forName("org.sqlite.JDBC")
        } catch (e: ClassNotFoundException) {
            val errorMessage = """
                ❌ SQLite JDBC драйвер не найден в classpath.
                
                РЕШЕНИЕ:
                
                1. Пересоберите проект через командную строку:
                   .\gradlew.bat clean build
                   
                2. Или в IntelliJ IDEA:
                   - Откройте Gradle панель (View -> Tool Windows -> Gradle)
                   - Нажмите на иконку обновления (Refresh Gradle Project)
                   - Выполните: Build -> Rebuild Project
                   
                3. Проверьте, что зависимость загружена:
                   .\gradlew.bat dependencies --configuration runtimeClasspath | findstr sqlite
                   
                4. После пересборки запустите снова:
                   .\gradlew.bat run
                   
                Зависимость уже добавлена в build.gradle.kts:
                implementation("org.xerial:sqlite-jdbc:3.44.1.0")
            """.trimIndent()
            throw RuntimeException(errorMessage, e)
        }
    }
    
    private fun getConnection(): Connection {
        val url = "jdbc:sqlite:$dbPath"
        return try {
            DriverManager.getConnection(url)
        } catch (e: Exception) {
            throw RuntimeException("Не удалось подключиться к БД: ${e.message}. Убедитесь, что SQLite JDBC драйвер установлен.", e)
        }
    }
    
    private fun initializeDatabase() {
        try {
            getConnection().use { connection ->
                connection.createStatement().execute("""
                    CREATE TABLE IF NOT EXISTS conversation_summaries (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        summary TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        message_count INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                ConsoleUI.printDatabaseInitialized(dbPath)
            }
        } catch (e: SQLException) {
            ConsoleUI.printDatabaseError("Ошибка инициализации БД: ${e.message}")
            throw RuntimeException("Не удалось инициализировать базу данных", e)
        }
    }
    
    fun saveSummary(summary: String, messageCount: Int): Long? {
        return try {
            getConnection().use { connection ->
                val sql = "INSERT INTO conversation_summaries (summary, created_at, message_count) VALUES (?, ?, ?)"
                val statement = connection.prepareStatement(sql)
                statement.setString(1, summary)
                statement.setLong(2, System.currentTimeMillis() / 1000)
                statement.setInt(3, messageCount)
                statement.executeUpdate()
                val idResult = connection.createStatement().executeQuery("SELECT last_insert_rowid() as id")
                if (idResult.next()) {
                    val id = idResult.getLong("id")
                    ConsoleUI.printSummarySaved(id)
                    id
                } else {
                    null
                }
            }
        } catch (e: SQLException) {
            ConsoleUI.printDatabaseError("Ошибка сохранения summary: ${e.message}")
            null
        }
    }
    
    fun getLatestSummary(): ConversationSummary? {
        return try {
            getConnection().use { connection ->
                val sql = "SELECT id, summary, created_at, message_count FROM conversation_summaries ORDER BY created_at DESC LIMIT 1"
                val statement = connection.prepareStatement(sql)
                val resultSet = statement.executeQuery()
                if (resultSet.next()) {
                    ConversationSummary(
                        id = resultSet.getLong("id"),
                        summary = resultSet.getString("summary"),
                        createdAt = resultSet.getLong("created_at"),
                        messageCount = resultSet.getInt("message_count")
                    )
                } else {
                    null
                }
            }
        } catch (e: SQLException) {
            ConsoleUI.printDatabaseError("Ошибка загрузки summary: ${e.message}")
            null
        }
    }
    
    fun clearAllSummaries(): Boolean {
        return try {
            getConnection().use { connection ->
                val sql = "DELETE FROM conversation_summaries"
                val statement = connection.prepareStatement(sql)
                val deleted = statement.executeUpdate()
                ConsoleUI.printDatabaseCleared(deleted)
                true
            }
        } catch (e: SQLException) {
            ConsoleUI.printDatabaseError("Ошибка очистки БД: ${e.message}")
            false
        }
    }
    
    fun getSummaryCount(): Int {
        return try {
            getConnection().use { connection ->
                val sql = "SELECT COUNT(*) as count FROM conversation_summaries"
                val statement = connection.prepareStatement(sql)
                val resultSet = statement.executeQuery()
                if (resultSet.next()) {
                    resultSet.getInt("count")
                } else {
                    0
                }
            }
        } catch (e: SQLException) {
            ConsoleUI.printDatabaseError("Ошибка подсчета summary: ${e.message}")
            0
        }
    }
}

