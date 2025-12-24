package org.example.embedding

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Сервис для RAG (Retrieval-Augmented Generation).
 * Выполняет поиск по локальному индексу документов и предоставляет релевантный контекст.
 * Поддерживает reranking для улучшения качества результатов.
 */
class RagService(
    private val embeddingClient: EmbeddingClient,
    private val storage: DocumentIndexStorage,
    private val minSimilarity: Double = 0.6,
    private val maxChunks: Int = 3,
    private val reranker: RelevanceReranker? = null,
    private val useReranker: Boolean = true
) {
    /**
     * Ищет релевантные документы по запросу пользователя с применением reranker (если включен).
     * @param query Текст запроса пользователя
     * @return Список релевантных чанков с контекстом или null, если ничего не найдено
     */
    suspend fun searchRelevantContext(query: String): String? = withContext(Dispatchers.IO) {
        try {
            // Генерируем эмбеддинг для запроса
            val queryEmbedding = embeddingClient.generateEmbedding(query)
            
            // Ищем похожие чанки (берем больше результатов для reranking)
            // Используем низкий minSimilarity для получения большего пула кандидатов
            val initialLimit = if (useReranker && reranker != null) maxChunks * 2 else maxChunks
            val initialResults = storage.searchSimilar(
                queryEmbedding, 
                limit = initialLimit, 
                minSimilarity = 0.0 // Низкий порог для получения большего пула кандидатов
            )
            
            if (initialResults.isEmpty()) {
                return@withContext null
            }
            
            println("🔍 Начальный поиск: найдено ${initialResults.size} результатов")
            if (initialResults.isNotEmpty()) {
                val initialSimilarities = initialResults.map { it.similarity }
                println("   Сходство всех результатов: мин=${String.format("%.3f", initialSimilarities.minOrNull() ?: 0.0)}, " +
                        "макс=${String.format("%.3f", initialSimilarities.maxOrNull() ?: 0.0)}, " +
                        "среднее=${String.format("%.3f", initialSimilarities.average())}")
            }
            
            // Применяем reranker, если он включен
            val finalResults = if (useReranker && reranker != null) {
                val threshold = reranker.getThreshold()
                println("   Применяем фильтр reranker с порогом: ${String.format("%.3f", threshold)}")
                val reranked = reranker.rerank(query, initialResults)
                val filtered = reranked.map { it.result }
                
                println("✅ С фильтром reranker:")
                println("   Отфильтровано: ${initialResults.size} → ${filtered.size} результатов")
                if (filtered.isNotEmpty()) {
                    val filteredSimilarities = filtered.map { it.similarity }
                    println("   Сходство отфильтрованных: мин=${String.format("%.3f", filteredSimilarities.minOrNull() ?: 0.0)}, " +
                            "макс=${String.format("%.3f", filteredSimilarities.maxOrNull() ?: 0.0)}, " +
                            "среднее=${String.format("%.3f", filteredSimilarities.average())}")
                }
                filtered
            } else {
                val topResults = initialResults.take(maxChunks)
                println("ℹ️ Без фильтра reranker: взято ${topResults.size} результатов")
                topResults
            }
            
            if (finalResults.isEmpty()) {
                println("⚠️ После фильтрации не осталось результатов")
                return@withContext null
            }
            
            // Формируем контекст из найденных чанков
            return@withContext formatContext(finalResults, useReranker)
        } catch (e: Exception) {
            // В случае ошибки просто возвращаем null - агент продолжит работу без RAG
            println("⚠️ Ошибка RAG поиска: ${e.message}")
            return@withContext null
        }
    }
    
    /**
     * Ищет релевантные документы БЕЗ применения reranker (для сравнения).
     * Использует очень низкий minSimilarity (0.0), чтобы получить все результаты без фильтрации.
     * @param query Текст запроса пользователя
     * @return Список релевантных чанков с контекстом или null, если ничего не найдено
     */
    suspend fun searchRelevantContextWithoutReranker(query: String): String? = withContext(Dispatchers.IO) {
        try {
            // Генерируем эмбеддинг для запроса
            val queryEmbedding = embeddingClient.generateEmbedding(query)
            
            // Ищем похожие чанки БЕЗ фильтрации по minSimilarity (используем 0.0)
            // Берем больше результатов для честного сравнения
            val results = storage.searchSimilar(
                queryEmbedding, 
                limit = maxChunks * 2, // Берем больше для сравнения
                minSimilarity = 0.0 // НЕ применяем фильтрацию по сходству
            )
            
            if (results.isEmpty()) {
                return@withContext null
            }
            
            // Берем только топ-N результатов (без reranking)
            // НО: показываем все similarity scores для понимания разницы
            val topResults = results.take(maxChunks)
            
            println("🔍 БЕЗ фильтра reranker:")
            println("   Найдено в БД: ${results.size} результатов (лимит поиска: ${maxChunks * 2})")
            println("   Взято для контекста: ${topResults.size} результатов (без фильтрации)")
            if (topResults.isNotEmpty()) {
                val similarities = topResults.map { it.similarity }
                println("   Сходство взятых результатов:")
                println("      мин=${String.format("%.3f", similarities.minOrNull() ?: 0.0)}, " +
                        "макс=${String.format("%.3f", similarities.maxOrNull() ?: 0.0)}, " +
                        "среднее=${String.format("%.3f", similarities.average())}")
            }
            if (results.size > topResults.size) {
                val notUsed = results.drop(topResults.size)
                val notUsedSimilarities = notUsed.map { it.similarity }
                println("   НЕ использовано: ${notUsed.size} результатов")
                if (notUsedSimilarities.isNotEmpty()) {
                    println("      Сходство неиспользованных: " +
                            "мин=${String.format("%.3f", notUsedSimilarities.minOrNull() ?: 0.0)}, " +
                            "макс=${String.format("%.3f", notUsedSimilarities.maxOrNull() ?: 0.0)}")
                }
            }
            
            // Формируем контекст из найденных чанков
            return@withContext formatContext(topResults, useReranker = false)
        } catch (e: Exception) {
            println("⚠️ Ошибка RAG поиска (без reranker): ${e.message}")
            return@withContext null
        }
    }
    
    /**
     * Форматирует результаты поиска в контекст для LLM.
     */
    private fun formatContext(results: List<SearchResult>, useReranker: Boolean): String {
        val contextBuilder = StringBuilder()
        contextBuilder.append("Релевантная информация из локальной базы знаний")
        if (useReranker) {
            contextBuilder.append(" (с применением фильтра релевантности)")
        }
        contextBuilder.append(":\n\n")
        
        results.forEachIndexed { index, result ->
            contextBuilder.append("[${index + 1}] ")
            if (result.title != null) {
                contextBuilder.append("Источник: ${result.title}\n")
            }
            contextBuilder.append("Сходство: ${String.format("%.3f", result.similarity)}\n")
            contextBuilder.append("Текст: ${result.text}\n")
            contextBuilder.append("\n")
        }
        
        // Добавляем статистику для отладки
        if (results.isNotEmpty()) {
            val avgSimilarity = results.map { it.similarity }.average()
            val minSimilarity = results.minOfOrNull { it.similarity } ?: 0.0
            val maxSimilarity = results.maxOfOrNull { it.similarity } ?: 0.0
            contextBuilder.append("---\n")
            contextBuilder.append("Статистика: ${results.size} чанков, ")
            contextBuilder.append("сходство: мин=${String.format("%.3f", minSimilarity)}, ")
            contextBuilder.append("макс=${String.format("%.3f", maxSimilarity)}, ")
            contextBuilder.append("среднее=${String.format("%.3f", avgSimilarity)}\n")
        }
        
        contextBuilder.append("---\n")
        contextBuilder.append("Используй эту информацию для ответа на вопрос пользователя. ")
        contextBuilder.append("Если информация из базы знаний не полностью отвечает на вопрос, ")
        contextBuilder.append("дополни ответ своими знаниями, но приоритет отдавай информации из базы знаний.\n")
        
        return contextBuilder.toString()
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
    
    /**
     * Включает/выключает использование reranker.
     */
    fun setRerankerEnabled(enabled: Boolean): RagService {
        return RagService(
            embeddingClient = embeddingClient,
            storage = storage,
            minSimilarity = minSimilarity,
            maxChunks = maxChunks,
            reranker = reranker,
            useReranker = enabled
        )
    }
    
    /**
     * Обновляет порог фильтрации reranker.
     */
    fun updateRerankerThreshold(newThreshold: Double): RagService {
        val updatedReranker = reranker?.updateThreshold(newThreshold)
        return RagService(
            embeddingClient = embeddingClient,
            storage = storage,
            minSimilarity = minSimilarity,
            maxChunks = maxChunks,
            reranker = updatedReranker,
            useReranker = useReranker
        )
    }
    
    /**
     * Получает текущий порог reranker.
     */
    fun getRerankerThreshold(): Double? {
        return reranker?.getThreshold()
    }
}


