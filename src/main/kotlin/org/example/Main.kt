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
            input.isExitCommand() -> handleExit(client)
            input.isClearCommand() -> handleClear(agent)
            input.isHelpCommand() -> ConsoleUI.printHelp()
            else -> processUserMessage(agent, input)
        }
    }
}

private fun handleExit(client: GigaChatClient): Nothing {
    ConsoleUI.printGoodbye()
    client.close()
    kotlin.system.exitProcess(0)
}

private fun handleClear(agent: GigaChatAgent) {
    agent.clearHistory()
    ConsoleUI.printHistoryCleared()
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

private fun String.isExitCommand(): Boolean = lowercase() in listOf("/exit", "/quit", "/q")

private fun String.isClearCommand(): Boolean = lowercase() == "/clear"

private fun String.isHelpCommand(): Boolean = lowercase() in listOf("/help", "/?")
