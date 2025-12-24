package org.example.ui

import org.example.models.ApiResponse
import org.example.models.ChatResponse

object ConsoleUI {
    private const val SEPARATOR_WIDTH = 60
    private const val SEPARATOR_CHAR = '─'
    private const val HEADER_CHAR = '='

    fun printWelcome() = println(
        """
        ╔══════════════════════════════════════════════════════════════╗
        ║         🤖 OpenRouter Agent - Терминальный чат 🤖            ║
        ╠══════════════════════════════════════════════════════════════╣
        ║  Команды:                                                    ║
        ║    /exit         - выход из программы                        ║
        ║    /clear        - очистить историю разговора                 ║
        ║    /clear-tasks  - очистить базу данных задач                 ║
        ║    /help         - показать справку                          ║
        ║    /tools        - переключить отправку инструментов         ║
        ║    /rag          - переключить RAG режим                       ║
        ║    /rag-compare  - переключить режим сравнения RAG            ║
        ║    /reranker     - переключить фильтр релевантности            ║
        ║    /reranker-compare - сравнение с фильтром и без              ║
        ╚══════════════════════════════════════════════════════════════╝
        """.trimIndent()
    )

    fun printHelp() = println(
        """
        
        📖 Справка по использованию OpenRouter Agent:
        
        Доступные инструменты:
        • get_current_time - узнать текущее время
        • calculator       - математические вычисления
        • search          - поиск информации
        • random_number   - генерация случайного числа
        
        Примеры запросов:
        • "Сколько будет 25 * 4?"
        • "Который сейчас час?"
        • "Сгенерируй случайное число от 1 до 100"
        • "Найди информацию о Kotlin"
        
        Команды:
        • /exit        - выход
        • /clear       - очистить историю разговора
        • /clear-tasks - очистить базу данных задач
        • /tasks       - переключить напоминания о задачах (вкл/выкл)
        • /help        - эта справка
        • /tools       - переключить отправку инструментов (вкл/выкл)
        • /rag         - переключить RAG режим (вкл/выкл)
        • /rag-compare - переключить режим сравнения RAG (вкл/выкл)
        • /reranker    - переключить фильтр релевантности (вкл/выкл)
        • /reranker-compare - сравнение с фильтром и без (вкл/выкл)
        • /reranker-threshold <число> - установить порог фильтрации (0.0-1.0)
        
        """.trimIndent()
    )

    fun printInitializing() = println("\n🔧 Инициализация...")
    fun printReady() = println("\n✅ Агент готов к работе! Введите ваш вопрос:\n")
    fun printGoodbye() = println("\n👋 До свидания!")
    fun printHistoryCleared() = println("✅ История очищена\n")
    fun printToolsStatus(enabled: Boolean) {
        val status = if (enabled) "включены" else "выключены"
        val emoji = if (enabled) "✅" else "❌"
        println("$emoji Инструменты $status")
        if (!enabled) {
            println("   💡 Запросы будут использовать меньше токенов")
        }
        println()
    }
    fun printUserPrompt() = print("Вы: ")

    fun printUserMessage(message: String) {
        println()
        printSeparator(HEADER_CHAR)
        println("👤 Пользователь: $message")
        printSeparator(HEADER_CHAR)
    }

    fun printResponse(response: ChatResponse) {
        println()
        printSeparator(SEPARATOR_CHAR)
        println("📝 Ответ: ${response.response}")
        printToolCallsIfPresent(response)
        printSeparator(SEPARATOR_CHAR)
        println()
    }

    fun printError(message: String?) = println("\n❌ Ошибка: $message")

    fun printTokenLimitExceeded() {
        println("\n⚠️  ПРЕВЫШЕН ЛИМИТ ТОКЕНОВ")
        println("   Запрос содержит слишком много токенов для обработки.")
        println("   Модель openai/gpt-4o-mini-2024-07-18 поддерживает до 128,000 токенов контекста.")
        println("   Попробуйте:")
        println("   • Сократить длину запроса")
        println("   • Очистить историю разговора командой /clear")
        println("   • Разбить запрос на несколько частей")
    }

    fun printToolCall(toolName: String, arguments: Any) {
        println("\n🔧 Вызов инструмента:")
        println("   📌 Инструмент: $toolName")
        println("   📝 Аргументы: $arguments")
    }

    fun printToolResult(result: String) = println("   ✅ Результат: $result")

    fun printAgentInitialized(model: String, toolCount: Int) {
        println("🤖 OpenRouter Агент инициализирован")
        println("   Модель: $model")
        println("   Инструментов: $toolCount")
    }

    fun printToolRegistered(toolName: String) = println("📦 Зарегистрирован инструмент: $toolName")
    fun printHistoryClearedLog() = println("🗑️ История разговора очищена")
    fun printHttpLog(message: String) = println("🌐 HTTP: $message")
    fun printArgumentParseError(error: String?) = println("   ⚠️ Ошибка парсинга аргументов: $error")

    fun printDebugIteration(current: Int, max: Int) {
    }

    fun printRequestDetails(
        historyItems: Int,
        historyTokens: Int,
        toolsCount: Int,
        toolsTokens: Int,
        totalEstimated: Int
    ) {
        println("\n📊 Детали запроса:")
        println("   📝 История разговора: $historyItems сообщений (~$historyTokens токенов)")
        if (toolsCount > 0) {
            println("   🔧 Инструменты: $toolsCount определений (~$toolsTokens токенов)")
        }
        println("   📊 Всего в запросе: ~$totalEstimated токенов")
    }

    fun printDebugOutputItems(items: List<Any>) {
    }

    fun printResponseReceived(
        temperature: Double?,
        finishReason: String?,
        inputTokens: Int?,
        outputTokens: Int?,
        totalTokens: Int?,
        responseTimeMs: Long?
    ) {
        println("📥 Получен ответ от OpenRouter")
        println("   Статус: $finishReason")
        println("   📤 Токенов на запрос (input): ${inputTokens ?: "N/A"}")
        println("   📥 Токенов на ответ (output): ${outputTokens ?: "N/A"}")
        println("   📊 Всего токенов: ${totalTokens ?: "N/A"}")
        println("   ⏱️ Время ответа: ${responseTimeMs?.let { "${it}ms" } ?: "N/A"}")
        println("🌡️ Temperature: $temperature")
    }

    private fun printSeparator(char: Char) = println(char.toString().repeat(SEPARATOR_WIDTH))

    private fun printToolCallsIfPresent(response: ChatResponse) {
        if (response.toolCalls.isEmpty()) return
        println("\n🔧 Использованные инструменты:")
        response.toolCalls.forEach { println("   • ${it.toolName}: ${it.result}") }
    }

    fun printHistoryCompressionStarted() {
        println("\n🗜️  Начато сжатие истории диалога...")
    }

    fun printHistoryCompressionCompleted(summary: String, tokensBefore: Int, tokensAfter: Int, savedTokens: Int) {
        println("✅ История диалога успешно сжата")
        println()
        println("📝 Созданное резюме:")
        printSeparator(SEPARATOR_CHAR)
        println(summary)
        printSeparator(SEPARATOR_CHAR)
        println()
        println("   📊 Токенов до сжатия: ~$tokensBefore")
        println("   📊 Токенов после сжатия: ~$tokensAfter")
        println("   💾 Сэкономлено токенов: ~$savedTokens (${if (tokensBefore > 0) (savedTokens * 100 / tokensBefore) else 0}%)")
        println()
    }

    fun printHistoryCompressionFailed() {
        println("⚠️  Не удалось сжать историю диалога, продолжается без сжатия")
    }

    fun printHistoryCompressionError(error: String?) {
        println("❌ Ошибка при сжатии истории: ${error ?: "неизвестная ошибка"}")
    }

    fun printCreatingSummary(messagesCount: Int) {
        println("   🔄 Создание резюме из $messagesCount сообщений...")
    }

    fun printDatabaseInitialized(dbPath: String) {
        println("💾 База данных инициализирована: $dbPath")
    }

    fun printDatabaseError(error: String?) {
        println("❌ Ошибка БД: ${error ?: "неизвестная ошибка"}")
    }

    fun printSummarySaved(id: Long) {
        println("   💾 Summary сохранен в БД (ID: $id)")
    }

    fun printSummaryLoaded(summary: String) {
        println("📂 Загружен сохраненный summary из БД:")
        printSeparator(SEPARATOR_CHAR)
        println(summary)
        printSeparator(SEPARATOR_CHAR)
        println()
    }

    fun printNoSavedSummary() {
        println("ℹ️  Сохраненных summary не найдено, начинаем новый диалог")
    }

    fun printDatabaseCleared(deleted: Int) {
        println("🗑️  Очищено summary из БД: $deleted записей")
    }

    fun printTasksDatabaseCleared(deleted: Int) {
        println("🗑️  Очищено задач из БД: $deleted записей\n")
    }

    fun printTasksDatabaseError(error: String) {
        println("❌ $error\n")
    }

    fun printCompressionCheck(currentCount: Int, threshold: Int) {
        if (currentCount > 0 && currentCount % 5 == 0) {
            println("   💡 Сообщений пользователя: $currentCount/$threshold (компрессия произойдет после $threshold сообщений)")
        }
    }

    fun printMcpConnecting(serverName: String) {
        println("🔌 Подключение к MCP серверу: $serverName...")
    }

    fun printMcpConnected(serverName: String, serverVersion: String) {
        println("✅ Подключено к MCP серверу: $serverName v$serverVersion")
    }

    fun printMcpTools(tools: List<org.example.mcp.McpTool>) {
        if (tools.isEmpty()) {
            println("   ℹ️  Доступных инструментов не найдено")
            return
        }
        println("   📋 Доступные инструменты (${tools.size}):")
        tools.forEachIndexed { index, tool ->
            val description = tool.description?.take(60)?.let { if (it.length == 60) "$it..." else it } ?: "без описания"
            println("      ${index + 1}. ${tool.name}")
            println("         └─ $description")
        }
    }

    fun printMcpError(error: String) {
        println("❌ Ошибка MCP: $error")
    }

    fun printStartingServices() {
        println("🚀 Запуск локальных сервисов...")
        println("   🔌 Notion MCP Server: http://localhost:8081")
    }

    fun printServicesStarted() {
        println("✅ Локальные сервисы запущены")
    }

    fun printMcpToolsRegistered(count: Int) {
        println("✅ Зарегистрировано MCP инструментов: $count")
    }

    fun printComparisonStep(mode: String) {
        println("\n${"=".repeat(SEPARATOR_WIDTH)}")
        println("🔄 Режим: $mode")
        println("${"=".repeat(SEPARATOR_WIDTH)}\n")
    }

    fun printRagComparison(
        question: String,
        answerWithRag: org.example.models.ChatResponse,
        answerWithoutRag: org.example.models.ChatResponse,
        ragContext: String?
    ) {
        println("\n${"=".repeat(SEPARATOR_WIDTH)}")
        println("📊 СРАВНЕНИЕ ОТВЕТОВ: RAG vs БЕЗ RAG")
        println("${"=".repeat(SEPARATOR_WIDTH)}\n")
        
        println("❓ Вопрос: $question\n")
        
        if (ragContext != null) {
            println("📚 Найденный RAG контекст:")
            printSeparator(SEPARATOR_CHAR)
            println(ragContext.take(500) + if (ragContext.length > 500) "..." else "")
            printSeparator(SEPARATOR_CHAR)
            println()
        } else {
            println("⚠️ RAG контекст не найден\n")
        }
        
        println("${"-".repeat(SEPARATOR_WIDTH)}")
        println("❌ ОТВЕТ БЕЗ RAG:")
        println("${"-".repeat(SEPARATOR_WIDTH)}")
        println(answerWithoutRag.response)
        println()
        
        println("${"-".repeat(SEPARATOR_WIDTH)}")
        println("✅ ОТВЕТ С RAG:")
        println("${"-".repeat(SEPARATOR_WIDTH)}")
        println(answerWithRag.response)
        println()
        
        // Анализ различий
        val analysis = analyzeDifferences(answerWithoutRag.response, answerWithRag.response, ragContext != null)
        println("${"=".repeat(SEPARATOR_WIDTH)}")
        println("🔍 АНАЛИЗ:")
        println("${"=".repeat(SEPARATOR_WIDTH)}")
        println(analysis)
        println("${"=".repeat(SEPARATOR_WIDTH)}\n")
    }

    private fun analyzeDifferences(answerWithoutRag: String, answerWithRag: String, hasRagContext: Boolean): String {
        val builder = StringBuilder()
        
        if (!hasRagContext) {
            builder.append("⚠️ RAG контекст не был найден, поэтому ответы могут быть идентичными.\n")
            builder.append("💡 Попробуйте задать вопрос, связанный с проиндексированными документами.\n")
            return builder.toString()
        }
        
        val lengthDiff = answerWithRag.length - answerWithoutRag.length
        val wordsDiff = answerWithRag.split(Regex("\\s+")).size - answerWithoutRag.split(Regex("\\s+")).size
        
        builder.append("📏 Длина ответов:\n")
        builder.append("   Без RAG: ${answerWithoutRag.length} символов\n")
        builder.append("   С RAG: ${answerWithRag.length} символов\n")
        builder.append("   Разница: ${if (lengthDiff >= 0) "+" else ""}$lengthDiff символов\n\n")
        
        builder.append("📝 Количество слов:\n")
        builder.append("   Без RAG: ${answerWithoutRag.split(Regex("\\s+")).size} слов\n")
        builder.append("   С RAG: ${answerWithRag.split(Regex("\\s+")).size} слов\n")
        builder.append("   Разница: ${if (wordsDiff >= 0) "+" else ""}$wordsDiff слов\n\n")
        
        // Простая проверка на схожесть
        val similarity = calculateSimpleSimilarity(answerWithoutRag, answerWithRag)
        builder.append("🔗 Схожесть ответов: ${String.format("%.1f", similarity * 100)}%\n\n")
        
        // Выводы
        builder.append("💡 Выводы:\n")
        if (similarity < 0.5) {
            builder.append("   ✅ RAG значительно изменил ответ - контекст был релевантным\n")
        } else if (similarity < 0.8) {
            builder.append("   ⚠️ RAG частично изменил ответ - контекст был частично релевантным\n")
        } else {
            builder.append("   ℹ️ RAG мало повлиял на ответ - возможно, контекст был не очень релевантным\n")
        }
        
        if (lengthDiff > 100) {
            builder.append("   📚 Ответ с RAG значительно подробнее - добавлена информация из базы знаний\n")
        } else if (lengthDiff < -100) {
            builder.append("   ✂️ Ответ с RAG короче - возможно, модель использовала более точную информацию\n")
        }
        
        return builder.toString()
    }

    private fun calculateSimpleSimilarity(text1: String, text2: String): Double {
        // Нормализуем тексты: убираем пунктуацию, приводим к нижнему регистру
        val normalize = { text: String ->
            text.lowercase()
                .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
                .split(Regex("\\s+"))
                .filter { it.isNotBlank() && it.length > 2 } // Игнорируем короткие слова
                .toSet()
        }
        
        val words1 = normalize(text1)
        val words2 = normalize(text2)
        
        if (words1.isEmpty() && words2.isEmpty()) return 1.0
        if (words1.isEmpty() || words2.isEmpty()) return 0.0
        
        // Jaccard similarity (пересечение / объединение)
        val intersection = words1.intersect(words2).size
        val union = words1.union(words2).size
        
        val jaccard = intersection.toDouble() / union.toDouble()
        
        // Дополнительно учитываем длину текстов (если тексты очень разные по длине, схожесть ниже)
        val lengthRatio = minOf(text1.length, text2.length).toDouble() / maxOf(text1.length, text2.length).toDouble()
        
        // Комбинируем метрики (70% Jaccard, 30% длина)
        return jaccard * 0.7 + lengthRatio * 0.3
    }

    fun printRagModeStatus(enabled: Boolean) {
        val status = if (enabled) "включен" else "выключен"
        val emoji = if (enabled) "✅" else "❌"
        println("$emoji RAG режим $status")
        println()
    }

    fun printComparisonModeStatus(enabled: Boolean) {
        val status = if (enabled) "включен" else "выключен"
        val emoji = if (enabled) "✅" else "❌"
        println("$emoji Режим сравнения RAG $status")
        if (enabled) {
            println("   💡 Каждый вопрос будет обрабатываться дважды: с RAG и без RAG")
        }
        println()
    }

    fun printRerankerComparison(
        question: String,
        answerWithReranker: org.example.models.ChatResponse,
        answerWithoutReranker: org.example.models.ChatResponse,
        contextWithReranker: String?,
        contextWithoutReranker: String?
    ) {
        println("\n${"=".repeat(SEPARATOR_WIDTH)}")
        println("📊 СРАВНЕНИЕ ОТВЕТОВ: С ФИЛЬТРОМ vs БЕЗ ФИЛЬТРА РЕЛЕВАНТНОСТИ")
        println("${"=".repeat(SEPARATOR_WIDTH)}\n")
        
        println("❓ Вопрос: $question\n")
        
        // Показываем контексты
        if (contextWithoutReranker != null) {
            println("📚 Контекст БЕЗ фильтра:")
            printSeparator(SEPARATOR_CHAR)
            println(contextWithoutReranker.take(400) + if (contextWithoutReranker.length > 400) "..." else "")
            printSeparator(SEPARATOR_CHAR)
            println()
        }
        
        if (contextWithReranker != null) {
            println("📚 Контекст С фильтром:")
            printSeparator(SEPARATOR_CHAR)
            println(contextWithReranker.take(400) + if (contextWithReranker.length > 400) "..." else "")
            printSeparator(SEPARATOR_CHAR)
            println()
        }
        
        println("${"-".repeat(SEPARATOR_WIDTH)}")
        println("❌ ОТВЕТ БЕЗ ФИЛЬТРА:")
        println("${"-".repeat(SEPARATOR_WIDTH)}")
        println(answerWithoutReranker.response)
        println()
        
        println("${"-".repeat(SEPARATOR_WIDTH)}")
        println("✅ ОТВЕТ С ФИЛЬТРОМ:")
        println("${"-".repeat(SEPARATOR_WIDTH)}")
        println(answerWithReranker.response)
        println()
        
        // Анализ различий
        val analysis = analyzeRerankerDifferences(
            answerWithoutReranker.response, 
            answerWithReranker.response,
            contextWithoutReranker,
            contextWithReranker
        )
        println("${"=".repeat(SEPARATOR_WIDTH)}")
        println("🔍 АНАЛИЗ КАЧЕСТВА ФИЛЬТРАЦИИ:")
        println("${"=".repeat(SEPARATOR_WIDTH)}")
        println(analysis)
        println("${"=".repeat(SEPARATOR_WIDTH)}\n")
    }

    private fun analyzeRerankerDifferences(
        answerWithoutReranker: String,
        answerWithReranker: String,
        contextWithoutReranker: String?,
        contextWithReranker: String?
    ): String {
        val builder = StringBuilder()
        
        // Анализ контекстов - более точный подсчет
        val contextWithoutCount = contextWithoutReranker?.let { 
            it.split("\n").count { line -> line.trim().matches(Regex("""^\[\d+\]""")) }
        } ?: 0
        val contextWithCount = contextWithReranker?.let { 
            it.split("\n").count { line -> line.trim().matches(Regex("""^\[\d+\]""")) }
        } ?: 0
        
        builder.append("📊 Статистика контекстов:\n")
        builder.append("   Без фильтра: $contextWithoutCount чанков\n")
        builder.append("   С фильтром: $contextWithCount чанков\n")
        if (contextWithoutCount > contextWithCount) {
            val filteredOut = contextWithoutCount - contextWithCount
            builder.append("   ✅ Фильтр отсек $filteredOut нерелевантных чанков (${String.format("%.1f", (filteredOut * 100.0 / contextWithoutCount))}%)\n")
        } else if (contextWithoutCount == contextWithCount && contextWithoutCount > 0) {
            builder.append("   ⚠️ Фильтр не изменил количество чанков - возможно, все результаты были релевантными\n")
        } else if (contextWithoutCount < contextWithCount) {
            builder.append("   ℹ️ С фильтром больше результатов - это необычно, проверьте логи\n")
        }
        builder.append("\n")
        
        // Анализ ответов
        val lengthDiff = answerWithReranker.length - answerWithoutReranker.length
        val wordsDiff = answerWithReranker.split(Regex("\\s+")).size - answerWithoutReranker.split(Regex("\\s+")).size
        
        builder.append("📏 Длина ответов:\n")
        builder.append("   Без фильтра: ${answerWithoutReranker.length} символов\n")
        builder.append("   С фильтром: ${answerWithReranker.length} символов\n")
        builder.append("   Разница: ${if (lengthDiff >= 0) "+" else ""}$lengthDiff символов\n\n")
        
        builder.append("📝 Количество слов:\n")
        builder.append("   Без фильтра: ${answerWithoutReranker.split(Regex("\\s+")).size} слов\n")
        builder.append("   С фильтром: ${answerWithReranker.split(Regex("\\s+")).size} слов\n")
        builder.append("   Разница: ${if (wordsDiff >= 0) "+" else ""}$wordsDiff слов\n\n")
        
        // Схожесть
        val similarity = calculateSimpleSimilarity(answerWithoutReranker, answerWithReranker)
        builder.append("🔗 Схожесть ответов: ${String.format("%.1f", similarity * 100)}%\n\n")
        
        // Выводы
        builder.append("💡 Выводы:\n")
        if (contextWithoutCount > contextWithCount && similarity < 0.9) {
            builder.append("   ✅ Фильтр эффективно отсеял нерелевантные результаты\n")
            builder.append("   ✅ Ответ с фильтром более точный и релевантный\n")
        } else if (contextWithoutCount == contextWithCount) {
            builder.append("   ⚠️ Фильтр не изменил количество результатов\n")
            builder.append("   💡 Возможно, все результаты были достаточно релевантными\n")
        } else {
            builder.append("   ℹ️ Фильтр незначительно повлиял на результаты\n")
        }
        
        if (similarity > 0.95) {
            builder.append("   ℹ️ Ответы очень похожи - фильтр не сильно изменил качество\n")
        } else if (similarity < 0.7) {
            builder.append("   ✅ Фильтр значительно улучшил качество ответа\n")
        }
        
        return builder.toString()
    }

    fun printRerankerModeStatus(enabled: Boolean) {
        val status = if (enabled) "включен" else "выключен"
        val emoji = if (enabled) "✅" else "❌"
        println("$emoji Режим сравнения фильтра релевантности $status")
        if (enabled) {
            println("   💡 Каждый вопрос будет обрабатываться дважды: с фильтром и без фильтра")
        }
        println()
    }

    fun printRerankerThreshold(threshold: Double?) {
        if (threshold != null) {
            println("📊 Текущий порог фильтрации: ${String.format("%.2f", threshold)}")
        } else {
            println("⚠️ Reranker не инициализирован")
        }
        println()
    }
}
