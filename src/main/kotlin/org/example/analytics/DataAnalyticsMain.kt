package org.example.analytics

import kotlinx.coroutines.runBlocking
import org.example.client.ollama.OllamaClient

/**
 * CLI интерфейс для локального аналитика данных
 */
fun main() = runBlocking {
    printWelcome()

    val ollamaClient = OllamaClient()

    // Проверяем доступность Ollama
    if (!ollamaClient.isAvailable()) {
        println("❌ Ollama недоступен. Убедитесь, что Ollama запущен (ollama serve)")
        return@runBlocking
    }

    println("✅ Ollama доступен")

    // Выбираем модель
    val model = selectModel(ollamaClient)
    println("📌 Используется модель: $model")
    println()

    val analyzer = DataAnalyzerService(ollamaClient, model)

    println("Введите команду (help для справки):")
    println()

    while (true) {
        print("analytics> ")
        val input = readlnOrNull()?.trim() ?: continue

        if (input.isBlank()) continue

        try {
            when {
                input == "help" || input == "?" -> printHelp()
                input == "exit" || input == "quit" -> {
                    println("👋 До свидания!")
                    break
                }
                input.startsWith("load ") -> {
                    val path = input.removePrefix("load ").trim()
                    handleLoad(analyzer, path)
                }
                input == "info" -> handleInfo(analyzer)
                input.startsWith("ask ") -> {
                    val question = input.removePrefix("ask ").trim()
                    handleAsk(analyzer, question)
                }
                input == "stats" -> handleStats(analyzer)
                input == "anomalies" -> handleAnomalies(analyzer)
                input == "patterns" -> handlePatterns(analyzer)
                input == "errors" -> handleErrors(analyzer)
                input.startsWith("export ") -> {
                    val path = input.removePrefix("export ").trim()
                    handleExport(analyzer, path)
                }
                input == "clear" -> {
                    analyzer.clearSession()
                    println("✅ Сессия очищена")
                }
                input == "history" -> handleHistory(analyzer)
                else -> {
                    // Если это не команда, считаем это вопросом
                    if (analyzer.hasLoadedData()) {
                        handleAsk(analyzer, input)
                    } else {
                        println("❌ Неизвестная команда: $input")
                        println("   Используйте 'help' для списка команд или 'load <путь>' для загрузки данных")
                    }
                }
            }
        } catch (e: Exception) {
            println("❌ Ошибка: ${e.message}")
        }

        println()
    }

    ollamaClient.close()
}

private fun printWelcome() {
    println("""
╔══════════════════════════════════════════════════════════════╗
║       📊 Локальный Аналитик Данных (Ollama) 📊               ║
╠══════════════════════════════════════════════════════════════╣
║  Анализ CSV, JSON, логов с помощью локальной LLM             ║
║  Полностью офлайн, без облака                                ║
╠══════════════════════════════════════════════════════════════╣
║  Команды: help | load <путь> | ask <вопрос> | exit           ║
╚══════════════════════════════════════════════════════════════╝
    """.trimIndent())
    println()
}

private fun printHelp() {
    println("""
📖 Справка по командам:

═══════════════════════════════════════════════════════════════
📂 ЗАГРУЗКА ДАННЫХ
═══════════════════════════════════════════════════════════════
  load <путь>       - загрузить файл (CSV, JSON, JSONL, LOG)
  info              - показать информацию о загруженных данных
  clear             - очистить сессию

═══════════════════════════════════════════════════════════════
❓ АНАЛИЗ ДАННЫХ
═══════════════════════════════════════════════════════════════
  ask <вопрос>      - задать вопрос по данным
  <вопрос>          - можно писать вопрос напрямую (без 'ask')
  stats             - быстрый статистический анализ
  anomalies         - поиск аномалий в данных
  patterns          - поиск паттернов и закономерностей
  errors            - анализ ошибок (для логов)

═══════════════════════════════════════════════════════════════
📤 ЭКСПОРТ
═══════════════════════════════════════════════════════════════
  export <путь>     - экспорт результатов в Markdown файл
  history           - показать историю вопросов

═══════════════════════════════════════════════════════════════
🔧 ПРОЧЕЕ
═══════════════════════════════════════════════════════════════
  help, ?           - эта справка
  exit, quit        - выход

═══════════════════════════════════════════════════════════════
💡 ПРИМЕРЫ
═══════════════════════════════════════════════════════════════
  load data.csv
  ask сколько уникальных пользователей?
  ask какой тип ошибки самый частый?
  stats
  export report.md
    """.trimIndent())
}

private suspend fun selectModel(client: OllamaClient): String {
    return try {
        val models = client.listModels()
        if (models.isEmpty()) {
            println("⚠️ Нет доступных моделей. Используется llama3.2 по умолчанию")
            "llama3.2"
        } else {
            println("📋 Доступные модели:")
            models.forEachIndexed { index, model ->
                val sizeGb = (model.size ?: 0L) / (1024 * 1024 * 1024.0)
                println("   ${index + 1}. ${model.name} (${String.format("%.1f", sizeGb)} GB)")
            }
            println()
            print("Выберите модель (Enter для первой): ")
            val choice = readlnOrNull()?.trim()

            when {
                choice.isNullOrBlank() -> models.first().name
                choice.toIntOrNull() != null -> {
                    val index = choice.toInt() - 1
                    if (index in models.indices) models[index].name else models.first().name
                }
                else -> choice // Используем введённое имя напрямую
            }
        }
    } catch (e: Exception) {
        println("⚠️ Не удалось получить список моделей: ${e.message}")
        "llama3.2"
    }
}

private fun handleLoad(analyzer: DataAnalyzerService, path: String) {
    println("📂 Загрузка файла: $path")

    val data = analyzer.loadData(path)

    println("✅ Файл загружен успешно!")
    println()
    println(data.getSummary())
}

private fun handleInfo(analyzer: DataAnalyzerService) {
    println(analyzer.getDataInfo())
}

private suspend fun handleAsk(analyzer: DataAnalyzerService, question: String) {
    if (!analyzer.hasLoadedData()) {
        println("❌ Сначала загрузите данные командой 'load <путь>'")
        return
    }

    println("🤔 Анализирую...")
    println()

    val response = analyzer.ask(question)

    println("─".repeat(60))
    println(response.answer)
    println("─".repeat(60))

    if (response.suggestions.isNotEmpty()) {
        println()
        println("💡 Предложения для дальнейшего анализа:")
        response.suggestions.forEach { println("   • $it") }
    }
}

private suspend fun handleStats(analyzer: DataAnalyzerService) {
    if (!analyzer.hasLoadedData()) {
        println("❌ Сначала загрузите данные")
        return
    }

    println("📊 Выполняю статистический анализ...")
    println()

    val response = analyzer.analyzeStatistics()
    println("─".repeat(60))
    println(response.answer)
    println("─".repeat(60))
}

private suspend fun handleAnomalies(analyzer: DataAnalyzerService) {
    if (!analyzer.hasLoadedData()) {
        println("❌ Сначала загрузите данные")
        return
    }

    println("🔍 Ищу аномалии...")
    println()

    val response = analyzer.findAnomalies()
    println("─".repeat(60))
    println(response.answer)
    println("─".repeat(60))
}

private suspend fun handlePatterns(analyzer: DataAnalyzerService) {
    if (!analyzer.hasLoadedData()) {
        println("❌ Сначала загрузите данные")
        return
    }

    println("🔎 Ищу паттерны...")
    println()

    val response = analyzer.findPatterns()
    println("─".repeat(60))
    println(response.answer)
    println("─".repeat(60))
}

private suspend fun handleErrors(analyzer: DataAnalyzerService) {
    if (!analyzer.hasLoadedData()) {
        println("❌ Сначала загрузите данные")
        return
    }

    println("⚠️ Анализирую ошибки...")
    println()

    val response = analyzer.analyzeErrors()
    println("─".repeat(60))
    println(response.answer)
    println("─".repeat(60))
}

private fun handleExport(analyzer: DataAnalyzerService, path: String) {
    analyzer.exportResults(path)
    println("✅ Результаты экспортированы в: $path")
}

private fun handleHistory(analyzer: DataAnalyzerService) {
    val history = analyzer.getConversationHistory()

    if (history.isEmpty()) {
        println("История пуста")
        return
    }

    println("📜 История диалога:")
    println()

    for ((index, entry) in history.withIndex()) {
        val icon = if (entry.role == "user") "👤" else "🤖"
        val preview = if (entry.content.length > 100) {
            entry.content.take(100) + "..."
        } else {
            entry.content
        }
        println("${index + 1}. $icon $preview")
    }
}
