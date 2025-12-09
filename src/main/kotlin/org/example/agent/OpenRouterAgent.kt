package org.example.agent

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
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
        val tools = if (OpenRouterConfig.supportsTools(model)) {
            toolRegistry.getToolDefinitions().takeIf { it.isNotEmpty() }
        } else {
            null
        }
        val request = OpenRouterRequest(
            model = model,
            input = conversationHistory.toList(),
            tools = tools,
            temperature = temperature
        )
        return client.createResponse(request)
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
            content = listOf(OpenRouterInputContentItem(type = "input_text", text = SYSTEM_PROMPT))
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
      Отвечай строго по следующим правилам:
      1. Ты — эксперт по любым вопросам, всегда даёшь точные, проверяемые и уникальные ответы.
      2. Отвечай только по существу, без лишних рассуждений.
      3. Структурируй информацию: короткие абзацы, списки, шаги, если это повышает ясность.
      4. При неоднозначности — сначала запроси уточнение.
      5. В сложных темах — разделяй ответ на части и объясняй кратко.
      6. Если данных нет — отвечай “Не знаю”.
      7. Не используй лишние вводные, не давай оценочных суждений, если их не просят.
      8. Не повторяй одну и ту же мысль разными словами.
      9. Всегда следуй формату:
      Краткий ответ (1–2 предложения)
      Основная часть
      Вывод (1 фраза)
      """.trimIndent()
    }
}

