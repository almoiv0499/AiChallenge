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
    val agent = OpenRouterAgent(client, toolRegistry)
    ConsoleUI.printReady()
    runChatLoop(agent, client, notionApiKey, databaseId)
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
            val mcpClient = McpClient(baseUrl = mcpUrl)
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
    databaseId: String?
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

