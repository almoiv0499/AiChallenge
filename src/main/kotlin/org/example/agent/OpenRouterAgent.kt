package org.example.agent

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.example.client.OpenRouterClient
import org.example.config.OpenRouterConfig
import org.example.models.*
import org.example.tools.ToolRegistry
import org.example.ui.ConsoleUI

class OpenRouterAgent(
    private val client: OpenRouterClient,
    private val toolRegistry: ToolRegistry,
    private val model: String = OpenRouterConfig.DEFAULT_MODEL
) {
    private val temperature: Double = OpenRouterConfig.Temperature.DEFAULT
    private val conversationHistory = mutableListOf<JsonElement>()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

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
        repeat(OpenRouterConfig.MAX_AGENT_ITERATIONS) { iteration ->
            ConsoleUI.printDebugIteration(iteration + 1, OpenRouterConfig.MAX_AGENT_ITERATIONS)
            val response = sendRequest()
            val output = response.output ?: return createErrorResponse(
                "Пустой ответ от API",
                toolCallResults
            )
            for (item in output) {
                when (item.type) {
                    "message" -> {
                        val text = extractTextContent(item)
                        if (text.isNotEmpty()) {
                            addAssistantMessage(text)
                            val apiResponse = parseApiResponse(text)
                            return ChatResponse(
                                response = text,
                                toolCalls = toolCallResults,
                                apiResponse = apiResponse,
                                temperature = temperature
                            )
                        }
                    }

                    "function_call" -> {
                        addFunctionCallToHistory(item)
                        val result = handleFunctionCall(item, toolCallResults)
                        if (result != null) {
                            addFunctionResultToHistory(item.callId ?: "", result)
                        }
                    }
                }
            }
        }
        return createLimitExceededResponse(toolCallResults)
    }

    private suspend fun sendRequest(): OpenRouterResponse {
        val tools = if (OpenRouterConfig.ENABLE_TOOLS && OpenRouterConfig.supportsTools(model)) {
            toolRegistry.getToolDefinitions().takeIf { it.isNotEmpty() }
        } else {
            null
        }
        logRequestDetails(conversationHistory, tools)
        val request = OpenRouterRequest(
            model = model,
            input = conversationHistory.toList(),
            tools = tools,
            temperature = temperature
        )
        return client.createResponse(request)
    }

    private fun logRequestDetails(history: List<JsonElement>, tools: List<OpenRouterTool>?) {
        val historyText = history.joinToString("\n") { it.toString() }
        val toolsText = tools?.joinToString("\n") { json.encodeToString(OpenRouterTool.serializer(), it) } ?: ""
        val estimatedHistoryTokens = estimateTokens(historyText)
        val estimatedToolsTokens = if (toolsText.isNotEmpty()) estimateTokens(toolsText) else 0
        val totalEstimated = estimatedHistoryTokens + estimatedToolsTokens
        ConsoleUI.printRequestDetails(
            historyItems = history.size,
            historyTokens = estimatedHistoryTokens,
            toolsCount = tools?.size ?: 0,
            toolsTokens = estimatedToolsTokens,
            totalEstimated = totalEstimated
        )
    }

    private fun estimateTokens(text: String): Int {
        return text.split(Regex("\\s+")).size + (text.length / 4)
    }

    private fun handleFunctionCall(
        item: OpenRouterOutputItem,
        results: MutableList<ToolCallResult>
    ): String? {
        val toolName = item.name ?: return null
        val argumentsStr = item.arguments ?: "{}"
        val arguments = parseArguments(argumentsStr)
        ConsoleUI.printToolCall(toolName, argumentsStr)
        val result = executeFunction(toolName, arguments)
        ConsoleUI.printToolResult(result)
        results.add(ToolCallResult(toolName, arguments, result))
        return result
    }

    private fun executeFunction(toolName: String, arguments: Map<String, String>): String {
        val tool = toolRegistry.getTool(toolName)
        return tool?.execute(arguments) ?: "Ошибка: инструмент '$toolName' не найден"
    }

    private fun parseArguments(argumentsStr: String): Map<String, String> {
        return try {
            val jsonElement = json.parseToJsonElement(argumentsStr)
            jsonElement.jsonObject.mapValues { it.value.jsonPrimitive.content }
        } catch (e: Exception) {
            ConsoleUI.printArgumentParseError(e.message)
            emptyMap()
        }
    }

    private fun extractTextContent(item: OpenRouterOutputItem): String {
        return item.content
            ?.filter { it.type == "output_text" || it.type == "text" }
            ?.mapNotNull { it.text }
            ?.joinToString("") ?: ""
    }

    private fun createErrorResponse(message: String, toolCalls: List<ToolCallResult>) =
        ChatResponse(response = message, toolCalls = toolCalls, temperature = temperature)

    private fun createLimitExceededResponse(toolCalls: List<ToolCallResult>) =
        ChatResponse(
            response = "Превышен лимит итераций обработки",
            toolCalls = toolCalls,
            temperature = temperature
        )

    private fun addSystemPrompt() {
        val message = OpenRouterInputMessage(
            role = "system",
            content = listOf(OpenRouterInputContentItem(type = "input_text", text = ""))
        )
        conversationHistory.add(
            json.encodeToJsonElement(
                OpenRouterInputMessage.serializer(),
                message
            )
        )
    }

    private fun addUserMessage(message: String) {
        val msg = OpenRouterInputMessage(
            role = "user",
            content = listOf(OpenRouterInputContentItem(type = "input_text", text = message))
        )
        conversationHistory.add(json.encodeToJsonElement(OpenRouterInputMessage.serializer(), msg))
    }

    private fun addAssistantMessage(message: String) {
        val msg = OpenRouterInputMessage(
            role = "assistant",
            content = listOf(OpenRouterInputContentItem(type = "output_text", text = message))
        )
        conversationHistory.add(json.encodeToJsonElement(OpenRouterInputMessage.serializer(), msg))
    }

    private fun addFunctionCallToHistory(item: OpenRouterOutputItem) {
        val functionCall = OpenRouterFunctionCallInput(
            id = item.id ?: "",
            callId = item.callId ?: "",
            name = item.name ?: "",
            arguments = item.arguments ?: "{}"
        )
        conversationHistory.add(
            json.encodeToJsonElement(
                OpenRouterFunctionCallInput.serializer(),
                functionCall
            )
        )
    }

    private fun addFunctionResultToHistory(callId: String, result: String) {
        val output = OpenRouterFunctionCallOutput(callId = callId, output = result)
        conversationHistory.add(
            json.encodeToJsonElement(
                OpenRouterFunctionCallOutput.serializer(),
                output
            )
        )
    }

    private fun parseApiResponse(text: String): ApiResponse? {
        val jsonString = ApiResponse.extractJsonFromText(text) ?: return null
        return ApiResponse.parseFromString(jsonString)
    }

    companion object {
        private val SYSTEM_PROMPT = """
Ты — помощник на базе нейросети. Отвечай корректно, вежливо и понятно на русском языке, если пользователь не просит другой язык. По умолчанию давай точные и компактные ответы, но при явной просьбе пользователя «подробно», «максимально подробно», «в развернутом виде», «детально» и т.п. переключайся в режим максимально подробного ответа.

Правила поведения в режиме «подробно»:
1. Структура ответа:
   a. Короткая суть — 2–3 предложения (что будем разбирать).  
   b. Подробная основная часть — разбитая на нумерованные или именованные секции (пояснение, фон, шаги/алгоритм, примеры, варианты, подводные камни).  
   c. Практическая часть — конкретные действия/рецепт/код/формулы (если применимо).  
   d. Дополнительные материалы — ссылки, источники, литература или список ключевых терминов.  
   e. Итог / TL;DR — 1–2 предложения, повторяющие самое важное.

2. Глубина и формат:
   - Приводи определения, обоснования, мотивацию и альтернативы там, где это релевантно.  
   - Когда даёшь инструкции — показывай пошагово (шаг 1, шаг 2...) с ожидаемыми результатами и проверками.  
   - Для численных расчётов выполняй вычисления «цифра за цифрой» и показывай проверку результата.  
   - Для кода: предоставляй полностью рабочий пример, поясняй зависимости и как тестировать; когда можешь — добавляй короткий пример вывода.  
   - Для практических руководств указывай риски, предпосылки и необходимые инструменты/права.

3. Источники и проверяемость:
   - Если утверждение может изменяться со временем (новости, цены, законы, версии ПО и т.п.), отмечай необходимость проверки актуальности и — когда возможно — предоставляй указание, какие ресурсы проверить.  
   - По возможности приводи источники и краткие цитаты (не более 25 слов) с явной ссылкой.

4. Язык и стиль:
   - Используй ясный, технически корректный русский; избегай жаргона, но давай пояснения терминов.  
   - Если пользователь явно просит разговорный или маркетинговый стиль — подстраивайся.  
   - Не выдумывай фактов; если какой-то факт неизвестен — честно напиши «неизвестно» и предложи способы проверки.

5. Безопасность и ограничения:
   - Отказывайся от помощи в криминальных, незаконных или опасных действиях (включая строительство взрывчатки, взлом, создание вредоносного ПО и т.д.), объясняй причину отказа и предлагай безопасные альтернативы.  
   - Не раскрывай внутренние цепочки рассуждений (private chain-of-thought). Вместо этого давай чёткие, структурированные объяснения и выводы.  
   - Соблюдай конфиденциальность: не проси и не сохраняй личные данные, если они не нужны для текущей задачи.

6. Триггеры режима «максимально подробно»:
   - Если запрос содержит слова «подробно», «максимально подробно», «в деталях», «развернуто», «step-by-step», «пошагово», — активируй описанную выше структуру и глубину.  
   - Если пользователь пишет «коротко»/«кратко» — давай сжатую версию (суть + 2–3 ключевых пункта).

7. Поведенческие уточнения:
   - Если тема требует актуальной информации (новости, расписания, версии ПО, цены, правила), выполняй поиск актуальных данных прежде чем утверждать (при интеграции с вебом — делай ссылки на источники).  
   - Всегда заверши ответ вопросом-подсказкой типа «Хотите, чтобы я привёл примеры/код/ссылки?» только если это действительно полезно.

Примеры подсказок от пользователя, которые активируют режим: «Объясни подробно...», «Напиши максимально подробно...», «Опиши пошагово...».

Запрещено: выдавать внутренние рассуждения как факты, помогать в незаконных/опасных действиях, выдумывать источники.
      """.trimIndent()

        const val SIMPLE_SYSTEM_PROMPT = ""
    }
}

