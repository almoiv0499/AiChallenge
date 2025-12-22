package org.example.embedding

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Сервис для RAG (Retrieval-Augmented Generation).
 * Выполняет поиск по локальному индексу документов и предоставляет релевантный контекст.
 */
class RagService(
    private val embeddingClient: EmbeddingClient,
    private val storage: DocumentIndexStorage,
    private val minSimilarity: Double = 0.6,
    private val maxChunks: Int = 3
) {
    /**
     * Ищет релевантные документы по запросу пользователя.
     * @param query Текст запроса пользователя
     * @return Список релевантных чанков с контекстом или null, если ничего не найдено
     */
    suspend fun searchRelevantContext(query: String): String? = withContext(Dispatchers.IO) {
        try {
            // Генерируем эмбеддинг для запроса
            val queryEmbedding = embeddingClient.generateEmbedding(query)
            
            // Ищем похожие чанки
            val results = storage.searchSimilar(queryEmbedding, limit = maxChunks, minSimilarity = minSimilarity)
            
            if (results.isEmpty()) {
                return@withContext null
            }
            
            // Формируем контекст из найденных чанков
            val contextBuilder = StringBuilder()
            contextBuilder.append("Релевантная информация из локальной базы знаний:\n\n")
            
            results.forEachIndexed { index, result ->
                contextBuilder.append("[${index + 1}] ")
                if (result.title != null) {
                    contextBuilder.append("Источник: ${result.title}\n")
                }
                contextBuilder.append("Сходство: ${String.format("%.2f", result.similarity)}\n")
                contextBuilder.append("Текст: ${result.text}\n")
                contextBuilder.append("\n")
            }
            
            contextBuilder.append("---\n")
            contextBuilder.append("Используй эту информацию для ответа на вопрос пользователя. ")
            contextBuilder.append("Если информация из базы знаний не полностью отвечает на вопрос, ")
            contextBuilder.append("дополни ответ своими знаниями, но приоритет отдавай информации из базы знаний.\n")
            
            return@withContext contextBuilder.toString()
        } catch (e: Exception) {
            // В случае ошибки просто возвращаем null - агент продолжит работу без RAG
            println("⚠️ Ошибка RAG поиска: ${e.message}")
            return@withContext null
        }
    }
    
    /**
     * Проверяет, есть ли документы в индексе.
     */
    fun hasDocuments(): Boolean {
        return try {
            storage.getAllDocuments().isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }
}

