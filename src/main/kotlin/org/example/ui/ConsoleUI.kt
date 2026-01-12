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
        ║    /exit            - выход из программы                     ║
        ║    /clear           - очистить историю разговора             ║
        ║    /help            - показать справку                       ║
        ║    /help <вопрос>   - поиск в документации проекта (RAG)     ║
        ║    /tools           - переключить отправку инструментов      ║
        ╠══════════════════════════════════════════════════════════════╣
        ║  💡 Примеры:                                                 ║
        ║    /help как настроить проект?                               ║
        ║    /help какие правила стиля кода?                           ║
        ╚══════════════════════════════════════════════════════════════╝
        """.trimIndent()
    )

    fun printHelp() = println(
        """
        
        📖 Справка по использованию OpenRouter Agent:
        
        ═══════════════════════════════════════════════════════════════
        📋 КОМАНДЫ
        ═══════════════════════════════════════════════════════════════
        
        🔹 Основные команды:
        • /exit            - выход из программы
        • /clear           - очистить историю разговора
        • /help            - эта справка
        • /tools           - переключить использование инструментов
        
        🔹 Помощь по OpenRouterAgent (RAG):
        • /help <запрос>   - поиск по OpenRouterAgent.kt
        • Примеры:
          🔍 /help что делает processMessage
          🔍 /help как работает executeAgentLoop
          🔍 /help системный промпт
          📂 /help строки 100-200
        
        🔹 Задачи:
        • /tasks           - напоминания о задачах (вкл/выкл)
        • /clear-tasks     - очистить базу данных задач
        
        ═══════════════════════════════════════════════════════════════
        🔧 ИНСТРУМЕНТЫ
        ═══════════════════════════════════════════════════════════════
        
        📌 Встроенные:
        • get_current_time - текущее время
        • calculator       - математические вычисления
        • search           - поиск информации
        • random_number    - генерация случайного числа
        
        📌 MCP Notion (порт 8081):
        • notion_get_tasks    - получить задачи
        • notion_create_task  - создать задачу
        • notion_update_task  - обновить задачу
        
        📌 MCP Weather (порт 8082):
        • get_weather   - текущая погода
        • get_forecast  - прогноз погоды
        
        📌 MCP Git (порт 8083):
        • get_current_branch  - текущая ветка
        • get_git_status      - статус репозитория
        • get_open_files      - изменённые файлы (git status)
        • get_ide_open_files  - файлы, открытые в IDE
        • get_recent_commits  - последние коммиты
        
        ═══════════════════════════════════════════════════════════════
        💡 ПРИМЕРЫ ЗАПРОСОВ
        ═══════════════════════════════════════════════════════════════
        
        • "Сколько будет 25 * 4?"
        • "Который сейчас час?"
        • "На какой ветке я сейчас?"
        • "Покажи последние коммиты"
        • "Какие файлы изменены?" (git status)
        • "Какие файлы открыты в IDE?" (вкладки Android Studio)
        • "Какая погода в Москве?"
        
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
}
