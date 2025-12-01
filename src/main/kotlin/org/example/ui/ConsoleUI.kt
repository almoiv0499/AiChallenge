package org.example.ui

import org.example.models.ChatResponse

object ConsoleUI {
    private const val SEPARATOR_WIDTH = 60
    private const val SEPARATOR_CHAR = '─'
    private const val HEADER_CHAR = '='
    fun printWelcome() {
        println("""
            ╔══════════════════════════════════════════════════════════════╗
            ║           🤖 GigaChat Agent - Терминальный чат 🤖            ║
            ╠══════════════════════════════════════════════════════════════╣
            ║  Команды:                                                    ║
            ║    /exit  - выход из программы                               ║
            ║    /clear - очистить историю разговора                       ║
            ║    /help  - показать справку                                 ║
            ╚══════════════════════════════════════════════════════════════╝
        """.trimIndent())
    }
    fun printHelp() {
        println("""
            
            📖 Справка по использованию GigaChat Agent:
            
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
            
        """.trimIndent())
    }
    fun printInitializing() = println("\n🔧 Инициализация...")
    fun printReady() = println("\n✅ Агент готов к работе! Введите ваш вопрос:\n")
    fun printGoodbye() = println("\n👋 До свидания!")
    fun printHistoryCleared() = println("✅ История очищена\n")
    fun printUserPrompt() = print("Вы: ")
    fun printUserMessage(message: String) {
        println()
        println(HEADER_CHAR.toString().repeat(SEPARATOR_WIDTH))
        println("👤 Пользователь: $message")
        println(HEADER_CHAR.toString().repeat(SEPARATOR_WIDTH))
    }
    fun printAssistantMessage(message: String) = println("\n🤖 Ассистент: $message")
    fun printResponse(response: ChatResponse) {
        println()
        println(SEPARATOR_CHAR.toString().repeat(SEPARATOR_WIDTH))
        println("📝 Ответ: ${response.response}")
        if (response.toolCalls.isNotEmpty()) {
            println("\n🔧 Использованные инструменты:")
            response.toolCalls.forEach { toolCall ->
                println("   • ${toolCall.toolName}: ${toolCall.result}")
            }
        }
        println(SEPARATOR_CHAR.toString().repeat(SEPARATOR_WIDTH))
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
        println("🤖 GigaChat Агент инициализирован")
        println("   Модель: $model")
        println("   Инструментов: $toolCount")
    }
    fun printToolRegistered(toolName: String) = println("📦 Зарегистрирован инструмент: $toolName")
    fun printHistoryClearedLog() = println("🗑️ История разговора очищена")
    fun printTokenObtained(expiresAt: String) = println("✅ Токен получен, действителен до: $expiresAt")
    fun printFetchingToken() = println("\n🔐 Получение токена доступа...")
    fun printSendingRequest(model: String, messageCount: Int, functionCount: Int) {
        println("\n📤 Отправка запроса к GigaChat...")
        println("   Модель: $model")
        println("   Сообщений: $messageCount")
        println("   Функций: $functionCount")
    }
    fun printResponseReceived(finishReason: String?, tokensUsed: Int?) {
        println("📥 Получен ответ от GigaChat")
        println("   Finish reason: $finishReason")
        println("   Токенов использовано: ${tokensUsed ?: "N/A"}")
    }
    fun printHttpLog(message: String) = println("🌐 HTTP: $message")
    fun printArgumentParseError(error: String?) = println("   ⚠️ Ошибка парсинга аргументов: $error")
}

