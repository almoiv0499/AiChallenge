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
    
    // Путь к документу
    val docPath = File("docs/rag_test.md")
    if (!docPath.exists()) {
        println("❌ Файл не найден: ${docPath.absolutePath}")
        return@runBlocking
    }
    
    // Читаем документ
    println("📖 Чтение документа: ${docPath.absolutePath}")
    val documentText = docPath.readText(Charsets.UTF_8)
    
    if (documentText.isBlank()) {
        println("❌ Документ пуст")
        return@runBlocking
    }
    
    println("📊 Размер документа: ${documentText.length} символов")
    
    // Инициализируем компоненты
    val embeddingClient = EmbeddingClient(apiKey)
    val storage = DocumentIndexStorage()
    val indexer = DocumentIndexer(embeddingClient, storage)
    
    try {
        // Генерируем уникальный ID для документа
        val documentId = "docs_md_${UUID.randomUUID().toString().take(8)}"
        
        // Индексируем документ
        val chunkCount = indexer.indexDocument(
            documentId = documentId,
            text = documentText,
            source = docPath.absolutePath,
            title = "Documentation",
            metadata = mapOf(
                "file" to docPath.name,
                "path" to docPath.absolutePath
            )
        )
        
        println("\n✅ Индексация завершена успешно!")
        println("   Документ ID: $documentId")
        println("   Чанков проиндексировано: $chunkCount")
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

