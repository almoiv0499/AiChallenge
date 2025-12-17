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
    ConsoleUI.printInitializing()
    startLocalServices(notionApiKey, weatherApiKey)
    delay(1000)
    
    // Start background task reminder scheduler if database ID is configured
    if (databaseId != null && databaseId.isNotBlank()) {
        if (notionApiKey != "empty" && notionApiKey.isNotBlank()) {
            startTaskReminderScheduler(notionApiKey, databaseId)
        }
    }
    
    val client = OpenRouterClient(apiKey)
    val toolRegistry = ToolRegistry.createDefault()
    connectToLocalMcpServers(toolRegistry)
    val agent = OpenRouterAgent(client, toolRegistry)
    ConsoleUI.printReady()
    runChatLoop(agent, client)
}

private suspend fun startLocalServices(notionApiKey: String, weatherApiKey: String) {
    ConsoleUI.printStartingServices()
    val notionClient = NotionClient(notionApiKey)
    val databaseId = AppConfig.loadNotionDatabaseId()
    val reminderService = if (databaseId != null && databaseId.isNotBlank() && notionApiKey != "empty" && notionApiKey.isNotBlank()) {
        ReminderService(notionClient, databaseId)
    } else {
        null
    }
    val notionMcpServer = NotionMcpServer(notionClient, reminderService)
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

private suspend fun runChatLoop(agent: OpenRouterAgent, client: OpenRouterClient) {
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

/**
 * Starts the background task reminder scheduler.
 * The scheduler runs continuously in a separate coroutine scope and never terminates.
 */
private fun startTaskReminderScheduler(notionApiKey: String, databaseId: String) {
    val notionClient = NotionClient(notionApiKey)
    val reminderService = ReminderService(notionClient, databaseId)
    val scheduler = TaskReminderScheduler(reminderService)
    scheduler.start()
    println("✅ Task reminder scheduler started (runs every 10 seconds)")
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
