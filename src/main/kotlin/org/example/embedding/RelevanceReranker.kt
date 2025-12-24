package org.example.embedding

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.example.client.OpenRouterClient

/**
 * Стратегия reranking/фильтрации релевантности результатов поиска.
 */
sealed interface RerankingStrategy {
    /**
     * Фильтрация только по порогу сходства (threshold-based).
     */
    data class ThresholdBased(
        val threshold: Double = 0.7
    ) : RerankingStrategy

    /**
     * Комбинированная стратегия: сначала фильтрация по порогу, затем LLM reranking.
     */
    data class Hybrid(
        val threshold: Double = 0.6,
        val llmClient: OpenRouterClient,
        val maxRerankedResults: Int = 3
    ) : RerankingStrategy
}

/**
 * Результат reranking с метаданными.
 */
data class RerankedResult(
    val result: SearchResult,
    val originalRank: Int,
    val rerankedScore: Double? = null,
    val passedFilter: Boolean
)

/**
 * Reranker для фильтрации и переранжирования результатов поиска по релевантности.
 * 
 * Поддерживает два режима:
 * 1. Threshold-based: простая фильтрация по порогу сходства
 * 2. Hybrid: фильтрация + LLM reranking для более точной оценки релевантности
 */
class RelevanceReranker(
    private val strategy: RerankingStrategy = RerankingStrategy.ThresholdBased(threshold = 0.7)
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    /**
     * Применяет reranking к результатам поиска.
     * @param query Оригинальный запрос пользователя
     * @param results Результаты поиска (уже отсортированные по сходству)
     * @return Отфильтрованные и переранжированные результаты
     */
    suspend fun rerank(
        query: String,
        results: List<SearchResult>
    ): List<RerankedResult> = withContext(Dispatchers.IO) {
        when (strategy) {
            is RerankingStrategy.ThresholdBased -> {
                rerankWithThreshold(query, results, strategy.threshold)
            }
            is RerankingStrategy.Hybrid -> {
                rerankWithHybrid(query, results, strategy)
            }
        }
    }

    /**
     * Простая фильтрация по порогу сходства.
     */
    private fun rerankWithThreshold(
        query: String,
        results: List<SearchResult>,
        threshold: Double
    ): List<RerankedResult> {
        val reranked = results.mapIndexed { index, result ->
            val passed = result.similarity >= threshold
            RerankedResult(
                result = result,
                originalRank = index + 1,
                passedFilter = passed
            )
        }
        
        val filtered = reranked.filter { it.passedFilter }
        val filteredOut = reranked.filter { !it.passedFilter }
        val filteredCount = filteredOut.size
        
        if (filteredCount > 0) {
            println("   🔽 Фильтр reranker: отсеяно $filteredCount из ${results.size} результатов (порог: ${String.format("%.3f", threshold)})")
            if (filteredOut.isNotEmpty()) {
                val minFiltered = filteredOut.minOfOrNull { it.result.similarity } ?: 0.0
                val maxFiltered = filteredOut.maxOfOrNull { it.result.similarity } ?: 0.0
                println("      Отсеянные результаты: сходство от ${String.format("%.3f", minFiltered)} до ${String.format("%.3f", maxFiltered)}")
            }
        } else {
            println("   ℹ️ Все ${results.size} результатов прошли фильтр (порог: ${String.format("%.3f", threshold)})")
        }
        
        if (filtered.isNotEmpty()) {
            val minPassed = filtered.minOfOrNull { it.result.similarity } ?: 0.0
            val maxPassed = filtered.maxOfOrNull { it.result.similarity } ?: 0.0
            println("      Прошедшие фильтр: ${filtered.size} результатов, сходство от ${String.format("%.3f", minPassed)} до ${String.format("%.3f", maxPassed)}")
        }
        
        return filtered
    }

    /**
     * Гибридная стратегия: фильтрация + LLM reranking.
     */
    private suspend fun rerankWithHybrid(
        query: String,
        results: List<SearchResult>,
        strategy: RerankingStrategy.Hybrid
    ): List<RerankedResult> {
        // Шаг 1: Предварительная фильтрация по порогу
        val preFiltered = results.mapIndexed { index, result ->
            val passed = result.similarity >= strategy.threshold
            RerankedResult(
                result = result,
                originalRank = index + 1,
                passedFilter = passed
            )
        }.filter { it.passedFilter }

        if (preFiltered.isEmpty()) {
            return emptyList()
        }

        // Шаг 2: LLM reranking для топ-N результатов
        val candidatesForReranking = preFiltered.take(strategy.maxRerankedResults * 2)
        
        return try {
            val reranked = performLlmReranking(query, candidatesForReranking, strategy)
            // Сортируем по новому скору и берем топ-N
            reranked.sortedByDescending { it.rerankedScore ?: it.result.similarity }
                .take(strategy.maxRerankedResults)
        } catch (e: Exception) {
            // В случае ошибки LLM reranking возвращаем результаты с предварительной фильтрацией
            println("⚠️ Ошибка LLM reranking: ${e.message}. Используем предварительную фильтрацию.")
            preFiltered.take(strategy.maxRerankedResults)
        }
    }

    /**
     * Выполняет LLM reranking для оценки релевантности результатов.
     */
    private suspend fun performLlmReranking(
        query: String,
        candidates: List<RerankedResult>,
        strategy: RerankingStrategy.Hybrid
    ): List<RerankedResult> {
        // Формируем промпт для LLM
        val prompt = buildRerankingPrompt(query, candidates)
        
        // Вызываем LLM для оценки релевантности
        val message = org.example.models.OpenRouterInputMessage(
            role = "user",
            content = listOf(
                org.example.models.OpenRouterInputContentItem(
                    type = "input_text",
                    text = prompt
                )
            )
        )
        
        val response = strategy.llmClient.createResponse(
            org.example.models.OpenRouterRequest(
                model = "openai/gpt-4o-mini", // Используем быструю модель для reranking
                input = listOf(
                    json.encodeToJsonElement(
                        org.example.models.OpenRouterInputMessage.serializer(),
                        message
                    )
                ),
                tools = null,
                temperature = 0.0, // Низкая температура для консистентности
                maxTokens = 500.0 // Ограничиваем для reranking (нужен только JSON массив)
            )
        )

        // Парсим ответ LLM
        val scores = parseRerankingResponse(response, candidates.size)
        
        // Присваиваем скоры результатам
        return candidates.mapIndexed { index, rerankedResult ->
            val llmScore = scores.getOrNull(index) ?: rerankedResult.result.similarity
            rerankedResult.copy(rerankedScore = llmScore)
        }
    }

    /**
     * Формирует промпт для LLM reranking.
     */
    private fun buildRerankingPrompt(
        query: String,
        candidates: List<RerankedResult>
    ): String {
        val candidatesText = candidates.mapIndexed { index, rerankedResult ->
            val result = rerankedResult.result
            """
            [${index + 1}]
            Источник: ${result.title ?: result.source}
            Сходство (cosine): ${String.format("%.3f", result.similarity)}
            Текст: ${result.text.take(500)}${if (result.text.length > 500) "..." else ""}
            """.trimIndent()
        }.joinToString("\n\n")

        return """
            Ты — эксперт по оценке релевантности документов для поисковых запросов.
            
            Запрос пользователя: "$query"
            
            Ниже представлены результаты поиска, отсортированные по векторному сходству (cosine similarity).
            Твоя задача — оценить релевантность каждого результата для данного запроса по шкале от 0.0 до 1.0,
            где 1.0 — максимально релевантный результат, а 0.0 — нерелевантный.
            
            Учти:
            - Семантическую релевантность содержания запросу
            - Полноту информации (насколько результат отвечает на запрос)
            - Качество и информативность текста
            
            Результаты:
            $candidatesText
            
            Верни ответ ТОЛЬКО в формате JSON массива чисел (scores), где каждое число — оценка релевантности
            соответствующего результата в порядке их появления (от 0.0 до 1.0).
            
            Формат ответа (без дополнительного текста):
            [0.95, 0.82, 0.65, ...]
        """.trimIndent()
    }

    /**
     * Парсит ответ LLM с оценками релевантности.
     */
    private fun parseRerankingResponse(
        response: org.example.models.OpenRouterResponse,
        expectedCount: Int
    ): List<Double> {
        val output = response.output?.firstOrNull()?.content
            ?.firstOrNull()?.text
            ?: return emptyList()

        // Пытаемся извлечь JSON массив из ответа
        val jsonMatch = Regex("""\[([\d.,\s]+)\]""").find(output)
        if (jsonMatch != null) {
            val numbersStr = jsonMatch.groupValues[1]
            val scores = numbersStr.split(",")
                .mapNotNull { it.trim().toDoubleOrNull()?.coerceIn(0.0, 1.0) }
            
            if (scores.size == expectedCount) {
                return scores
            }
        }

        // Fallback: пытаемся найти числа в тексте
        val numbers = Regex("""\b0?\.\d+\b|\b1\.0\b""").findAll(output)
            .mapNotNull { it.value.toDoubleOrNull()?.coerceIn(0.0, 1.0) }
            .take(expectedCount)
            .toList()

        return if (numbers.size == expectedCount) {
            numbers
        } else {
            // Если не удалось распарсить, возвращаем пустой список
            emptyList()
        }
    }

    /**
     * Получает текущий порог фильтрации.
     */
    fun getThreshold(): Double {
        return when (strategy) {
            is RerankingStrategy.ThresholdBased -> strategy.threshold
            is RerankingStrategy.Hybrid -> strategy.threshold
        }
    }

    /**
     * Обновляет порог фильтрации (только для threshold-based стратегии).
     */
    fun updateThreshold(newThreshold: Double): RelevanceReranker {
        return when (strategy) {
            is RerankingStrategy.ThresholdBased -> {
                RelevanceReranker(
                    RerankingStrategy.ThresholdBased(
                        threshold = newThreshold.coerceIn(0.0, 1.0)
                    )
                )
            }
            is RerankingStrategy.Hybrid -> {
                RelevanceReranker(
                    RerankingStrategy.Hybrid(
                        threshold = newThreshold.coerceIn(0.0, 1.0),
                        llmClient = strategy.llmClient,
                        maxRerankedResults = strategy.maxRerankedResults
                    )
                )
            }
        }
    }
}

