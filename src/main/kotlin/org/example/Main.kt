package org.example

import kotlinx.coroutines.runBlocking
import org.example.agent.GigaChatAgent
import org.example.client.GigaChatClient
import org.example.config.AppConfig
import org.example.tools.GigaChatToolRegistry
import org.example.ui.ConsoleUI

fun main() = runBlocking {
    ConsoleUI.printWelcome()
    val authorizationKey = AppConfig.loadAuthorizationKey()
    ConsoleUI.printInitializing()
    val client = GigaChatClient(authorizationKey)
    val toolRegistry = GigaChatToolRegistry.createDefault()
    val agent = GigaChatAgent(client, toolRegistry)
    ConsoleUI.printReady()
    runChatLoop(agent, client)
}

private suspend fun runChatLoop(agent: GigaChatAgent, client: GigaChatClient) {
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
            else -> processUserMessage(agent, input)
        }
    }
}

private suspend fun processUserMessage(agent: GigaChatAgent, input: String) {
    try {
        val response = agent.processMessage(input)
        ConsoleUI.printResponse(response)
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
