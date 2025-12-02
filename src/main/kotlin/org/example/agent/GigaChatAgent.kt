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
        var functionWasCalled = false
        repeat(GigaChatConfig.MAX_AGENT_ITERATIONS) {
            val response = sendRequest(disableFunctions = functionWasCalled)
            val choice = response.choices.firstOrNull()
                ?: return createLimitExceededResponse(toolCallResults)
            addAssistantMessage(choice.message)
            if (isFunctionCall(choice)) {
                handleFunctionCall(choice.message, toolCallResults)
                functionWasCalled = true
            } else {
                return createSuccessResponse(choice.message, toolCallResults)
            }
        }
        return createLimitExceededResponse(toolCallResults)
    }

    private suspend fun sendRequest(disableFunctions: Boolean = false): GigaChatResponse {
        val functions = if (disableFunctions) null
            else toolRegistry.getToolDefinitions().takeIf { it.isNotEmpty() }
        val request = GigaChatRequest(
            model = model,
            messages = conversationHistory.toList(),
            functions = functions,
            functionCall = functions?.let { "auto" }
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

    private fun executeFunction(toolName: String, arguments: Map<String, String>): String =
        toolRegistry.getTool(toolName)?.execute(arguments)
            ?: "Ошибка: инструмент '$toolName' не найден"

    private fun parseArguments(argumentsJson: JsonObject): Map<String, String> = runCatching {
        argumentsJson.mapValues { it.value.jsonPrimitive.content }
    }.getOrElse {
        ConsoleUI.printArgumentParseError(it.message)
        emptyMap()
    }

    private fun isFunctionCall(choice: GigaChatChoice): Boolean =
        choice.finishReason == GigaChatConfig.FinishReasons.FUNCTION_CALL &&
            choice.message.functionCall != null

    private fun createSuccessResponse(
        message: GigaChatMessageResponse,
        toolCalls: List<ToolCallResult>
    ): ChatResponse {
        val responseText = message.content ?: "Нет ответа"
        ConsoleUI.printAssistantMessage(responseText)
        val apiResponse = parseApiResponse(responseText)
        return ChatResponse(responseText, toolCalls, apiResponse)
    }

    private fun createLimitExceededResponse(toolCalls: List<ToolCallResult>): ChatResponse {
        val errorResponse = ApiResponse.error(
            userMessage = "Системная ошибка",
            answer = "Превышен лимит итераций обработки"
        )
        return ChatResponse(errorResponse.toJsonString(), toolCalls, errorResponse)
    }

    private fun parseApiResponse(responseText: String): ApiResponse? =
        ApiResponse.parseFromString(responseText)
            ?: ApiResponse.extractJsonFromText(responseText)?.let { ApiResponse.parseFromString(it) }

    private fun addSystemPrompt() {
        conversationHistory.add(
            GigaChatMessage(role = GigaChatConfig.Roles.SYSTEM, content = SYSTEM_PROMPT)
        )
    }

    private fun addUserMessage(message: String) {
        conversationHistory.add(
            GigaChatMessage(role = GigaChatConfig.Roles.USER, content = message)
        )
    }

    private fun addAssistantMessage(message: GigaChatMessageResponse) {
        conversationHistory.add(
            GigaChatMessage(
                role = message.role,
                content = message.content,
                functionCall = message.functionCall,
                functionsStateId = message.functionsStateId
            )
        )
    }

    private fun addFunctionResult(result: String) {
        val resultJson = buildJsonObject {
            put("result", result)
            put("instruction", "Теперь верни финальный JSON-ответ с этим результатом. Не вызывай инструменты повторно.")
        }.toString()
        conversationHistory.add(
            GigaChatMessage(role = GigaChatConfig.Roles.FUNCTION, content = resultJson)
        )
    }

    companion object {
        private val SYSTEM_PROMPT = """
            ТЫ — ЭКСПЕРТНЫЙ АГЕНТ. ТВОЙ ФИНАЛЬНЫЙ ОТВЕТ ВСЕГДА ДОЛЖЕН БЫТЬ В СТРОГОМ JSON-ФОРМАТЕ.
            
            ФОРМАТ ОТВЕТА (СТРОГО):
            {"status":"string","userMessage":"string","answer":"string"}
            
            ПОЛЯ:
            - status: "success" или "error"
            - userMessage: точная копия вопроса пользователя
            - answer: твой ответ на вопрос
            
            ВАЖНЫЕ ПРАВИЛА РАБОТЫ С ИНСТРУМЕНТАМИ:
            1. Если нужно использовать инструмент - вызови его ОДИН РАЗ
            2. После получения результата от инструмента - СРАЗУ ВЕРНИ JSON-ответ с результатом
            3. НИКОГДА не вызывай один и тот же инструмент повторно
            4. Используй полученный результат для формирования ответа
            
            ЗАПРЕЩЕНО:
            - Менять названия полей JSON
            - Добавлять или удалять поля
            - Выводить что-либо кроме JSON в финальном ответе
            - Вызывать инструмент более одного раза для одного запроса
            
            ПРИМЕРЫ:
            
            Вопрос: "Который час?"
            Действие: вызвать get_current_time один раз
            После получения результата "Текущее время: 2025-12-02 15:30:00":
            {"status":"success","userMessage":"Который час?","answer":"Сейчас 15:30:00 (2 декабря 2025)"}
            
            Вопрос: "Сколько будет 5+3?"
            Действие: вызвать calculator один раз
            После получения результата "8":
            {"status":"success","userMessage":"Сколько будет 5+3?","answer":"5+3 = 8"}
            
            Вопрос: "Столица России?"
            {"status":"success","userMessage":"Столица России?","answer":"Москва"}
        """.trimIndent()
    }
}
