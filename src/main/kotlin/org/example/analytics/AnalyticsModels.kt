package org.example.analytics

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Источник данных - информация о загруженном файле
 */
@Serializable
data class DataSource(
    val path: String,
    val type: DataType,
    val sizeBytes: Long,
    val fileName: String
)

/**
 * Тип данных
 */
enum class DataType {
    CSV,
    JSON,
    JSON_LINES,
    LOG,
    UNKNOWN
}

/**
 * Распарсенные данные с метаинформацией
 */
@Serializable
data class ParsedData(
    val source: DataSource,
    val records: List<Map<String, String>>,
    val columns: List<String>,
    val totalRecords: Int,
    val preview: String
) {
    fun getSummary(): String = buildString {
        appendLine("Файл: ${source.fileName}")
        appendLine("Тип: ${source.type}")
        appendLine("Размер: ${formatSize(source.sizeBytes)}")
        appendLine("Записей: $totalRecords")
        if (columns.isNotEmpty()) {
            appendLine("Колонки: ${columns.joinToString(", ")}")
        }
    }

    private fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${bytes / (1024 * 1024)} MB"
    }
}

/**
 * Запрос на анализ данных
 */
@Serializable
data class AnalyticsQuery(
    val question: String,
    val analysisType: AnalysisType = AnalysisType.GENERAL
)

/**
 * Тип анализа
 */
enum class AnalysisType {
    GENERAL,           // Общий вопрос по данным
    STATISTICS,        // Статистический анализ
    ANOMALIES,         // Поиск аномалий
    PATTERNS,          // Поиск паттернов
    ERRORS             // Анализ ошибок (для логов)
}

/**
 * Ответ с результатом анализа
 */
@Serializable
data class AnalyticsResponse(
    val query: String,
    val answer: String,
    val analysisType: AnalysisType,
    val confidence: String? = null,
    val suggestions: List<String> = emptyList()
)

/**
 * Состояние сессии аналитика
 */
data class AnalyticsSession(
    var loadedData: ParsedData? = null,
    val conversationHistory: MutableList<ConversationEntry> = mutableListOf(),
    val analysisResults: MutableList<AnalyticsResponse> = mutableListOf()
)

/**
 * Запись в истории разговора
 */
data class ConversationEntry(
    val role: String,
    val content: String
)
