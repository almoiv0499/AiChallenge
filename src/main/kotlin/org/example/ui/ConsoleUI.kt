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
        ║    /exit  - выход из программы                               ║
        ║    /clear - очистить историю разговора                       ║
        ║    /help  - показать справку                                 ║
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
        • /exit  - выход
        • /clear - очистить историю
        • /help  - эта справка
        
        """.trimIndent()
    )

    fun printInitializing() = println("\n🔧 Инициализация...")
    fun printReady() = println("\n✅ Агент готов к работе! Введите ваш вопрос:\n")
    fun printGoodbye() = println("\n👋 До свидания!")
    fun printHistoryCleared() = println("✅ История очищена\n")
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
        println("\n🔄 DEBUG: Итерация $current из $max")
    }

    fun printDebugOutputItems(items: List<Any>) {
        println("🔍 DEBUG: Получено ${items.size} элементов в output:")
        items.forEachIndexed { index, item -> println("   [$index] $item") }
    }

    fun printResponseReceived(temperature: Double?, finishReason: String?, tokensUsed: Int?, responseTimeMs: Long?) {
        println("📥 Получен ответ от OpenRouter")
        println("   Статус: $finishReason")
        println("   Токенов использовано: ${tokensUsed ?: "N/A"}")
        println("   ⏱️ Время ответа: ${responseTimeMs?.let { "${it}ms" } ?: "N/A"}")
        println("🌡️ Temperature: $temperature")
    }

    private fun printSeparator(char: Char) = println(char.toString().repeat(SEPARATOR_WIDTH))

    private fun printToolCallsIfPresent(response: ChatResponse) {
        if (response.toolCalls.isEmpty()) return
        println("\n🔧 Использованные инструменты:")
        response.toolCalls.forEach { println("   • ${it.toolName}: ${it.result}") }
    }
}
