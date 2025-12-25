package org.example.embedding

import kotlinx.coroutines.runBlocking
import org.example.config.AppConfig

/**
 * Утилита для тестирования поиска по индексу.
 * 
 * Использование:
 * gradlew runSearchTest
 */
fun main() = runBlocking {
    println("🔍 Тестирование поиска по индексу документов...\n")
    
    // Проверяем наличие базы данных
    val storage = DocumentIndexStorage()
    val allDocs = storage.getAllDocuments()
    
    if (allDocs.isEmpty()) {
        println("❌ Индекс пуст! Сначала запустите индексацию:")
        println("   gradlew runIndexDocs")
        return@runBlocking
    }
    
    println("📚 Найдено документов в индексе: ${allDocs.size}")
    allDocs.forEach { doc ->
        val chunks = storage.getDocumentChunks(doc.id)
        println("   - ${doc.title ?: doc.source}: ${chunks.size} чанков")
    }
    println()
    
    // Загружаем API ключ для генерации эмбеддингов запросов
    val apiKey = try {
        AppConfig.loadApiKey()
    } catch (e: Exception) {
        println("❌ Ошибка загрузки API ключа: ${e.message}")
        return@runBlocking
    }
    
    val embeddingClient = EmbeddingClient(apiKey)
    val indexer = DocumentIndexer(embeddingClient, storage)
    
    try {
        // Тестовые запросы
        val testQueries = listOf(
            "MCP stdio transport",
            "Android emulator",
            "Kotlin Coroutines",
            "архитектура проекта",
            "документация"
        )
        
        println("🔎 Выполняю поиск по тестовым запросам...\n")
        
        for (query in testQueries) {
            println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            println("📝 Запрос: \"$query\"")
            println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            
            val results = indexer.search(query, limit = 3, minSimilarity = 0.5)
            
            if (results.isEmpty()) {
                println("   ❌ Результаты не найдены (порог сходства слишком высокий)")
            } else {
                results.forEachIndexed { index, result ->
                    println("\n   ${index + 1}. Сходство: ${String.format("%.3f", result.similarity)}")
                    println("      Источник: ${result.source}")
                    println("      Чанк #${result.chunkIndex}")
                    println("      Текст: ${result.text.take(150)}${if (result.text.length > 150) "..." else ""}")
                }
            }
            println()
        }
        
        println("✅ Тестирование завершено!")
        
    } catch (e: Exception) {
        println("❌ Ошибка при поиске: ${e.message}")
        e.printStackTrace()
    } finally {
        embeddingClient.close()
    }
}



