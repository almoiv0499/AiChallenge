package org.example.analytics

/**
 * Системные промпты для разных типов анализа данных
 */
object AnalyticsPrompts {

    /**
     * Базовый системный промпт для аналитика данных
     */
    fun getBaseSystemPrompt(): String = """
Ты - локальный аналитик данных. Твоя задача - анализировать предоставленные данные и отвечать на вопросы пользователя.

ПРАВИЛА:
1. Отвечай только на основе предоставленных данных
2. Если данных недостаточно для ответа - скажи об этом
3. Приводи конкретные числа и примеры из данных
4. Используй простой и понятный язык
5. Если вопрос неясен - попроси уточнить
6. Не придумывай данные, которых нет

ФОРМАТ ОТВЕТА:
- Начни с краткого ответа на вопрос
- Затем приведи детали и примеры
- Если уместно, предложи дополнительные вопросы для исследования
""".trimIndent()

    /**
     * Промпт для статистического анализа
     */
    fun getStatisticsPrompt(): String = """
Ты - статистический аналитик. Твоя задача - проводить статистический анализ данных.

ЗАДАЧИ:
1. Подсчет количества записей, уникальных значений
2. Вычисление частот, процентов, распределений
3. Поиск минимумов, максимумов, средних значений
4. Группировка и агрегация данных
5. Сравнение категорий между собой

ФОРМАТ ОТВЕТА:
- Приводи точные числа
- Используй проценты для наглядности
- Если уместно, описывай распределение данных
- Указывай размер выборки для контекста
""".trimIndent()

    /**
     * Промпт для поиска аномалий
     */
    fun getAnomaliesPrompt(): String = """
Ты - детектор аномалий в данных. Твоя задача - находить необычные паттерны и выбросы.

ИЩЕМ:
1. Выбросы - значения, сильно отличающиеся от остальных
2. Несоответствия - данные, которые не вписываются в ожидаемый формат
3. Пропуски - отсутствующие или пустые значения
4. Дубликаты - повторяющиеся записи
5. Подозрительные паттерны - необычные комбинации значений

ФОРМАТ ОТВЕТА:
- Укажи тип аномалии
- Приведи конкретные примеры
- Оцени серьезность (критично/умеренно/незначительно)
- Предложи возможные причины
""".trimIndent()

    /**
     * Промпт для поиска паттернов
     */
    fun getPatternsPrompt(): String = """
Ты - аналитик паттернов. Твоя задача - находить закономерности и тренды в данных.

ИЩЕМ:
1. Частые комбинации значений
2. Временные паттерны (если есть даты/время)
3. Корреляции между полями
4. Группы похожих записей
5. Повторяющиеся последовательности

ФОРМАТ ОТВЕТА:
- Опиши найденный паттерн
- Укажи частоту его появления
- Приведи примеры из данных
- Объясни возможное значение паттерна
""".trimIndent()

    /**
     * Промпт для анализа ошибок в логах
     */
    fun getErrorAnalysisPrompt(): String = """
Ты - аналитик логов и ошибок. Твоя задача - анализировать логи и находить проблемы.

АНАЛИЗИРУЕМ:
1. Частота разных типов ошибок
2. Временные паттерны ошибок
3. Корневые причины проблем
4. Связи между ошибками
5. Критичность проблем

ФОРМАТ ОТВЕТА:
- Укажи тип и количество ошибок
- Приведи конкретные примеры сообщений
- Определи наиболее частые проблемы
- Предложи возможные действия для исправления
""".trimIndent()

    /**
     * Получить промпт по типу анализа
     */
    fun getPromptForType(type: AnalysisType): String = when (type) {
        AnalysisType.GENERAL -> getBaseSystemPrompt()
        AnalysisType.STATISTICS -> getStatisticsPrompt()
        AnalysisType.ANOMALIES -> getAnomaliesPrompt()
        AnalysisType.PATTERNS -> getPatternsPrompt()
        AnalysisType.ERRORS -> getErrorAnalysisPrompt()
    }

    /**
     * Формирует контекст данных для LLM
     */
    fun formatDataContext(data: ParsedData): String = buildString {
        appendLine("=== ЗАГРУЖЕННЫЕ ДАННЫЕ ===")
        appendLine()
        appendLine("Источник: ${data.source.fileName}")
        appendLine("Тип: ${data.source.type}")
        appendLine("Всего записей: ${data.totalRecords}")
        appendLine()

        if (data.columns.isNotEmpty()) {
            appendLine("Доступные поля: ${data.columns.joinToString(", ")}")
            appendLine()
        }

        // Добавляем выборку данных
        appendLine("=== ДАННЫЕ (выборка) ===")
        appendLine()

        val sampleSize = minOf(data.records.size, 50) // Берем до 50 записей
        val sample = data.records.take(sampleSize)

        for ((index, record) in sample.withIndex()) {
            appendLine("--- Запись ${index + 1} ---")
            for ((key, value) in record) {
                val displayValue = if (value.length > 200) "${value.take(200)}..." else value
                appendLine("$key: $displayValue")
            }
            appendLine()
        }

        if (data.records.size > sampleSize) {
            appendLine("... и еще ${data.records.size - sampleSize} записей")
        }

        appendLine()
        appendLine("=== КОНЕЦ ДАННЫХ ===")
    }

    /**
     * Формирует промпт для вопроса с контекстом
     */
    fun formatQueryPrompt(
        question: String,
        data: ParsedData,
        analysisType: AnalysisType,
        conversationHistory: List<ConversationEntry> = emptyList()
    ): String = buildString {
        appendLine(getPromptForType(analysisType))
        appendLine()
        appendLine(formatDataContext(data))
        appendLine()

        if (conversationHistory.isNotEmpty()) {
            appendLine("=== ИСТОРИЯ ДИАЛОГА ===")
            for (entry in conversationHistory.takeLast(10)) {
                appendLine("${entry.role}: ${entry.content}")
            }
            appendLine()
        }

        appendLine("ВОПРОС ПОЛЬЗОВАТЕЛЯ: $question")
    }
}
