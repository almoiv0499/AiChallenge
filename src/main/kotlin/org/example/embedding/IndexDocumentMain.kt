package org.example.embedding

import kotlinx.coroutines.runBlocking
import org.example.config.AppConfig
import java.io.File
import java.util.UUID

/**
 * Утилита для индексации документа docs.md.
 * 
 * Использование:
 * - Убедитесь, что OPENROUTER_API_KEY установлен в local.properties или переменных окружения
 * - Запустите: gradlew runIndexDocs
 */
fun main() = runBlocking {
    
    println("🚀 Запуск индексации документа...")
    
    // Загружаем API ключ
    val apiKey = try {
        AppConfig.loadApiKey()
    } catch (e: Exception) {
        println("❌ Ошибка: ${e.message}")
        return@runBlocking
    }
    
    // Список документов для индексации
    val documentsToIndex = listOf(
        "docs/rag_index_data.md" to "OpenRouter Agent - Основная документация",
        "docs/kotlin_programming_guide.md" to "Kotlin Programming Guide - Руководство по Kotlin",
        "docs/web_development_basics.md" to "Web Development Basics - Основы веб-разработки",
        "docs/machine_learning_intro.md" to "Machine Learning Introduction - Введение в машинное обучение"
    )
    
    // Инициализируем компоненты
    val embeddingClient = EmbeddingClient(apiKey)
    val storage = DocumentIndexStorage()
    val indexer = DocumentIndexer(embeddingClient, storage)
    
    var totalChunks = 0
    var indexedCount = 0
    
    try {
        for ((docPathStr, docTitle) in documentsToIndex) {
            val docPath = File(docPathStr)
            if (!docPath.exists()) {
                println("⚠️ Файл не найден: ${docPath.absolutePath}, пропускаем...")
                continue
            }
            
            // Читаем документ
            println("\n📖 Чтение документа: ${docPath.absolutePath}")
            val documentText = docPath.readText(Charsets.UTF_8)
            
            if (documentText.isBlank()) {
                println("⚠️ Документ пуст, пропускаем...")
                continue
            }
            
            println("📊 Размер документа: ${documentText.length} символов")
            
            // Генерируем уникальный ID для документа
            val documentId = "${docPath.nameWithoutExtension}_${UUID.randomUUID().toString().take(8)}"
            
            // Индексируем документ
            val chunkCount = indexer.indexDocument(
                documentId = documentId,
                text = documentText,
                source = docPath.absolutePath,
                title = docTitle,
                metadata = mapOf(
                    "file" to docPath.name,
                    "path" to docPath.absolutePath,
                    "title" to docTitle
                )
            )
            
            totalChunks += chunkCount
            indexedCount++
            println("✅ Проиндексировано: $chunkCount чанков (ID: $documentId)")
        }
        
        println("\n✅ Индексация завершена успешно!")
        println("   Документов проиндексировано: $indexedCount")
        println("   Всего чанков: $totalChunks")
        println("   База данных: document_index.db")
        
        // Показываем статистику
        val allDocs = storage.getAllDocuments()
        println("\n📚 Всего документов в индексе: ${allDocs.size}")
        allDocs.forEach { doc ->
            val chunks = storage.getDocumentChunks(doc.id)
            println("   - ${doc.title ?: doc.source}: ${chunks.size} чанков")
        }
        
    } catch (e: Exception) {
        println("❌ Ошибка индексации: ${e.message}")
        e.printStackTrace()
    } finally {
        embeddingClient.close()
    }
}

