package org.example.analytics

import org.example.client.ollama.OllamaClient
import org.example.client.ollama.OllamaMessage

/**
 * Основной сервис анализа данных с использованием Ollama LLM
 */
class DataAnalyzerService(
    private val ollamaClient: OllamaClient,
    private val model: String = "llama3.2"
) {
    private val session = AnalyticsSession()

    /**
     * Загрузить данные из файла
     */
    fun loadData(path: String): ParsedData {
        val data = DataParser.autoDetect(path)
        session.loadedData = data
        session.conversationHistory.clear()
        session.analysisResults.clear()
        return data
    }

    /**
     * Проверить, загружены ли данные
     */
    fun hasLoadedData(): Boolean = session.loadedData != null

    /**
     * Получить информацию о загруженных данных
     */
    fun getDataInfo(): String {
        val data = session.loadedData ?: return "Данные не загружены. Используйте команду 'load <путь>' для загрузки."
        return buildString {
            appendLine(data.getSummary())
            appendLine()
            appendLine(data.preview)
        }
    }

    /**
     * Задать вопрос по загруженным данным
     */
    suspend fun ask(question: String, analysisType: AnalysisType = AnalysisType.GENERAL): AnalyticsResponse {
        val data = session.loadedData
            ?: throw IllegalStateException("Данные не загружены. Сначала загрузите файл командой 'load <путь>'")

        // Формируем промпт
        val systemPrompt = AnalyticsPrompts.getPromptForType(analysisType)
        val dataContext = AnalyticsPrompts.formatDataContext(data)

        // Формируем историю разговора для контекста
        val history = session.conversationHistory.takeLast(10).map { entry ->
            OllamaMessage(role = entry.role, content = entry.content)
        }

        // Отправляем запрос к LLM
        val response = ollamaClient.chat(
            message = buildString {
                appendLine(dataContext)
                appendLine()
                appendLine("Вопрос: $question")
            },
            model = model,
            conversationHistory = history,
            systemPrompt = systemPrompt,
            temperature = 0.3, // Низкая температура для более точных ответов
            numCtx = 8192 // Увеличиваем контекст для больших данных
        )

        val answer = response.message?.content ?: "Не удалось получить ответ"

        // Сохраняем в историю
        session.conversationHistory.add(ConversationEntry("user", question))
        session.conversationHistory.add(ConversationEntry("assistant", answer))

        // Формируем ответ
        val analyticsResponse = AnalyticsResponse(
            query = question,
            answer = answer,
            analysisType = analysisType,
            suggestions = extractSuggestions(answer)
        )

        session.analysisResults.add(analyticsResponse)

        return analyticsResponse
    }

    /**
     * Быстрый статистический анализ
     */
    suspend fun analyzeStatistics(): AnalyticsResponse {
        return ask(
            "Проведи краткий статистический анализ данных: посчитай количество записей, уникальные значения ключевых полей, распределение по категориям если они есть.",
            AnalysisType.STATISTICS
        )
    }

    /**
     * Поиск аномалий
     */
    suspend fun findAnomalies(): AnalyticsResponse {
        return ask(
            "Найди аномалии в данных: выбросы, пропуски, дубликаты, подозрительные значения.",
            AnalysisType.ANOMALIES
        )
    }

    /**
     * Поиск паттернов
     */
    suspend fun findPatterns(): AnalyticsResponse {
        return ask(
            "Найди закономерности и паттерны в данных: частые комбинации, тренды, корреляции.",
            AnalysisType.PATTERNS
        )
    }

    /**
     * Анализ ошибок (для логов)
     */
    suspend fun analyzeErrors(): AnalyticsResponse {
        return ask(
            "Проанализируй ошибки в логах: какие ошибки встречаются чаще всего, их частота и возможные причины.",
            AnalysisType.ERRORS
        )
    }

    /**
     * Экспорт результатов анализа
     */
    fun exportResults(path: String) {
        val data = session.loadedData
        val results = session.analysisResults

        val exportContent = buildString {
            appendLine("# Отчет по анализу данных")
            appendLine()
            appendLine("## Источник данных")
            if (data != null) {
                appendLine("- Файл: ${data.source.fileName}")
                appendLine("- Тип: ${data.source.type}")
                appendLine("- Записей: ${data.totalRecords}")
                appendLine("- Колонки: ${data.columns.joinToString(", ")}")
            }
            appendLine()

            appendLine("## Результаты анализа")
            appendLine()

            for ((index, result) in results.withIndex()) {
                appendLine("### ${index + 1}. ${result.analysisType}")
                appendLine()
                appendLine("**Вопрос:** ${result.query}")
                appendLine()
                appendLine("**Ответ:**")
                appendLine(result.answer)
                appendLine()
                if (result.suggestions.isNotEmpty()) {
                    appendLine("**Предложения для дальнейшего анализа:**")
                    result.suggestions.forEach { appendLine("- $it") }
                }
                appendLine()
                appendLine("---")
                appendLine()
            }
        }

        java.io.File(path).writeText(exportContent)
    }

    /**
     * Очистить сессию
     */
    fun clearSession() {
        session.loadedData = null
        session.conversationHistory.clear()
        session.analysisResults.clear()
    }

    /**
     * Получить историю разговора
     */
    fun getConversationHistory(): List<ConversationEntry> = session.conversationHistory.toList()

    /**
     * Извлечь предложения для дальнейшего анализа из ответа
     */
    private fun extractSuggestions(answer: String): List<String> {
        val suggestions = mutableListOf<String>()

        // Ищем строки, начинающиеся с маркеров предложений
        val lines = answer.lines()
        var inSuggestionBlock = false

        for (line in lines) {
            val trimmed = line.trim()

            // Определяем начало блока предложений
            if (trimmed.contains("предлож", ignoreCase = true) ||
                trimmed.contains("дополнительн", ignoreCase = true) ||
                trimmed.contains("можно также", ignoreCase = true)) {
                inSuggestionBlock = true
                continue
            }

            // Собираем предложения из списка
            if (inSuggestionBlock && (trimmed.startsWith("-") || trimmed.startsWith("•") || trimmed.matches(Regex("^\\d+\\..*")))) {
                val suggestion = trimmed.removePrefix("-").removePrefix("•").trim()
                    .replace(Regex("^\\d+\\.\\s*"), "")
                if (suggestion.isNotBlank() && suggestion.length > 5) {
                    suggestions.add(suggestion)
                }
            }

            // Завершаем сбор при пустой строке или новом разделе
            if (inSuggestionBlock && trimmed.isBlank()) {
                break
            }
        }

        return suggestions.take(5)
    }
}
