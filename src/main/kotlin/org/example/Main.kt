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

fun main() = runBlocking {
    ConsoleUI.printWelcome()
    val apiKey = AppConfig.loadApiKey()
    val notionApiKey = AppConfig.loadNotionApiKey()
    val weatherApiKey = AppConfig.loadWeatherApiKey()
    val databaseId = AppConfig.loadNotionDatabaseId()
    val pageId = AppConfig.loadNotionPageId()
    val weatherLat = AppConfig.loadWeatherLatitude()
    val weatherLon = AppConfig.loadWeatherLongitude()
    ConsoleUI.printInitializing()
    startLocalServices(notionApiKey, weatherApiKey, pageId)
    delay(1000)
    
    val client = OpenRouterClient(apiKey)
    val toolRegistry = ToolRegistry.createDefault()
    connectToLocalMcpServers(toolRegistry)
    
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
