package org.example.embedding

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * RAG (Retrieval-Augmented Generation) сервис для поиска информации в документации проекта.
 * Использует векторный поиск для нахождения релевантных фрагментов документации.
 */
class RagService(
    private val embeddingClient: EmbeddingClient,
    private val storage: DocumentIndexStorage,
    private val indexer: DocumentIndexer = DocumentIndexer(embeddingClient, storage)
) {
    /**
     * Поиск релевантной информации в документации по текстовому запросу.
     * @param query Текстовый запрос
     * @param limit Количество результатов
     * @param minSimilarity Минимальный порог сходства (0.0 - 1.0)
     * @return Список найденных фрагментов документации
     */
    suspend fun search(
        query: String,
        limit: Int = 10,
        minSimilarity: Double = 0.7
    ): List<SearchResult> = withContext(Dispatchers.IO) {
        try {
            indexer.search(query, limit, minSimilarity)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Проверяет, есть ли проиндексированные документы.
     */
    fun hasDocuments(): Boolean {
        return storage.getAllDocuments().isNotEmpty()
    }

    /**
     * Получает все проиндексированные документы.
     */
    fun getAllDocuments(): List<Document> {
        return storage.getAllDocuments()
    }

    /**
     * Получает количество проиндексированных документов.
     */
    fun getDocumentCount(): Int {
        return storage.getAllDocuments().size
    }
}
