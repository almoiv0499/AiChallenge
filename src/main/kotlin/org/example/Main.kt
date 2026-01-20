package org.example

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.example.agent.OpenRouterAgent
import org.example.client.OpenRouterClient
import org.example.client.TokenLimitExceededException
import org.example.config.AppConfig
import org.example.config.OpenRouterConfig
import org.example.mcp.McpClient
import org.example.mcp.server.NotionMcpServer
import org.example.mcp.server.WeatherMcpServer
import org.example.mcp.server.GitMcpServer
import org.example.notion.NotionClient
import org.example.weather.WeatherClient
import org.example.tools.McpToolAdapter
import org.example.tools.ToolRegistry
import org.example.ui.ConsoleUI
import org.example.reminder.ReminderService
import org.example.reminder.TaskReminderScheduler
import org.example.storage.TaskStorage
import org.example.agent.android.DeviceSearchService
import org.example.embedding.EmbeddingClient
import org.example.embedding.DocumentIndexStorage
import org.example.embedding.RagService
import org.example.embedding.ProjectDocsIndexer
import org.example.project.ProjectTaskStorage
import org.example.project.ProjectTaskService
import org.example.project.ProjectTaskApiServer
import org.example.project.ProjectTaskClient
import org.example.project.CreateProjectTaskTool
import org.example.project.GetProjectTasksTool
import org.example.project.UpdateProjectTaskTool
import org.example.project.GetProjectStatusTool
import org.example.project.GetTeamCapacityTool
import org.example.client.ollama.OllamaClient
import org.example.client.ollama.OllamaChatService

fun main(args: Array<String>) = runBlocking {
    // Проверяем только аргумент help
    if (args.isNotEmpty() && args.any { it.lowercase() in listOf("--help", "-h", "/help", "/?") }) {
        printModeHelp()
        return@runBlocking
    }
    
    runApplication()
}

/**
 * Автоматически определяет режим работы приложения
 * По умолчанию использует Ollama (офлайн), если доступен
 * @return Mode.USE_OLLAMA или Mode.USE_OPENROUTER
 */
private suspend fun determineMode(): Mode {
    // Проверяем переменную окружения для принудительного выбора режима
    val forceMode = System.getenv("FORCE_MODE")?.uppercase()
    when (forceMode) {
        "OLLAMA", "1" -> {
            println("🔧 Принудительный режим: Ollama (из переменной окружения FORCE_MODE)")
            return Mode.USE_OLLAMA
        }
        "OPENROUTER", "2" -> {
            println("🔧 Принудительный режим: OpenRouter (из переменной окружения FORCE_MODE)")
            return Mode.USE_OPENROUTER
        }
    }
    
    // Проверяем переменную окружения USE_OLLAMA
    val useOllamaEnv = System.getenv("USE_OLLAMA")?.uppercase()
    if (useOllamaEnv == "FALSE" || useOllamaEnv == "0" || useOllamaEnv == "NO") {
        println("🔧 Режим OpenRouter (USE_OLLAMA=FALSE)")
        return Mode.USE_OPENROUTER
    }
    
    // По умолчанию пытаемся использовать Ollama (офлайн режим)
    println("🔍 Проверка доступности Ollama (локальная модель)...")
    val ollamaClient = OllamaClient()
    return try {
        val isAvailable = ollamaClient.isAvailable()
        ollamaClient.close()
        
        if (isAvailable) {
            println("✅ Ollama доступен - используется офлайн режим")
            Mode.USE_OLLAMA
        } else {
            println("⚠️ Ollama недоступен - переключение на OpenRouter")
            println("   Для использования офлайн режима:")
            println("   1. Установите Ollama: https://ollama.ai")
            println("   2. Запустите Ollama: ollama serve")
            println("   3. Установите модель: ollama pull llama3.2")
            println("   4. Перезапустите приложение")
            println()
            // Проверяем наличие API ключа для OpenRouter
            try {
                AppConfig.loadApiKey()
                Mode.USE_OPENROUTER
            } catch (e: Exception) {
                println("❌ КРИТИЧЕСКАЯ ОШИБКА: Не настроен ни Ollama, ни OpenRouter!")
                println("   Настройте один из вариантов:")
                println("   • Установите и запустите Ollama для офлайн режима")
                println("   • Или настройте OPENROUTER_API_KEY для облачного режима")
                throw RuntimeException("Не настроен ни один режим работы")
            }
        }
    } catch (e: Exception) {
        ollamaClient.close()
        println("⚠️ Ошибка при проверке Ollama: ${e.message}")
        println("   Переключение на OpenRouter...")
        try {
            AppConfig.loadApiKey()
            Mode.USE_OPENROUTER
        } catch (e2: Exception) {
            println("❌ КРИТИЧЕСКАЯ ОШИБКА: Не настроен ни Ollama, ни OpenRouter!")
            throw RuntimeException("Не настроен ни один режим работы", e2)
        }
    }
}

/**
 * Интерактивно запрашивает у пользователя выбор режима работы (используется только при FORCE_INTERACTIVE=true)
 * @return Mode.USE_OLLAMA или Mode.USE_OPENROUTER
 */
private suspend fun requestMode(): Mode {
    println()
    println("╔══════════════════════════════════════════════════════════════╗")
    println("║           🤖 Выберите режим работы приложения 🤖            ║")
    println("╠══════════════════════════════════════════════════════════════╣")
    println("║                                                              ║")
    println("║  1  🦙 Ollama - локальная модель (офлайн)                    ║")
    println("║  2  🌐 OpenRouter - облачная модель                          ║")
    println("║                                                              ║")
    println("╚══════════════════════════════════════════════════════════════╝")
    print("\nВыберите режим (1 или 2): ")
    
    while (true) {
        val input = readlnOrNull()?.trim()
        when (input) {
            "1" -> {
                println("✅ Выбран режим: Ollama\n")
                return Mode.USE_OLLAMA
            }
            "2" -> {
                println("✅ Выбран режим: OpenRouter\n")
                return Mode.USE_OPENROUTER
            }
            else -> {
                print("❌ Неверный ввод. Пожалуйста, введите 1 или 2: ")
            }
        }
    }
}

/**
 * Выводит справку по режимам работы приложения
 */
private fun printModeHelp() {
    println("""
        
        ╔══════════════════════════════════════════════════════════════╗
        ║              🤖 Режимы работы приложения 🤖                  ║
        ╠══════════════════════════════════════════════════════════════╣
        ║                                                              ║
        ║  По умолчанию приложение автоматически использует:            ║
        ║    🦙 Ollama (локальная модель, офлайн режим)               ║
        ║                                                              ║
        ║  Если Ollama недоступен, используется:                       ║
        ║    🌐 OpenRouter (облачная модель)                           ║
        ║                                                              ║
        ║  Переменные окружения для управления режимом:                ║
        ║    FORCE_MODE=OLLAMA      - принудительно использовать Ollama║
        ║    FORCE_MODE=OPENROUTER  - принудительно использовать OpenRouter║
        ║    USE_OLLAMA=FALSE       - отключить автоматическое использование Ollama║
        ║    OLLAMA_MODEL=<name>    - выбрать модель Ollama (по умолчанию: llama3.2)║
        ║                                                              ║
        ║  Аргументы командной строки:                                ║
        ║    --help, -h            Показать эту справку                ║
        ║                                                              ║
        ║  Настройка Ollama (офлайн режим):                            ║
        ║    1. Установите Ollama: https://ollama.ai                   ║
        ║    2. Запустите: ollama serve                                ║
        ║    3. Установите модель: ollama pull llama3.2                ║
        ║                                                              ║
        ╚══════════════════════════════════════════════════════════════╝
        
    """.trimIndent())
}

/**
 * Режим работы приложения
 */
private enum class Mode {
    USE_OLLAMA,      // Использовать Ollama
    USE_OPENROUTER   // Использовать OpenRouter (старая реализация)
}

private suspend fun runApplication() {
    // Проверка режима работы: интерактивный терминал или сервер
    val isServerMode = isServerMode()
    
    if (isServerMode) {
        // Серверный режим - инициализируем все сервисы
        val notionApiKey = AppConfig.loadNotionApiKey()
        val weatherApiKey = AppConfig.loadWeatherApiKey()
        val pageId = AppConfig.loadNotionPageId()
        ConsoleUI.printInitializing()
        startLocalServices(notionApiKey, weatherApiKey, pageId)
        delay(1000)
        
        println("🚀 Серверный режим: приложение работает как API сервер")
        println("   API endpoints доступны на портах:")
        println("   - Project Task API: http://localhost:8084/api")
        println("   - Notion MCP: http://localhost:8081")
        println("   - Weather MCP: http://localhost:8082")
        println("   - Git MCP: http://localhost:8083")
        println("")
        println("   Приложение работает в фоновом режиме. Для остановки используйте Ctrl+C или остановите контейнер.")
        
        // В серверном режиме просто держим приложение запущенным
        while (true) {
            delay(Long.MAX_VALUE)
        }
    } else {
        // Интерактивный режим для локальной разработки
        // Автоматически определяем режим: Ollama по умолчанию (офлайн)
        val mode = determineMode()
        
        when (mode) {
            Mode.USE_OLLAMA -> {
                ConsoleUI.printWelcomeOffline()
                println("\n🦙 Запуск в режиме Ollama (офлайн)...")
                println("   Инструменты, RAG и MCP серверы не инициализируются")
                runOllama()
            }
            Mode.USE_OPENROUTER -> {
                ConsoleUI.printWelcome()
                println("\n🌐 Запуск в режиме OpenRouter...")
                initializeOpenRouterServices()
            }
        }
    }
}

/**
 * Инициализирует все сервисы для режима OpenRouter: инструменты, RAG, MCP
 */
private suspend fun initializeOpenRouterServices() {
    val apiKey = AppConfig.loadApiKey()
    val notionApiKey = AppConfig.loadNotionApiKey()
    val weatherApiKey = AppConfig.loadWeatherApiKey()
    val databaseId = AppConfig.loadNotionDatabaseId()
    val pageId = AppConfig.loadNotionPageId()
    
    ConsoleUI.printInitializing()
    startLocalServices(notionApiKey, weatherApiKey, pageId)
    delay(1000)
    
    val client = OpenRouterClient(apiKey)
    val toolRegistry = ToolRegistry.createDefault()
    connectToLocalMcpServers(toolRegistry)
    
    // Register Project Task API tools
    try {
        val projectTaskClient = ProjectTaskClient()
        toolRegistry.register(CreateProjectTaskTool(projectTaskClient))
        toolRegistry.register(GetProjectTasksTool(projectTaskClient))
        toolRegistry.register(UpdateProjectTaskTool(projectTaskClient))
        toolRegistry.register(GetProjectStatusTool(projectTaskClient))
        toolRegistry.register(GetTeamCapacityTool(projectTaskClient))
        println("✅ Project Task API tools registered (5 tools)")
    } catch (e: Exception) {
        println("⚠️ Failed to register Project Task API tools: ${e.message}")
    }
    
    // Initialize device search service if Android SDK is configured
    val deviceSearchService = DeviceSearchService.create()
    if (deviceSearchService != null) {
        println("✅ Device search service initialized (Android emulator support enabled)")
    }
    
    // Initialize RAG service for local document search
    val embeddingClientForRag = try {
        EmbeddingClient(apiKey)
    } catch (e: Exception) {
        println("⚠️ Failed to initialize embedding client for RAG: ${e.message}")
        null
    }
    
    val ragService = embeddingClientForRag?.let { embClient ->
        try {
            val documentStorage = DocumentIndexStorage()
            val rag = RagService(embClient, documentStorage)
            if (rag.hasDocuments()) {
                println("✅ RAG service initialized (local document search enabled, ${rag.getDocumentCount()} documents indexed)")
            } else {
                println("⚠️ RAG service initialized but no documents in index.")
                println("📚 Attempting to auto-index project documentation...")
                try {
                    val indexer = ProjectDocsIndexer.create()
                    if (indexer != null) {
                        val indexedCount = indexer.indexProjectDocumentation()
                        if (indexedCount > 0) {
                            println("✅ Successfully indexed $indexedCount documentation files")
                        } else {
                            println("⚠️ No documentation files found to index")
                        }
                        indexer.close()
                    }
                } catch (e: Exception) {
                    println("⚠️ Failed to auto-index documentation: ${e.message}")
                    println("   You can manually index documents by running 'gradlew runIndexDocs'")
                }
            }
            rag
        } catch (e: Exception) {
            println("⚠️ Failed to initialize RAG service: ${e.message}")
            null
        }
    }
    
    val agent = OpenRouterAgent(
        client, 
        toolRegistry, 
        deviceSearchExecutor = deviceSearchService,
        ragService = ragService
    )
    ConsoleUI.printReady()
    
    runChatLoop(agent, client, notionApiKey, databaseId, embeddingClientForRag, ragService)
}

/**
 * Определяет, запущено ли приложение в серверном режиме
 * (без интерактивного терминала, например, в Docker или на Railway)
 */
private fun isServerMode(): Boolean {
    // Проверяем переменные окружения, указывающие на серверный режим
    val port = System.getenv("PORT")
    val railwayEnv = System.getenv("RAILWAY_ENVIRONMENT")
    val mode = System.getenv("MODE")?.uppercase()
    
    // Если установлен PORT (Railway всегда устанавливает) - это серверный режим
    if (port != null && port.isNotBlank()) {
        return true
    }
    
    // Если установлен RAILWAY_ENVIRONMENT - это Railway
    if (railwayEnv != null && railwayEnv.isNotBlank()) {
        return true
    }
    
    // Если MODE=server
    if (mode == "SERVER" || mode == "PRODUCTION") {
        return true
    }
    
    // Проверяем, доступен ли System.in как интерактивный терминал
    try {
        val systemIn = System.`in`
        // Если System.in недоступен или не является терминалом - серверный режим
        if (!systemIn.available().let { it >= 0 }) {
            return true
        }
    } catch (e: Exception) {
        // Если не можем проверить - считаем серверным режимом
        return true
    }
    
    return false
}

private suspend fun startLocalServices(notionApiKey: String, weatherApiKey: String, pageId: String?) {
    ConsoleUI.printStartingServices()
    val notionClient = NotionClient(notionApiKey)
    val databaseId = AppConfig.loadNotionDatabaseId()
    val reminderService = if (databaseId != null && databaseId.isNotBlank() && notionApiKey != "empty" && notionApiKey.isNotBlank()) {
        ReminderService(notionClient, databaseId)
    } else {
        null
    }
    val notionMcpServer = NotionMcpServer(notionClient, reminderService, pageId)
    embeddedServer(Netty, port = 8081) {
        notionMcpServer.configureMcpServer(this)
    }.start(wait = false)
    val weatherClient = WeatherClient(weatherApiKey)
    val weatherMcpServer = WeatherMcpServer(weatherClient)
    embeddedServer(Netty, port = 8082) {
        weatherMcpServer.configureMcpServer(this)
    }.start(wait = false)
    val gitMcpServer = GitMcpServer()
    embeddedServer(Netty, port = 8083) {
        gitMcpServer.configureMcpServer(this)
    }.start(wait = false)
    
    // Project Task API Server
    val projectTaskStorage = ProjectTaskStorage()
    val projectTaskService = ProjectTaskService(projectTaskStorage)
    val projectTaskApiServer = ProjectTaskApiServer(projectTaskService)
    embeddedServer(Netty, port = 8084) {
        projectTaskApiServer.configureApiServer(this)
    }.start(wait = false)
    println("✅ Project Task API Server: http://localhost:8084")
    
    ConsoleUI.printServicesStarted()
}

private suspend fun connectToLocalMcpServers(toolRegistry: ToolRegistry) {
    val mcpServers = listOf(
        "http://localhost:8081/mcp" to "Notion",
        "http://localhost:8082/mcp" to "Weather",
        "http://localhost:8083/mcp" to "Git"
    )
    var totalToolsRegistered = 0
    for ((mcpUrl, serverName) in mcpServers) {
        try {
            ConsoleUI.printMcpConnecting(mcpUrl)
            val mcpClient = McpClient.createHttp(baseUrl = mcpUrl)
            val initResult = mcpClient.initialize()
            ConsoleUI.printMcpConnected(initResult.serverInfo.name, initResult.serverInfo.version)
            val mcpTools = mcpClient.listTools()
            ConsoleUI.printMcpTools(mcpTools)
            for (mcpTool in mcpTools) {
                val adapter = McpToolAdapter(mcpTool, mcpClient)
                toolRegistry.register(adapter)
            }
            totalToolsRegistered += mcpTools.size
            ConsoleUI.printMcpToolsRegistered(mcpTools.size)
        } catch (e: Exception) {
            ConsoleUI.printMcpError("Ошибка подключения к $serverName MCP серверу: ${e.message ?: "Неизвестная ошибка"}")
            e.printStackTrace()
        }
    }
    if (totalToolsRegistered > 0) {
        println("✅ Всего зарегистрировано MCP инструментов: $totalToolsRegistered")
    }
}

private suspend fun runChatLoop(
    agent: OpenRouterAgent, 
    client: OpenRouterClient,
    notionApiKey: String,
    databaseId: String?,
    embeddingClientForRag: EmbeddingClient?,
    ragService: org.example.embedding.RagService?
) {
    var taskScheduler: TaskReminderScheduler? = null
    while (true) {
        ConsoleUI.printUserPrompt()
        val input = readlnOrNull()?.trim() ?: continue
        if (input.isEmpty()) continue
        when {
            isExitCommand(input) -> {
                ConsoleUI.printGoodbye()
                client.close()
                embeddingClientForRag?.close()
                return
            }
            isClearCommand(input) -> {
                agent.clearHistory()
                ConsoleUI.printHistoryCleared()
            }
            isHelpCommand(input) -> {
                ConsoleUI.printHelp()
            }
            isHelpByProjectCommand(input) -> {
                if (ragService != null && ragService.hasDocuments()) {
                    handleHelpByProject(agent, ragService, input)
                } else {
                    println("❌ RAG сервис не инициализирован или нет проиндексированных документов.")
                    println("   Запустите индексацию документации или проверьте настройки.")
                }
            }
            isToolsCommand(input) -> {
                OpenRouterConfig.ENABLE_TOOLS = !OpenRouterConfig.ENABLE_TOOLS
                ConsoleUI.printToolsStatus(OpenRouterConfig.ENABLE_TOOLS)
            }
            isTaskReminderCommand(input) -> {
                taskScheduler = toggleTaskReminder(notionApiKey, databaseId, taskScheduler)
            }
            isClearTasksCommand(input) -> {
                clearTasksDatabase()
            }
            else -> processUserMessage(agent, input)
        }
    }
}

private suspend fun processUserMessage(agent: OpenRouterAgent, input: String) {
    try {
        val response = agent.processMessage(input)
        ConsoleUI.printResponse(response)
    } catch (e: TokenLimitExceededException) {
        ConsoleUI.printTokenLimitExceeded()
    } catch (e: Exception) {
        ConsoleUI.printError(e.message)
        e.printStackTrace()
    }
}

private fun isExitCommand(input: String): Boolean =
    input.lowercase() in listOf("/exit", "/quit", "/q")

private fun isClearCommand(input: String): Boolean =
    input.lowercase() == "/clear"

private fun isHelpCommand(input: String): Boolean =
    input.lowercase() == "/?" || input.lowercase() == "/help" && !input.lowercase().contains(" ")

private fun isHelpByProjectCommand(input: String): Boolean =
    input.lowercase().startsWith("/help_by_project") || 
    input.lowercase().startsWith("/help-by-project") ||
    (input.lowercase().startsWith("/help ") && input.length > 6)

private fun isToolsCommand(input: String): Boolean =
    input.lowercase() in listOf("/tools", "/tool")

private fun isClearTasksCommand(input: String): Boolean =
    input.lowercase() in listOf("/clear-tasks", "/cleartasks", "/clear-tasks-db")

private fun isTaskReminderCommand(input: String): Boolean =
    input.lowercase() in listOf("/tasks", "/task-reminder", "/reminder")

/**
 * Toggles the task reminder scheduler on/off.
 * Returns the scheduler instance if started, null if stopped.
 */
private fun toggleTaskReminder(
    notionApiKey: String,
    databaseId: String?,
    currentScheduler: TaskReminderScheduler?
): TaskReminderScheduler? {
    if (currentScheduler != null || OpenRouterConfig.ENABLE_TASK_REMINDER) {
        // Stop scheduler
        OpenRouterConfig.ENABLE_TASK_REMINDER = false
        println("❌ Task reminder scheduler выключен")
        return null
    } else {
        // Start scheduler
        if (databaseId == null || databaseId.isBlank()) {
            println("❌ Не удалось запустить scheduler: NOTION_DATABASE_ID не настроен")
            return null
        }
        if (notionApiKey == "empty" || notionApiKey.isBlank()) {
            println("❌ Не удалось запустить scheduler: NOTION_API_KEY не настроен")
            return null
        }
        OpenRouterConfig.ENABLE_TASK_REMINDER = true
        val notionClient = NotionClient(notionApiKey)
        val reminderService = ReminderService(notionClient, databaseId)
        val scheduler = TaskReminderScheduler(reminderService)
        scheduler.start()
        println("✅ Task reminder scheduler включен (проверка каждые 10 секунд, вывод каждые 30 секунд)")
        return scheduler
    }
}

private fun clearTasksDatabase() {
    val taskStorage = TaskStorage()
    val deleted = taskStorage.clearAllTasks()
    if (deleted >= 0) {
        ConsoleUI.printTasksDatabaseCleared(deleted)
    } else {
        ConsoleUI.printTasksDatabaseError("Ошибка при очистке базы данных задач")
    }
}

/**
 * Обрабатывает команду /help - поиск по OpenRouterAgent.kt через RAG
 */
private suspend fun handleHelpByProject(
    agent: OpenRouterAgent,
    ragService: org.example.embedding.RagService,
    input: String
) {
    val question = input
        .substringAfter("/help_by_project")
        .substringAfter("/help-by-project")
        .substringAfter("/help")
        .trim()
    
    if (question.isBlank()) {
        printHelpUsage()
        return
    }
    
    // Проверяем запрос на показ конкретных строк
    val linesRequest = parseLinesRequest(question)
    if (linesRequest != null) {
        showCodeLines(linesRequest.first, linesRequest.second)
        return
    }
    
    println("\n🔍 Поиск в OpenRouterAgent.kt...")
    
    val searchResults = ragService.search(question, limit = 2, minSimilarity = 0.3)
    
    if (searchResults.isEmpty()) {
        println("❌ Информация не найдена.")
        println("💡 Попробуйте: /help processMessage, /help executeAgentLoop, /help системный промпт")
        return
    }
    
    val bestResult = searchResults.first()
    val lines = bestResult.metadata["lines"] ?: ""
    
    // Формируем краткий контекст
    val context = searchResults.joinToString("\n\n") { it.text.take(600) }
    
    val prompt = """
        Вопрос: "$question"
        
        Код из OpenRouterAgent.kt:
        $context
        
        ИНСТРУКЦИИ:
        1. Дай КРАТКИЙ ответ (2-4 предложения)
        2. Если нужен код - покажи только КЛЮЧЕВЫЕ 5-10 строк
        3. НЕ копируй весь контекст
        
        Формат:
        📝 [краткое описание]
        
        ```kotlin
        [только ключевой фрагмент если нужен]
        ```
    """.trimIndent()
    
    try {
        val response = agent.processMessage(prompt)
        println("\n💬 Ответ:")
        println(response.response)
        println("\n📍 Источник: ${bestResult.title} (строки $lines)")
    } catch (e: Exception) {
        // Fallback
        println("\n📖 ${bestResult.title}:")
        println("${"─".repeat(50)}")
        println(bestResult.text.take(400))
        println("...")
    }
}

/**
 * Парсит запрос на показ строк: "строки 100-200"
 */
private fun parseLinesRequest(question: String): Pair<Int, Int>? {
    val pattern = Regex("""строк[иа]?\s+(\d+)\s*[-–]\s*(\d+)""")
    val match = pattern.find(question.lowercase())
    return match?.let {
        Pair(it.groupValues[1].toInt(), it.groupValues[2].toInt())
    }
}

/**
 * Показывает строки из OpenRouterAgent.kt
 */
private fun showCodeLines(startLine: Int, endLine: Int) {
    val file = java.io.File("src/main/kotlin/org/example/agent/OpenRouterAgent.kt")
    if (!file.exists()) {
        println("❌ Файл OpenRouterAgent.kt не найден")
        return
    }
    
    val lines = file.readLines()
    val actualStart = maxOf(1, startLine)
    val actualEnd = minOf(endLine, lines.size)
    
    println("\n📄 OpenRouterAgent.kt (строки $actualStart-$actualEnd из ${lines.size})")
    println("${"─".repeat(60)}")
    
    for (i in (actualStart - 1) until actualEnd) {
        println("${(i + 1).toString().padStart(4)}│ ${lines[i]}")
    }
    
    println("${"─".repeat(60)}")
}

private fun printHelpUsage() {
    println("""
        
    📖 Справка по OpenRouterAgent.kt
    
    🔍 Вопросы о коде:
      • /help для чего нужен OpenRouterAgent
      • /help что делает processMessage
      • /help как работает executeAgentLoop
      • /help парсинг function_call
      • /help системный промпт
      • /help сжатие истории
      
    📂 Просмотр кода:
      • /help строки 1-50
      • /help строки 100-200
      
    """.trimIndent())
}

/**
 * Проверяет, должна ли использоваться Ollama вместо OpenRouter
 * Проверяет переменную окружения USE_OLLAMA или доступность Ollama API
 */
private suspend fun shouldUseOllama(): Boolean {
    val useOllamaEnv = System.getenv("USE_OLLAMA")?.uppercase()
    if (useOllamaEnv == "TRUE" || useOllamaEnv == "1" || useOllamaEnv == "YES") {
        return true
    }
    if (useOllamaEnv == "FALSE" || useOllamaEnv == "0" || useOllamaEnv == "NO") {
        return false
    }
    
    // Если не указано явно, проверяем доступность Ollama
    val ollamaClient = OllamaClient()
    return try {
        val isAvailable = ollamaClient.isAvailable()
        ollamaClient.close()
        if (isAvailable) {
            println("✅ Обнаружен локальный Ollama API")
            println("   Используется Ollama для общения. Для переключения на OpenRouter установите USE_OLLAMA=FALSE")
            true
        } else {
            false
        }
    } catch (e: Exception) {
        ollamaClient.close()
        false
    }
}

/**
 * Запускает локальную модель Ollama для общения в терминале
 * Вся логика работы с Ollama находится в этой функции
 * @return true если Ollama успешно запущен и готов к работе, false если произошла ошибка
 */
private suspend fun runOllama(): Boolean {
    println("\n🦙 Инициализация Ollama (офлайн режим)...")
    
    val baseUrl = System.getenv("OLLAMA_BASE_URL") ?: "http://localhost:11434/api"
    val ollamaClient = OllamaClient(baseUrl = baseUrl)
    
    // Проверяем доступность
    if (!ollamaClient.isAvailable()) {
        println("❌ Ollama API недоступен на $baseUrl")
        println()
        println("📋 Инструкции по настройке офлайн режима:")
        println("   1. Установите Ollama: https://ollama.ai")
        println("   2. Запустите Ollama сервер:")
        println("      • Windows: скачайте и запустите Ollama.exe")
        println("      • Linux/Mac: ollama serve")
        println("   3. Установите модель:")
        println("      ollama pull llama3.2")
        println("   4. Перезапустите приложение")
        println()
        println("   Или используйте облачный режим:")
        println("   • Установите переменную окружения USE_OLLAMA=FALSE")
        println("   • Настройте OPENROUTER_API_KEY")
        ollamaClient.close()
        return false
    }
    
    // Получаем список доступных моделей
    val models = try {
        ollamaClient.listModels()
    } catch (e: Exception) {
        println("⚠️ Не удалось получить список моделей: ${e.message}")
        emptyList()
    }
    
    // Выбираем модель
    val defaultModel = if (models.isNotEmpty()) {
        println("\n📋 Доступные модели:")
        models.forEachIndexed { index, model ->
            println("   ${index + 1}. ${model.name}")
        }
        val modelName = System.getenv("OLLAMA_MODEL") ?: models.first().name
        println("\n✅ Используется модель: $modelName")
        if (System.getenv("OLLAMA_MODEL") == null && models.isNotEmpty()) {
            println("   Для выбора другой модели установите переменную окружения OLLAMA_MODEL")
        }
        modelName
    } else {
        val modelName = System.getenv("OLLAMA_MODEL") ?: "llama3.2"
        println("\n✅ Используется модель: $modelName (по умолчанию)")
        modelName
    }
    
    // Создаем системный промпт и сервис
    val systemPrompt = """
        Ты полезный AI-ассистент. Отвечай на вопросы пользователя кратко и по делу.
        Используй дружелюбный и профессиональный тон.
    """.trimIndent()
    
    val chatService = OllamaChatService(
        ollamaClient = ollamaClient,
        model = defaultModel,
        systemPrompt = systemPrompt
    )
    
    println("\n✅ Ollama готов к работе! Модель: $defaultModel")
    println("   🌐 Режим: Офлайн (работает без интернета)")
    println("   Введите ваш вопрос:\n")
    
    // Запускаем цикл общения
    while (true) {
        ConsoleUI.printUserPrompt()
        val input = readlnOrNull()?.trim() ?: continue
        if (input.isEmpty()) continue
        
        when {
            isExitCommand(input) -> {
                ConsoleUI.printGoodbye()
                ollamaClient.close()
                return true
            }
            isClearCommand(input) -> {
                chatService.clearHistory()
                ConsoleUI.printHistoryCleared()
            }
            isHelpCommand(input) -> {
                printOllamaHelp()
            }
            isModelsCommand(input) -> {
                printOllamaModels(ollamaClient)
            }
            isRunningModelsCommand(input) -> {
                printRunningOllamaModels(ollamaClient)
            }
            else -> {
                try {
                    val response = chatService.processMessage(input)
                    ConsoleUI.printResponse(response)
                } catch (e: Exception) {
                    ConsoleUI.printError(e.message)
                    println("\n⚠️ Произошла ошибка при общении с Ollama")
                    println("   Возможные причины:")
                    println("   • Ollama сервер остановлен - проверьте, что Ollama запущен")
                    println("   • Модель не установлена - выполните: ollama pull $defaultModel")
                    println("   • Недостаточно памяти - попробуйте меньшую модель")
                    println("   • Проблемы с сетью - проверьте подключение к localhost:11434")
                    println()
                    println("   Для отладки:")
                    println("   • Проверьте статус: curl http://localhost:11434/api/tags")
                    println("   • Перезапустите Ollama: ollama serve")
                    println()
                    println("   Или используйте облачный режим:")
                    println("   • Установите USE_OLLAMA=FALSE и настройте OPENROUTER_API_KEY")
                }
            }
        }
    }
}

/**
 * Проверяет, является ли команда запросом списка моделей
 */
private fun isModelsCommand(input: String): Boolean =
    input.lowercase() in listOf("/models", "/model-list", "/list-models")

/**
 * Проверяет, является ли команда запросом списка запущенных моделей
 */
private fun isRunningModelsCommand(input: String): Boolean =
    input.lowercase() in listOf("/running", "/running-models", "/ps", "/list-running")

/**
 * Выводит список доступных моделей Ollama
 */
private suspend fun printOllamaModels(ollamaClient: OllamaClient) {
    try {
        val models = ollamaClient.listModels()
        if (models.isEmpty()) {
            println("📋 Модели не найдены. Установите модель командой: ollama pull <model-name>")
        } else {
            println("\n📋 Доступные модели Ollama:")
            models.forEachIndexed { index, model ->
                val size = model.size?.let { 
                    val gb = it / 1_000_000_000.0
                    if (gb >= 1) String.format("%.2fGB", gb) else "${it / 1_000_000}MB"
                } ?: "N/A"
                
                val details = model.details
                val paramSize = details?.parameterSize ?: "N/A"
                val family = details?.family ?: details?.families?.firstOrNull() ?: "N/A"
                
                println("   ${index + 1}. ${model.name}")
                println("      └─ Размер: $size | Параметры: $paramSize | Семейство: $family")
                if (details?.quantizationLevel != null) {
                    println("      └─ Квантование: ${details.quantizationLevel}")
                }
            }
            println()
        }
    } catch (e: Exception) {
        println("❌ Ошибка при получении списка моделей: ${e.message}")
    }
}

/**
 * Выводит список запущенных моделей Ollama (загруженных в память)
 */
private suspend fun printRunningOllamaModels(ollamaClient: OllamaClient) {
    try {
        val runningModels = ollamaClient.listRunningModels()
        if (runningModels.isEmpty()) {
            println("📋 Нет запущенных моделей в памяти")
            println("   Модели загружаются автоматически при первом использовании")
        } else {
            println("\n🔄 Запущенные модели Ollama (в памяти):")
            runningModels.forEachIndexed { index, model ->
                val sizeVram = model.sizeVram?.let { 
                    val gb = it / 1_000_000_000.0
                    if (gb >= 1) String.format("%.2fGB", gb) else "${it / 1_000_000}MB"
                } ?: "N/A"
                
                val contextLength = model.contextLength ?: "N/A"
                val expiresAt = model.expiresAt?.let { 
                    // Парсим ISO 8601 дату и показываем оставшееся время
                    "до $it"
                } ?: "не указано"
                
                println("   ${index + 1}. ${model.model}")
                println("      └─ VRAM: $sizeVram | Контекст: $contextLength токенов | Истекает: $expiresAt")
                
                val details = model.details
                if (details != null) {
                    val paramSize = details.parameterSize ?: "N/A"
                    val family = details.family ?: details.families?.firstOrNull() ?: "N/A"
                    println("      └─ Параметры: $paramSize | Семейство: $family")
                }
            }
            println()
        }
    } catch (e: Exception) {
        println("❌ Ошибка при получении списка запущенных моделей: ${e.message}")
    }
}

/**
 * Выводит справку по использованию Ollama чата
 */
private fun printOllamaHelp() {
    println("""
        
        📖 Справка по использованию Ollama чата:
        
        ═══════════════════════════════════════════════════════════════
        📋 КОМАНДЫ
        ═══════════════════════════════════════════════════════════════
        
        🔹 Основные команды:
        • /exit            - выход из программы
        • /clear           - очистить историю разговора
        • /help            - эта справка
        • /models          - показать список доступных моделей
        • /running         - показать список запущенных моделей (в памяти)
        
        ═══════════════════════════════════════════════════════════════
        🔧 НАСТРОЙКИ
        ═══════════════════════════════════════════════════════════════
        
        🔹 Переменные окружения:
        • USE_OLLAMA=TRUE/FALSE  - использовать Ollama (по умолчанию проверяется автоматически)
        • OLLAMA_MODEL=<name>    - выбрать модель (по умолчанию: llama3.2)
        • OLLAMA_BASE_URL=<url>  - URL Ollama API (по умолчанию: http://localhost:11434/api)
        
        🔹 Установка моделей:
        • ollama pull llama3.2
        • ollama pull mistral
        • ollama pull codellama
        
        ═══════════════════════════════════════════════════════════════
        💡 ПРИМЕРЫ
        ═══════════════════════════════════════════════════════════════
        
        • "Привет! Расскажи о себе"
        • "Что такое Kotlin?"
        • "Напиши простую функцию на Kotlin для сортировки массива"
        • "Объясни концепцию корутин в Kotlin"
        
        """.trimIndent())
}

/**
 * Утилита для валидации конфигурации.
 * TODO: добавить проверку на пустые значения
 */
fun validateConfig(config: Map<String, String>): Boolean {
    // Потенциальная проблема: не проверяем на null
    val apiKey = config["API_KEY"]
    val baseUrl = config["BASE_URL"]
    
    // Логическая проблема: проверяем только apiKey, но возвращаем true даже если baseUrl пустой
    if (apiKey != null && apiKey.isNotEmpty()) {
        println("Config validated: API_KEY=${apiKey.take(10)}...")
        return true
    }
    return false
}
