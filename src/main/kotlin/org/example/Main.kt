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
import org.example.embedding.RelevanceReranker
import org.example.embedding.RerankingStrategy

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
            // Инициализируем reranker с порогом по умолчанию
            val defaultThreshold = AppConfig.loadRerankerThreshold() ?: 0.7
            val reranker = RelevanceReranker(
                RerankingStrategy.ThresholdBased(threshold = defaultThreshold)
            )
            val rag = RagService(
                embClient, 
                documentStorage,
                reranker = reranker,
                useReranker = true
            )
            if (rag.hasDocuments()) {
                println("✅ RAG service initialized (local document search enabled)")
                println("   📊 Фильтр релевантности: включен (порог: ${String.format("%.2f", defaultThreshold)})")
            } else {
                println("⚠️ RAG service initialized but no documents in index. Run 'gradlew runIndexDocs' to index documents.")
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
    ConsoleUI.printServicesStarted()
}

private suspend fun connectToLocalMcpServers(toolRegistry: ToolRegistry) {
    val mcpServers = listOf(
        "http://localhost:8081/mcp" to "Notion",
        "http://localhost:8082/mcp" to "Weather"
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
    ragService: RagService?
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
            isHelpCommand(input) -> ConsoleUI.printHelp()
            isToolsCommand(input) -> {
                OpenRouterConfig.ENABLE_TOOLS = !OpenRouterConfig.ENABLE_TOOLS
                ConsoleUI.printToolsStatus(OpenRouterConfig.ENABLE_TOOLS)
            }
            isRagCommand(input) -> {
                agent.setRagEnabled(!agent.isRagEnabled())
                ConsoleUI.printRagModeStatus(agent.isRagEnabled())
            }
            isRagCompareCommand(input) -> {
                agent.setComparisonMode(!agent.isComparisonMode())
                ConsoleUI.printComparisonModeStatus(agent.isComparisonMode())
            }
            isRerankerCommand(input) -> {
                val currentRagService = agent.getRagService()
                if (currentRagService != null) {
                    val currentThreshold = currentRagService.getRerankerThreshold()
                    val isEnabled = currentThreshold != null
                    val newRagService = currentRagService.setRerankerEnabled(!isEnabled)
                    agent.updateRagService(newRagService)
                    ConsoleUI.printRerankerModeStatus(!isEnabled)
                } else {
                    println("⚠️ RAG сервис не инициализирован")
                }
            }
            isRerankerCompareCommand(input) -> {
                agent.setRerankerComparisonMode(!agent.isRerankerComparisonMode())
                ConsoleUI.printRerankerModeStatus(agent.isRerankerComparisonMode())
            }
            isRerankerThresholdCommand(input) -> {
                val threshold = extractThreshold(input)
                val currentRagService = agent.getRagService()
                if (threshold != null && currentRagService != null) {
                    val updatedRagService = currentRagService.updateRerankerThreshold(threshold)
                    agent.updateRagService(updatedRagService)
                    ConsoleUI.printRerankerThreshold(threshold)
                } else if (threshold == null) {
                    println("❌ Неверный формат. Используйте: /reranker-threshold <число от 0.0 до 1.0>")
                } else {
                    println("⚠️ RAG сервис не инициализирован")
                }
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
    input.lowercase() in listOf("/help", "/?")

private fun isToolsCommand(input: String): Boolean =
    input.lowercase() in listOf("/tools", "/tool")

private fun isClearTasksCommand(input: String): Boolean =
    input.lowercase() in listOf("/clear-tasks", "/cleartasks", "/clear-tasks-db")

private fun isTaskReminderCommand(input: String): Boolean =
    input.lowercase() in listOf("/tasks", "/task-reminder", "/reminder")

private fun isRagCommand(input: String): Boolean =
    input.lowercase() in listOf("/rag", "/rag-mode", "/rag-toggle")

private fun isRagCompareCommand(input: String): Boolean =
    input.lowercase() in listOf("/rag-compare", "/ragcompare", "/compare-rag", "/compare")

private fun isRerankerCommand(input: String): Boolean =
    input.lowercase() in listOf("/reranker", "/reranker-toggle", "/reranker-mode")

private fun isRerankerCompareCommand(input: String): Boolean =
    input.lowercase() in listOf("/reranker-compare", "/rerankercompare", "/compare-reranker")

private fun isRerankerThresholdCommand(input: String): Boolean =
    input.lowercase().startsWith("/reranker-threshold") || input.lowercase().startsWith("/reranker-threshold")

private fun extractThreshold(input: String): Double? {
    val parts = input.trim().split(Regex("\\s+"))
    if (parts.size < 2) return null
    return parts[1].toDoubleOrNull()?.coerceIn(0.0, 1.0)
}

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

