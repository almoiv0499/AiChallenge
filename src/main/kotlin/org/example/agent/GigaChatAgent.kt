package org.example.agent

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.example.client.GigaChatClient
import org.example.config.GigaChatConfig
import org.example.models.*
import org.example.tools.GigaChatToolRegistry
import org.example.ui.ConsoleUI

class GigaChatAgent(
    private val client: GigaChatClient,
    private val toolRegistry: GigaChatToolRegistry,
    private val model: String = GigaChatConfig.DEFAULT_MODEL
) {
    private val conversationHistory = mutableListOf<GigaChatMessage>()

    init {
        addSystemPrompt()
        ConsoleUI.printAgentInitialized(model, toolRegistry.getAllTools().size)
    }

    suspend fun processMessage(userMessage: String): ChatResponse {
        ConsoleUI.printUserMessage(userMessage)
        addUserMessage(userMessage)
        return executeAgentLoop()
    }

    fun clearHistory() {
        conversationHistory.clear()
        addSystemPrompt()
        ConsoleUI.printHistoryClearedLog()
    }

    private suspend fun executeAgentLoop(): ChatResponse {
        val toolCallResults = mutableListOf<ToolCallResult>()
        repeat(GigaChatConfig.MAX_AGENT_ITERATIONS) {
            val response = sendRequest()
            val choice = response.choices.firstOrNull() ?: return createLimitExceededResponse(toolCallResults)
            addAssistantMessage(choice.message)
            if (isFunctionCall(choice)) {
                handleFunctionCall(choice.message, toolCallResults)
            } else {
                return createSuccessResponse(choice.message, toolCallResults)
            }
        }
        return createLimitExceededResponse(toolCallResults)
    }

    private suspend fun sendRequest(): GigaChatResponse {
        val functions = toolRegistry.getToolDefinitions().takeIf { it.isNotEmpty() }
        val request = GigaChatRequest(
            model = model,
            messages = conversationHistory.toList(),
            functions = functions,
            functionCall = if (functions != null) "auto" else null
        )
        return client.chat(request)
    }

    private fun handleFunctionCall(message: GigaChatMessageResponse, results: MutableList<ToolCallResult>) {
        val functionCall = message.functionCall ?: return
        val toolName = functionCall.name
        val arguments = parseArguments(functionCall.arguments)
        ConsoleUI.printToolCall(toolName, functionCall.arguments)
        val result = executeFunction(toolName, arguments)
        ConsoleUI.printToolResult(result)
        results.add(ToolCallResult(toolName, arguments, result))
        addFunctionResult(result)
    }

    private fun executeFunction(toolName: String, arguments: Map<String, String>): String {
        val tool = toolRegistry.getTool(toolName)
        return tool?.execute(arguments) ?: "Ошибка: инструмент '$toolName' не найден"
    }

    private fun parseArguments(argumentsJson: JsonObject): Map<String, String> {
        return try {
            argumentsJson.mapValues { it.value.jsonPrimitive.content }
        } catch (e: Exception) {
            ConsoleUI.printArgumentParseError(e.message)
            emptyMap()
        }
    }

    private fun isFunctionCall(choice: GigaChatChoice): Boolean =
        choice.finishReason == GigaChatConfig.FinishReasons.FUNCTION_CALL &&
            choice.message.functionCall != null

    private fun createSuccessResponse(message: GigaChatMessageResponse, toolCalls: List<ToolCallResult>): ChatResponse {
        val responseText = message.content ?: "Нет ответа"
        ConsoleUI.printAssistantMessage(responseText)
        return ChatResponse(responseText, toolCalls)
    }

    private fun createLimitExceededResponse(toolCalls: List<ToolCallResult>) =
        ChatResponse("Превышен лимит итераций обработки", toolCalls)

    private fun addSystemPrompt() {
        conversationHistory.add(GigaChatMessage(
            role = GigaChatConfig.Roles.SYSTEM,
            content = SYSTEM_PROMPT
        ))
    }

    private fun addUserMessage(message: String) {
        conversationHistory.add(GigaChatMessage(
            role = GigaChatConfig.Roles.USER,
            content = message
        ))
    }

    private fun addAssistantMessage(message: GigaChatMessageResponse) {
        conversationHistory.add(GigaChatMessage(
            role = message.role,
            content = message.content,
            functionCall = message.functionCall,
            functionsStateId = message.functionsStateId
        ))
    }

    private fun addFunctionResult(result: String) {
        val resultJson = buildJsonObject { put("result", result) }.toString()
        conversationHistory.add(GigaChatMessage(
            role = GigaChatConfig.Roles.FUNCTION,
            content = resultJson
        ))
    }

    companion object {
        private val SYSTEM_PROMPT = """
            Ты - полезный AI-ассистент. Ты можешь отвечать на вопросы пользователя и использовать доступные инструменты для выполнения задач.
            
            Доступные инструменты:
            - get_current_time: получить текущее время
            - calculator: выполнить математические вычисления (add, subtract, multiply, divide, power, sqrt)
            - search: найти информацию по запросу
            - random_number: сгенерировать случайное число
            
            Если пользователь просит выполнить задачу, которую можно решить с помощью инструментов - используй их.
            Отвечай на русском языке.
        """.trimIndent()
    }
}
