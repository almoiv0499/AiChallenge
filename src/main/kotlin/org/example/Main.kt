package org.example

import kotlinx.coroutines.runBlocking
import org.example.agent.OpenRouterAgent
import org.example.client.OpenRouterClient
import org.example.client.TokenLimitExceededException
import org.example.config.AppConfig
import org.example.config.McpConfig
import org.example.config.OpenRouterConfig
import org.example.mcp.McpClient
import org.example.tools.ToolRegistry
import org.example.ui.ConsoleUI

fun main() = runBlocking {
    ConsoleUI.printWelcome()
    val apiKey = AppConfig.loadApiKey()
    ConsoleUI.printInitializing()
    val client = OpenRouterClient(apiKey)
    val toolRegistry = ToolRegistry.createDefault()
    val agent = OpenRouterAgent(client, toolRegistry)
    connectToMcpServer()
    ConsoleUI.printReady()
    runChatLoop(agent, client)
}

private suspend fun connectToMcpServer() {
    try {
        val mcpUrl = McpConfig.MCP_HTTP_URL
        ConsoleUI.printMcpConnecting(mcpUrl)
        val mcpClient = McpClient(baseUrl = mcpUrl)
        val initResult = mcpClient.initialize()
        ConsoleUI.printMcpConnected(initResult.serverInfo.name, initResult.serverInfo.version)
        val tools = mcpClient.listTools()
        ConsoleUI.printMcpTools(tools)
        mcpClient.close()
    } catch (e: Exception) {
        ConsoleUI.printMcpError(e.message ?: "Неизвестная ошибка")
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
