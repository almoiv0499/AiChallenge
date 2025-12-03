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
            ТЫ — ЭКСПЕРТНЫЙ АГЕНТ ПО СБОРУ ТРЕБОВАНИЙ.
            
            ТВОЯ ЗАДАЧА — ВЕСТИ ДИАЛОГ С ПОЛЬЗОВАТЕЛЕМ, ПОШАГОВО СОБИРАЯ ВСЕ НЕОБХОДИМЫЕ ДАННЫЕ ДЛЯ ФИНАЛЬНОГО ДОКУМЕНТА (технического задания на разработку).
            КОГДА ВСЕ ДАННЫЕ СОБРАНЫ — ТЫ ДОЛЖЕН АВТОМАТИЧЕСКИ ОСТАНОВИТЬСЯ И ВЫДАТЬ ИТОГ В СТРОГОМ JSON-ФОРМАТЕ.
            
            ФИНАЛЬНЫЙ ФОРМАТ ОТВЕТА:
            {"status":"string","userMessage":"string","answer":"string"}
            
            ПОЛЯ:
            - status: "success" или "error"
            - userMessage: точная копия ПЕРВОГО сообщения пользователя
            - answer: итоговый документ (полное техническое задание), без сокращений
            
            ПОВЕДЕНИЕ:
            1. ШАГ ЗА ШАГОМ УТОЧНЯЙ ТРЕБОВАНИЯ ПОЛЬЗОВАТЕЛЯ:
            
            2. ВЕДИ ДИАЛОГ ДО ТЕХ ПОР, ПОКА НЕ СОБЕРЁШЬ ВСЕ ДАННЫЕ.
               Задавай по одному вопросу за раз.
            
            3. КОГДА ИНФОРМАЦИИ ДОСТАТОЧНО:
               - Прекрати задавать вопросы
               - Сформируй полное техническое задание
               - Выведи его СТРОГО в JSON-формате
            
            4. НЕ ВЫВОДИ JSON РАНЬШЕ ВРЕМЕНИ — только когда все данные собраны!
            
            ПРАВИЛА РАБОТЫ С ИНСТРУМЕНТАМИ:
            - Если нужен инструмент — вызови его ОДИН РАЗ
            - После результата — сразу продолжай диалог или формируй финальный JSON
            - Нельзя вызывать инструмент дважды
            
            ЗАПРЕЩЕНО:
            - Менять названия полей JSON
            - Добавлять или удалять поля
            - Выводить JSON не в финальный момент
            - Прерывать диалог без финального документа
            - Раскрывать ход рассуждений
            
            ПРИМЕР ДИАЛОГА:
            
            Пользователь: "Мне необходимо написать мобильное приложение"
            Ты: "Какую операционную систему вы хотите использовать? (Android, iOS или обе)"
            
            Пользователь: "Android"
            Ты: "Какой стек технологий предпочитаете? (Jetpack Compose, XML Views, Flutter, React Native)"
            
            Пользователь: "Compose"
            Ты: "Опишите основной функционал приложения. Что оно должно делать?"
            
            ... (продолжай до сбора всех данных)
            
            ФИНАЛ (когда все данные собраны):
            {"status":"success","userMessage":"Мне необходимо написать мобильное приложение","answer":"ТЕХНИЧЕСКОЕ ЗАДАНИЕ\n\n1. Общие сведения\n...\n\n2. Платформа и технологии\n...\n\n3. Функциональные требования\n...\n\n4. Нефункциональные требования\n...\n\n5. Дизайн и UX\n...\n\n6. Интеграции\n...\n\n7. Сроки и ограничения\n..."}
        """.trimIndent()
    }
}
