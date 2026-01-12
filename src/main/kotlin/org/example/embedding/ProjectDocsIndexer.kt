package org.example.embedding

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.example.config.AppConfig
import java.io.File
import java.util.UUID

/**
 * Утилита для автоматической индексации документации проекта.
 * Индексирует README, API документацию, схемы данных и другие документы.
 */
class ProjectDocsIndexer(
    private val embeddingClient: EmbeddingClient,
    private val storage: DocumentIndexStorage,
    private val indexer: DocumentIndexer
) {
    companion object {
        /**
         * Создает индексатор документации проекта.
         */
        suspend fun create(): ProjectDocsIndexer? {
            return try {
                val apiKey = AppConfig.loadApiKey()
                val embeddingClient = EmbeddingClient(apiKey)
                val storage = DocumentIndexStorage()
                val indexer = DocumentIndexer(embeddingClient, storage)
                ProjectDocsIndexer(embeddingClient, storage, indexer)
            } catch (e: Exception) {
                println("⚠️ Failed to create ProjectDocsIndexer: ${e.message}")
                null
            }
        }
    }

    /**
     * Индексирует только OpenRouterAgent.kt
     * @return Количество проиндексированных документов
     */
    suspend fun indexProjectDocumentation(): Int = withContext(Dispatchers.IO) {
        val file = File("src/main/kotlin/org/example/agent/OpenRouterAgent.kt")
        
        if (!file.exists()) {
            println("❌ Файл OpenRouterAgent.kt не найден")
            return@withContext 0
        }
        
        try {
            val content = file.readText(Charsets.UTF_8)
            
            // Индексируем файл по частям для лучшего поиска
            val chunks = splitIntoChunks(content)
            
            chunks.forEachIndexed { index, chunk ->
                val documentId = "agent_chunk_${index}_${UUID.randomUUID().toString().take(8)}"
                indexer.indexDocument(
                    documentId = documentId,
                    text = chunk.text,
                    source = "OpenRouterAgent.kt",
                    title = chunk.title,
                    metadata = mapOf(
                        "file" to "OpenRouterAgent.kt",
                        "type" to "source_code",
                        "chunk" to index.toString(),
                        "lines" to "${chunk.startLine}-${chunk.endLine}"
                    )
                )
            }
            
            println("✅ Проиндексирован OpenRouterAgent.kt (${chunks.size} частей)")
            return@withContext chunks.size
            
        } catch (e: Exception) {
            println("❌ Ошибка индексации: ${e.message}")
            return@withContext 0
        }
    }
    
    /**
     * Разбивает код на логические части для индексации
     */
    private fun splitIntoChunks(content: String): List<CodeChunk> {
        val chunks = mutableListOf<CodeChunk>()
        val lines = content.lines()
        
        // 1. Общее описание файла (первые 50 строк)
        chunks.add(CodeChunk(
            title = "OpenRouterAgent - Общее описание",
            text = """
                OpenRouterAgent.kt - главный агент для обработки сообщений пользователя.
                
                Назначение: Обрабатывает сообщения пользователя, вызывает инструменты (tools), 
                управляет историей диалога и взаимодействует с OpenRouter API.
                
                Основные компоненты:
                - processMessage() - обработка входящих сообщений
                - executeAgentLoop() - цикл выполнения агента
                - handleFunctionCall() - вызов инструментов
                - compressHistoryIfNeeded() - сжатие истории диалога
                
                ${lines.take(50).joinToString("\n")}
            """.trimIndent(),
            startLine = 1,
            endLine = 50
        ))
        
        // 2. Класс и конструктор
        val classStart = lines.indexOfFirst { it.contains("class OpenRouterAgent") }
        if (classStart >= 0) {
            chunks.add(CodeChunk(
                title = "OpenRouterAgent - Конструктор и свойства",
                text = lines.subList(classStart, minOf(classStart + 40, lines.size)).joinToString("\n"),
                startLine = classStart + 1,
                endLine = classStart + 40
            ))
        }
        
        // 3. Функция processMessage
        val processStart = lines.indexOfFirst { it.contains("suspend fun processMessage") }
        if (processStart >= 0) {
            chunks.add(CodeChunk(
                title = "OpenRouterAgent - processMessage()",
                text = """
                    Функция processMessage - точка входа для обработки сообщений пользователя.
                    Проверяет на device search запросы, добавляет сообщение в историю,
                    запускает цикл агента.
                    
                    ${extractFunction(lines, processStart)}
                """.trimIndent(),
                startLine = processStart + 1,
                endLine = processStart + 30
            ))
        }
        
        // 4. Функция executeAgentLoop
        val loopStart = lines.indexOfFirst { it.contains("private suspend fun executeAgentLoop") }
        if (loopStart >= 0) {
            chunks.add(CodeChunk(
                title = "OpenRouterAgent - executeAgentLoop()",
                text = """
                    Функция executeAgentLoop - основной цикл выполнения агента.
                    Отправляет запросы к API, обрабатывает ответы, вызывает инструменты.
                    
                    ${extractFunction(lines, loopStart)}
                """.trimIndent(),
                startLine = loopStart + 1,
                endLine = loopStart + 80
            ))
        }
        
        // 5. Функции для парсинга function_call
        val parseStart = lines.indexOfFirst { it.contains("fun tryParseFunctionCallFromText") }
        if (parseStart >= 0) {
            chunks.add(CodeChunk(
                title = "OpenRouterAgent - Парсинг function_call",
                text = """
                    Функции для парсинга function_call из текстового ответа модели.
                    Модель может возвращать JSON с function_call в тексте вместо нативного вызова.
                    
                    ${extractFunction(lines, parseStart)}
                """.trimIndent(),
                startLine = parseStart + 1,
                endLine = parseStart + 60
            ))
        }
        
        // 6. Системный промпт
        val promptStart = lines.indexOfFirst { it.contains("SIMPLE_SYSTEM_PROMPT") }
        if (promptStart >= 0) {
            chunks.add(CodeChunk(
                title = "OpenRouterAgent - Системный промпт",
                text = """
                    SIMPLE_SYSTEM_PROMPT - системный промпт, который определяет поведение агента.
                    Содержит инструкции по использованию инструментов Git, Weather, Notion.
                    
                    ${lines.subList(promptStart, minOf(promptStart + 60, lines.size)).joinToString("\n")}
                """.trimIndent(),
                startLine = promptStart + 1,
                endLine = promptStart + 60
            ))
        }
        
        // 7. Сжатие истории
        val compressStart = lines.indexOfFirst { it.contains("private suspend fun compressHistoryIfNeeded") }
        if (compressStart >= 0) {
            chunks.add(CodeChunk(
                title = "OpenRouterAgent - Сжатие истории диалога",
                text = """
                    Функции для сжатия истории диалога для экономии токенов.
                    Создает резюме старых сообщений и заменяет их на краткое описание.
                    
                    ${extractFunction(lines, compressStart)}
                """.trimIndent(),
                startLine = compressStart + 1,
                endLine = compressStart + 40
            ))
        }
        
        return chunks
    }
    
    /**
     * Извлекает функцию начиная с указанной строки
     */
    private fun extractFunction(lines: List<String>, startIndex: Int): String {
        var braceCount = 0
        var started = false
        val result = mutableListOf<String>()
        
        for (i in startIndex until minOf(lines.size, startIndex + 100)) {
            val line = lines[i]
            result.add(line)
            
            if (line.contains("{")) {
                started = true
                braceCount += line.count { it == '{' }
            }
            if (line.contains("}")) {
                braceCount -= line.count { it == '}' }
            }
            
            if (started && braceCount <= 0) break
        }
        
        return result.joinToString("\n")
    }
    
    /**
     * Чанк кода с метаданными
     */
    private data class CodeChunk(
        val title: String,
        val text: String,
        val startLine: Int,
        val endLine: Int
    )

    /**
     * Проверяет, есть ли уже проиндексированные документы.
     */
    fun hasIndexedDocuments(): Boolean {
        return storage.getAllDocuments().isNotEmpty()
    }

    fun close() {
        embeddingClient.close()
    }
}


