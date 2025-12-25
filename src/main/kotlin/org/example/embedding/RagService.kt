package org.example.embedding

/**
 * RAG (Retrieval-Augmented Generation) сервис для поиска релевантного контекста
 * из базы документов на основе запроса пользователя.
 */
class RagService(
    private val embeddingClient: EmbeddingClient,
    private val documentStorage: DocumentIndexStorage
) {
    companion object {
        // Количество релевантных чанков для возврата
        private const val DEFAULT_LIMIT = 5
        // Минимальный порог сходства (0.0 - 1.0)
        private const val DEFAULT_MIN_SIMILARITY = 0.3
    }
    
    /**
     * Проверяет, есть ли документы в индексе.
     */
    fun hasDocuments(): Boolean {
        return documentStorage.getAllDocuments().isNotEmpty()
    }
    
    /**
     * Ищет релевантный контекст в базе документов для заданного запроса.
     * @param query Текст запроса пользователя
     * @param limit Максимальное количество результатов
     * @param minSimilarity Минимальный порог сходства
     * @return Список найденных релевантных чанков с информацией об источниках
     */
    suspend fun searchContext(
        query: String,
        limit: Int = DEFAULT_LIMIT,
        minSimilarity: Double = DEFAULT_MIN_SIMILARITY
    ): List<RagContext> {
        if (!hasDocuments()) {
            return emptyList()
        }
        
        try {
            // Генерируем эмбеддинг для запроса
            val queryEmbedding = embeddingClient.generateEmbedding(query)
            
            // Ищем похожие чанки в базе
            val searchResults = documentStorage.searchSimilar(
                queryEmbedding = queryEmbedding,
                limit = limit,
                minSimilarity = minSimilarity
            )
            
            // Преобразуем результаты в RagContext
            return searchResults.map { result ->
                RagContext(
                    text = result.text,
                    source = result.source,
                    title = result.title,
                    similarity = result.similarity,
                    documentId = result.documentId,
                    chunkIndex = result.chunkIndex
                )
            }
        } catch (e: Exception) {
            // В случае ошибки возвращаем пустой список
            // Логирование ошибки можно добавить при необходимости
            return emptyList()
        }
    }
    
    /**
     * Форматирует найденный контекст для включения в промпт.
     */
    fun formatContextForPrompt(contexts: List<RagContext>): String {
        if (contexts.isEmpty()) {
            return ""
        }
        
        val contextParts = contexts.mapIndexed { index, context ->
            val sourceInfo = if (context.title != null) {
                "${context.title} (${context.source})"
            } else {
                context.source
            }
            """
            [Источник ${index + 1}: $sourceInfo]
            ${context.text}
            """.trimIndent()
        }
        
        return """
        
        === РЕЛЕВАНТНЫЙ КОНТЕКСТ ИЗ БАЗЫ ДОКУМЕНТОВ ===
        ${contextParts.joinToString("\n\n")}
        === КОНЕЦ КОНТЕКСТА ===
        """.trimIndent()
    }
}

/**
 * Представляет релевантный контекст из базы документов.
 */
data class RagContext(
    val text: String,
    val source: String,
    val title: String?,
    val similarity: Double,
    val documentId: String,
    val chunkIndex: Int
)
