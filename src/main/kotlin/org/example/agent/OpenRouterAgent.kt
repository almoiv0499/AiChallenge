package org.example.agent

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.example.client.OpenRouterClient
import org.example.config.OpenRouterConfig
import org.example.models.*
import org.example.storage.HistoryStorage
import org.example.tools.ToolRegistry
import org.example.ui.ConsoleUI
import org.example.agent.android.DeviceSearchExecutor
import org.example.embedding.RagService

class OpenRouterAgent(
    private val client: OpenRouterClient,
    private val toolRegistry: ToolRegistry,
    private val model: String = OpenRouterConfig.DEFAULT_MODEL,
    private val historyStorage: HistoryStorage = HistoryStorage(),
    private val deviceSearchExecutor: DeviceSearchExecutor? = null,
    private val ragService: RagService? = null
) {
    private val temperature: Double = OpenRouterConfig.Temperature.DEFAULT
    private val conversationHistory = mutableListOf<JsonElement>()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private var userMessageCount: Int = 0

    init {
        addSystemPrompt()
        loadSavedSummary()
        addProjectContextToSystemPrompt()
        ConsoleUI.printAgentInitialized(model, toolRegistry.getAllTools().size)
    }

    suspend fun processMessage(userMessage: String): ChatResponse {
        ConsoleUI.printUserMessage(userMessage)
        
        // Check if this is an on-device search request
        if (deviceSearchExecutor != null && isOnDeviceSearchRequest(userMessage)) {
            val searchQuery = extractSearchQuery(userMessage)
            if (searchQuery != null) {
                val result = deviceSearchExecutor.executeSearch(searchQuery)
                return ChatResponse(
                    response = result,
                    toolCalls = emptyList()
                )
            }
        }
        
        // Get RAG context if query is related to project tasks
        val enrichedMessage = if (isProjectTaskRelatedQuery(userMessage) && ragService != null) {
            val context = getProjectContext(userMessage)
            if (context.isNotEmpty()) {
                println("📚 Найден контекст проекта через RAG")
                """
                    Контекст проекта (из документации):
                    $context
                    
                    Вопрос пользователя: $userMessage
                """.trimIndent()
            } else {
                userMessage
            }
        } else {
            userMessage
        }
        
        addUserMessage(enrichedMessage)
        return executeAgentLoop()
    }
    
    /**
     * Detects if the user message is requesting an on-device search.
     */
    private fun isOnDeviceSearchRequest(message: String): Boolean {
        val lowerMessage = message.lowercase()
        val searchKeywords = listOf(
            "найди на устройстве",
            "найди на девайсе",
            "поиск на устройстве",
            "поиск на девайсе",
            "find on device",
            "search on device",
            "найди в эмуляторе",
            "поиск в эмуляторе",
            "search in emulator",
            "find in emulator"
        )
        return searchKeywords.any { lowerMessage.contains(it) }
    }
    
    /**
     * Extracts the search query from the user message.
     */
    private fun extractSearchQuery(message: String): String? {
        // Try to extract query after search keywords
        val patterns = listOf(
            Regex("""(?:найди|поиск|find|search)\s+(?:на\s+)?(?:устройстве|девайсе|эмуляторе|device|emulator)[\s:]+(.+)""", RegexOption.IGNORE_CASE),
            Regex("""(?:найди|поиск|find|search)\s+(.+?)\s+(?:на\s+)?(?:устройстве|девайсе|эмуляторе|device|emulator)""", RegexOption.IGNORE_CASE)
        )
        
        for (pattern in patterns) {
            val match = pattern.find(message)
            if (match != null) {
                return match.groupValues[1].trim()
            }
        }
        
        // If no pattern matches, try to extract after common phrases
        val fallbackPatterns = listOf(
            Regex(""":\s*(.+)"""),
            Regex("""-?\s*(.+)""")
        )
        
        for (pattern in fallbackPatterns) {
            val match = pattern.find(message)
            if (match != null) {
                val candidate = match.groupValues[1].trim()
                if (candidate.length > 3) {
                    return candidate
                }
            }
        }
        
        return null
    }
    
    /**
     * Определяет, связан ли запрос с задачами проекта
     */
    private fun isProjectTaskRelatedQuery(message: String): Boolean {
        val keywords = listOf(
            "задача", "задачи", "проект", "статус", "приоритет",
            "дедлайн", "релиз", "milestone", "критичн", "блокер",
            "блокирован", "исполнитель", "assignee", "epic",
            "просрочен", "overdue", "команда", "team", "capacity",
            "загрузка", "workload", "выполнен", "done", "todo"
        )
        val lowerMessage = message.lowercase()
        return keywords.any { lowerMessage.contains(it, ignoreCase = true) }
    }
    
    /**
     * Получает контекст проекта через RAG
     */
    private suspend fun getProjectContext(query: String): String {
        if (ragService == null) return ""
        
        return try {
            val results = ragService.search(query, limit = 3, minSimilarity = 0.5)
            if (results.isEmpty()) {
                // Попробуем более общий поиск
                val generalResults = ragService.search("проект статус дедлайн релиз", limit = 2, minSimilarity = 0.3)
                if (generalResults.isEmpty()) {
                    return ""
                }
                generalResults.joinToString("\n\n") { result ->
                    val source = result.metadata["title"] ?: result.metadata["file"] ?: "Документация проекта"
                    "$source:\n${result.text.take(500)}"
                }
            } else {
                results.joinToString("\n\n") { result ->
                    val source = result.metadata["title"] ?: result.metadata["file"] ?: "Документация проекта"
                    "$source:\n${result.text.take(500)}"
                }
            }
        } catch (e: Exception) {
            println("⚠️ Ошибка при получении RAG контекста: ${e.message}")
            ""
        }
    }

    fun clearHistory() {
        conversationHistory.clear()
        userMessageCount = 0
        historyStorage.clearAllSummaries()
        addSystemPrompt()
        addProjectContextToSystemPrompt()
        ConsoleUI.printHistoryClearedLog()
    }

    private suspend fun executeAgentLoop(): ChatResponse {
        val toolCallResults = mutableListOf<ToolCallResult>()
        var iterationCount = 0
        while (iterationCount < OpenRouterConfig.MAX_AGENT_ITERATIONS) {
            iterationCount++
            ConsoleUI.printDebugIteration(iterationCount, OpenRouterConfig.MAX_AGENT_ITERATIONS)
            val response = sendRequest()
            val output = response.output
            if (output == null) {
                return createErrorResponse(
                    "Пустой ответ от API",
                    toolCallResults
                )
            }
            var hasMessage = false
            var hasFunctionCall = false
            var finalMessageText: String? = null
            for (item in output) {
                when (item.type) {
                    "message" -> {
                        hasMessage = true
                        val text = extractTextContent(item)
                        if (text.isNotEmpty()) {
                            // Проверяем, не содержит ли текст JSON с function_call
                            val parsedFunctionCall = tryParseFunctionCallFromText(text)
                            if (parsedFunctionCall != null) {
                                hasFunctionCall = true
                                println("✅ Function call распознан: ${parsedFunctionCall.name}")
                                // Создаем временный OpenRouterOutputItem для обработки
                                val functionCallItem = OpenRouterOutputItem(
                                    type = "function_call",
                                    name = parsedFunctionCall.name,
                                    arguments = parsedFunctionCall.arguments,
                                    callId = parsedFunctionCall.callId
                                )
                                addFunctionCallToHistory(functionCallItem)
                                val result = handleFunctionCall(functionCallItem, toolCallResults)
                                if (result != null) {
                                    addFunctionResultToHistory(parsedFunctionCall.callId, result)
                                }
                                // Сохраняем текст до function_call как контекст для финального ответа
                                val textBeforeJson = extractTextBeforeJson(text)
                                if (textBeforeJson.isNotBlank()) {
                                    // Сохраняем вводный текст, но не как финальный ответ
                                    println("📝 Текст перед function_call: $textBeforeJson")
                                }
                            } else {
                                // Проверяем, есть ли в тексте упоминание function_call без полного JSON
                                if (text.contains("function_call") || text.contains("\"name\"")) {
                                    println("⚠️ Текст содержит признаки function_call, но не удалось распарсить:")
                                    println("   Текст: ${text.take(500)}...")
                                }
                                finalMessageText = text
                            }
                        }
                    }

                    "function_call" -> {
                        hasFunctionCall = true
                        addFunctionCallToHistory(item)
                        val result = handleFunctionCall(item, toolCallResults)
                        if (result != null) {
                            addFunctionResultToHistory(item.callId ?: "", result)
                        }
                    }
                }
            }
            if (finalMessageText != null && !hasFunctionCall) {
                addAssistantMessage(finalMessageText)
                val apiResponse = parseApiResponse(finalMessageText)
                return ChatResponse(
                    response = finalMessageText,
                    toolCalls = toolCallResults,
                    apiResponse = apiResponse
                )
            }
            if (!hasMessage && !hasFunctionCall) {
                return createErrorResponse(
                    "Нет сообщения или вызова функции в ответе",
                    toolCallResults
                )
            }
            // Continue to next iteration only if there was a function call
            // (meaning we need to process the result and get a new response)
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
            when {
                jsonElement is kotlinx.serialization.json.JsonNull -> emptyMap()
                jsonElement is kotlinx.serialization.json.JsonObject -> jsonElement.mapValues { it.value.jsonPrimitive?.content ?: "" }
                else -> emptyMap()
            }
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
    
    /**
     * Извлекает текст до JSON блока с function_call.
     */
    private fun extractTextBeforeJson(text: String): String {
        // Ищем начало JSON блока
        val jsonStartPatterns = listOf(
            Regex("""```json\s*\{"""),
            Regex("""```\s*\{"""),
            Regex("""\{\s*"function_call"""")
        )
        
        for (pattern in jsonStartPatterns) {
            val match = pattern.find(text)
            if (match != null) {
                return text.substring(0, match.range.first).trim()
            }
        }
        
        return ""
    }

    /**
     * Пытается извлечь function_call из текста, если модель вернула его как JSON в тексте.
     */
    private fun tryParseFunctionCallFromText(text: String): ParsedFunctionCall? {
        return try {
            // 1. Сначала пытаемся извлечь JSON из markdown блока ```json ... ```
            val jsonFromMarkdown = extractJsonFromMarkdown(text)
            if (jsonFromMarkdown != null) {
                val parsed = parseJsonFunctionCall(jsonFromMarkdown)
                if (parsed != null) {
                    println("🔧 Распознан function_call из markdown: ${parsed.name}")
                    return parsed
                }
            }
            
            // 2. Пытаемся найти JSON объект напрямую в тексте
            val jsonFromText = extractJsonFromText(text)
            if (jsonFromText != null) {
                val parsed = parseJsonFunctionCall(jsonFromText)
                if (parsed != null) {
                    println("🔧 Распознан function_call из текста: ${parsed.name}")
                    return parsed
                }
            }
            
            // 3. Regex fallback для различных форматов
            val regexParsed = parseWithRegex(text)
            if (regexParsed != null) {
                println("🔧 Распознан function_call через regex: ${regexParsed.name}")
                return regexParsed
            }
            
            // 4. Последняя попытка - ищем известные имена инструментов
            val knownTools = listOf(
                "get_current_branch", "get_git_status", "get_open_files", "get_ide_open_files", "get_recent_commits",
                "get_weather", "get_forecast", "calculator", "get_current_time", "random_number",
                "notion_get_tasks", "notion_create_task", "notion_update_task", "get_notion_page",
                "append_notion_block"
            )
            
            for (toolName in knownTools) {
                if (text.contains("\"name\"") && text.contains("\"$toolName\"")) {
                    println("🔧 Распознан function_call по имени инструмента: $toolName")
                    return ParsedFunctionCall(
                        name = toolName,
                        arguments = "{}",
                        callId = "parsed_${System.currentTimeMillis()}"
                    )
                }
            }
            
            null
        } catch (e: Exception) {
            println("⚠️ Ошибка парсинга function_call: ${e.message}")
            null
        }
    }
    
    /**
     * Извлекает JSON из markdown блока ```json ... ``` или ``` ... ```
     */
    private fun extractJsonFromMarkdown(text: String): String? {
        // Паттерн для ```json ... ``` или ``` ... ```
        val patterns = listOf(
            Regex("""```json\s*([\s\S]*?)```"""),
            Regex("""```\s*([\s\S]*?)```""")
        )
        
        for (pattern in patterns) {
            val match = pattern.find(text)
            if (match != null) {
                val content = match.groupValues[1].trim()
                if (content.contains("function_call") && content.startsWith("{")) {
                    return content
                }
            }
        }
        return null
    }
    
    /**
     * Извлекает JSON объект из текста, ища сбалансированные скобки
     */
    private fun extractJsonFromText(text: String): String? {
        // Ищем начало JSON с function_call
        val startIndex = text.indexOf("{\"function_call\"")
        if (startIndex == -1) {
            // Попробуем с пробелами
            val altStart = text.indexOf("{ \"function_call\"")
            if (altStart == -1) return null
            return extractBalancedJson(text, altStart)
        }
        return extractBalancedJson(text, startIndex)
    }
    
    /**
     * Извлекает JSON с балансировкой скобок начиная с указанной позиции
     */
    private fun extractBalancedJson(text: String, startIndex: Int): String? {
        var braceCount = 0
        var inString = false
        var escaped = false
        
        for (i in startIndex until text.length) {
            val char = text[i]
            
            if (escaped) {
                escaped = false
                continue
            }
            
            when {
                char == '\\' -> escaped = true
                char == '"' -> inString = !inString
                !inString && char == '{' -> braceCount++
                !inString && char == '}' -> {
                    braceCount--
                    if (braceCount == 0) {
                        return text.substring(startIndex, i + 1)
                    }
                }
            }
        }
        return null
    }
    
    /**
     * Парсит JSON строку и извлекает function_call
     */
    private fun parseJsonFunctionCall(jsonStr: String): ParsedFunctionCall? {
        return try {
            val jsonElement = json.parseToJsonElement(jsonStr)
            if (jsonElement is JsonObject) {
                val functionCallObj = jsonElement["function_call"] as? JsonObject
                if (functionCallObj != null) {
                    val name = functionCallObj["name"]?.jsonPrimitive?.content
                    val arguments = functionCallObj["arguments"]?.let { argElement ->
                        when (argElement) {
                            is JsonPrimitive -> argElement.content
                            is JsonObject -> json.encodeToString(JsonObject.serializer(), argElement)
                            is JsonNull -> "{}"
                            else -> "{}"
                        }
                    } ?: "{}"
                    
                    if (name != null) {
                        return ParsedFunctionCall(
                            name = name,
                            arguments = arguments,
                            callId = "parsed_${System.currentTimeMillis()}"
                        )
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Пытается распарсить function_call с помощью regex
     */
    private fun parseWithRegex(text: String): ParsedFunctionCall? {
        // Паттерн 1: arguments как строка "{}"
        val stringArgsPattern = Regex(""""name"\s*:\s*"([^"]+)"[\s\S]*?"arguments"\s*:\s*"(\{[^"]*\})"""")
        val stringArgsMatch = stringArgsPattern.find(text)
        if (stringArgsMatch != null) {
            return ParsedFunctionCall(
                name = stringArgsMatch.groupValues[1],
                arguments = stringArgsMatch.groupValues[2],
                callId = "parsed_${System.currentTimeMillis()}"
            )
        }
        
        // Паттерн 2: arguments как объект {}
        val objectArgsPattern = Regex(""""name"\s*:\s*"([^"]+)"[\s\S]*?"arguments"\s*:\s*(\{[^}]*\})""")
        val objectArgsMatch = objectArgsPattern.find(text)
        if (objectArgsMatch != null) {
            return ParsedFunctionCall(
                name = objectArgsMatch.groupValues[1],
                arguments = objectArgsMatch.groupValues[2],
                callId = "parsed_${System.currentTimeMillis()}"
            )
        }
        
        // Паттерн 3: только имя (для инструментов без аргументов)
        val nameOnlyPattern = Regex(""""name"\s*:\s*"([^"]+)"""")
        val nameOnlyMatch = nameOnlyPattern.find(text)
        if (nameOnlyMatch != null && text.contains("function_call")) {
            return ParsedFunctionCall(
                name = nameOnlyMatch.groupValues[1],
                arguments = "{}",
                callId = "parsed_${System.currentTimeMillis()}"
            )
        }
        
        return null
    }

    /**
     * Вспомогательный класс для хранения распарсенного function_call.
     */
    private data class ParsedFunctionCall(
        val name: String,
        val arguments: String,
        val callId: String
    )

    private fun createErrorResponse(message: String, toolCalls: List<ToolCallResult>) =
        ChatResponse(response = message, toolCalls = toolCalls)

    private fun createLimitExceededResponse(toolCalls: List<ToolCallResult>) =
        ChatResponse(
            response = "Превышен лимит итераций обработки",
            toolCalls = toolCalls
        )

    private fun addSystemPrompt() {
        val message = OpenRouterInputMessage(
            role = "system",
            content = listOf(OpenRouterInputContentItem(type = "input_text", text = SIMPLE_SYSTEM_PROMPT))
        )
        conversationHistory.add(
            json.encodeToJsonElement(
                OpenRouterInputMessage.serializer(),
                message
            )
        )
    }

    private fun loadSavedSummary() {
        val savedSummary = historyStorage.getLatestSummary()
        if (savedSummary != null) {
            ConsoleUI.printSummaryLoaded(savedSummary.summary)
            val summaryMessage = OpenRouterInputMessage(
                role = "system",
                content = listOf(OpenRouterInputContentItem(type = "input_text", text = "Summary of earlier conversation: ${savedSummary.summary}"))
            )
            conversationHistory.add(json.encodeToJsonElement(OpenRouterInputMessage.serializer(), summaryMessage))
            userMessageCount = 0
        } else {
            ConsoleUI.printNoSavedSummary()
        }
    }
    
    /**
     * Добавляет общий контекст проекта в системный промпт через RAG
     */
    private fun addProjectContextToSystemPrompt() {
        if (ragService == null) return
        
        // Используем корутины для асинхронного поиска
        kotlinx.coroutines.runBlocking {
            try {
                val contextQueries = listOf(
                    "проект статус этап roadmap",
                    "дедлайн релиз milestone",
                    "команда роли участники"
                )
                
                val contextParts = mutableListOf<String>()
                for (query in contextQueries) {
                    val results = ragService.search(query, limit = 1, minSimilarity = 0.4)
                    if (results.isNotEmpty()) {
                        val result = results.first()
                        contextParts.add(result.text.take(300))
                    }
                }
                
                if (contextParts.isNotEmpty()) {
                    val projectContext = """
                        Контекст проекта (из документации):
                        ${contextParts.joinToString("\n\n")}
                    """.trimIndent()
                    
                    val contextMessage = OpenRouterInputMessage(
                        role = "system",
                        content = listOf(OpenRouterInputContentItem(type = "input_text", text = projectContext))
                    )
                    conversationHistory.add(json.encodeToJsonElement(OpenRouterInputMessage.serializer(), contextMessage))
                    println("📚 Контекст проекта добавлен в системный промпт")
                }
            } catch (e: Exception) {
                println("⚠️ Не удалось добавить контекст проекта: ${e.message}")
            }
        }
    }

    private fun addUserMessage(message: String) {
        val msg = OpenRouterInputMessage(
            role = "user",
            content = listOf(OpenRouterInputContentItem(type = "input_text", text = message))
        )
        conversationHistory.add(json.encodeToJsonElement(OpenRouterInputMessage.serializer(), msg))
        userMessageCount++
    }

    private suspend fun addAssistantMessage(message: String) {
        val msg = OpenRouterInputMessage(
            role = "assistant",
            content = listOf(OpenRouterInputContentItem(type = "output_text", text = message))
        )
        conversationHistory.add(json.encodeToJsonElement(OpenRouterInputMessage.serializer(), msg))
        compressHistoryIfNeeded()
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

    private suspend fun compressHistoryIfNeeded() {
        if (userMessageCount < OpenRouterConfig.HISTORY_COMPRESSION_THRESHOLD) {
            return
        }
        val tokensBefore = estimateHistoryTokens()
        val messageCountBeforeCompression = userMessageCount
        ConsoleUI.printHistoryCompressionStarted()
        val summaryMessage = createHistorySummary()
        if (summaryMessage != null) {
            historyStorage.saveSummary(summaryMessage, messageCountBeforeCompression)
            replaceMessagesWithSummary(summaryMessage)
            val tokensAfter = estimateHistoryTokens()
            val savedTokens = tokensBefore - tokensAfter
            ConsoleUI.printHistoryCompressionCompleted(summaryMessage, tokensBefore, tokensAfter, savedTokens)
        } else {
            ConsoleUI.printHistoryCompressionFailed()
        }
    }

    private suspend fun createHistorySummary(): String? {
        val messagesToSummarize = extractMessagesForSummary()
        if (messagesToSummarize.isEmpty()) {
            ConsoleUI.printHistoryCompressionError("Нет сообщений для суммаризации")
            return null
        }
        ConsoleUI.printCreatingSummary(messagesToSummarize.size)
        val summaryPrompt = buildSummaryPrompt(messagesToSummarize)
        val summaryRequest = OpenRouterRequest(
            model = model,
            input = listOf(
                json.encodeToJsonElement(
                    OpenRouterInputMessage.serializer(),
                    OpenRouterInputMessage(
                        role = "user",
                        content = listOf(OpenRouterInputContentItem(type = "input_text", text = summaryPrompt))
                    )
                )
            ),
            tools = null,
            temperature = OpenRouterConfig.Temperature.LOW
        )
        return try {
            val response = client.createResponse(summaryRequest)
            val summary = response.output
                ?.firstOrNull()
                ?.let { extractTextContent(it) }
            if (summary.isNullOrBlank()) {
                ConsoleUI.printHistoryCompressionError("Получен пустой summary от API")
                null
            } else {
                summary
            }
        } catch (e: Exception) {
            ConsoleUI.printHistoryCompressionError(e.message ?: e.toString())
            null
        }
    }

    private fun estimateHistoryTokens(): Int {
        val historyText = conversationHistory.joinToString("\n") { it.toString() }
        return estimateTokens(historyText)
    }

    private fun extractMessagesForSummary(): List<String> {
        val keepLast = OpenRouterConfig.HISTORY_COMPRESSION_KEEP_LAST
        val totalMessages = conversationHistory.size
        if (totalMessages <= keepLast + 1) {
            return emptyList()
        }
        val messagesToSummarize = totalMessages - keepLast
        val messages = mutableListOf<String>()
        for (i in 0 until messagesToSummarize) {
            val element = conversationHistory[i]
            try {
                val jsonObject = when {
                    element is JsonNull -> continue
                    element is JsonObject -> element
                    else -> continue
                }
                val type = jsonObject["type"]?.jsonPrimitive?.content ?: continue
                when (type) {
                    "message" -> {
                        val message = json.decodeFromJsonElement(OpenRouterInputMessage.serializer(), element)
                        if (message.role != "system") {
                            val text = message.content.firstOrNull()?.text ?: continue
                            val roleLabel = when (message.role) {
                                "user" -> "Пользователь"
                                "assistant" -> "Ассистент"
                                else -> message.role
                            }
                            messages.add("$roleLabel: $text")
                        }
                    }
                    "function_call" -> {
                        val functionCall = json.decodeFromJsonElement(OpenRouterFunctionCallInput.serializer(), element)
                        messages.add("Вызов функции: ${functionCall.name}(${functionCall.arguments})")
                    }
                    "function_call_output" -> {
                        val functionOutput = json.decodeFromJsonElement(OpenRouterFunctionCallOutput.serializer(), element)
                        messages.add("Результат функции: ${functionOutput.output}")
                    }
                }
            } catch (e: Exception) {
                continue
            }
        }
        return messages
    }

    private fun buildSummaryPrompt(messages: List<String>): String {
        val conversationText = messages.joinToString("\n\n")
        return """
            Ты — помощник для создания краткого резюме диалога. Создай информативное резюме следующего диалога, которое сохраняет:
            
            1. Ключевые факты и информацию, упомянутые в разговоре
            2. Важные решения, которые были приняты
            3. Контекст и обстоятельства обсуждения
            4. Тон и стиль общения (формальный/неформальный)
            5. Результаты вызовов функций и инструментов, если они были использованы
            
            Резюме должно быть компактным, но содержательным, чтобы агент мог продолжить диалог с пониманием всего предыдущего контекста.
            Резюме должно быть на русском языке и написано в формате системного сообщения, описывающего предыдущий разговор.
            
            Диалог:
            $conversationText
            
            Резюме (начни с "Ранее в разговоре мы обсуждали:"):
        """.trimIndent()
    }

    private fun replaceMessagesWithSummary(summary: String) {
        val keepLast = OpenRouterConfig.HISTORY_COMPRESSION_KEEP_LAST
        val totalMessages = conversationHistory.size
        val messagesToKeep = minOf(keepLast, totalMessages)
        val systemPromptIndex = conversationHistory.indexOfFirst { element ->
            try {
                val jsonObject = when {
                    element is JsonNull -> null
                    element is JsonObject -> element
                    else -> null
                } ?: return
                val type = jsonObject["type"]?.jsonPrimitive?.content
                val role = jsonObject["role"]?.jsonPrimitive?.content
                type == "message" && role == "system"
            } catch (e: Exception) {
                false
            }
        }
        val newHistory = mutableListOf<JsonElement>()
        if (systemPromptIndex >= 0) {
            newHistory.add(conversationHistory[systemPromptIndex])
        }
        val summaryMessage = OpenRouterInputMessage(
            role = "system",
            content = listOf(OpenRouterInputContentItem(type = "input_text", text = "Summary of earlier conversation: $summary"))
        )
        newHistory.add(json.encodeToJsonElement(OpenRouterInputMessage.serializer(), summaryMessage))
        val lastMessages = conversationHistory.takeLast(messagesToKeep)
        newHistory.addAll(lastMessages)
        conversationHistory.clear()
        conversationHistory.addAll(newHistory)
        userMessageCount = countUserMessagesInLastK(newHistory, keepLast)
    }

    private fun countUserMessagesInLastK(history: List<JsonElement>, k: Int): Int {
        val lastMessages = history.takeLast(k)
        var count = 0
        for (element in lastMessages) {
            try {
                val jsonObject = when {
                    element is JsonNull -> continue
                    element is JsonObject -> element
                    else -> continue
                }
                val type = jsonObject["type"]?.jsonPrimitive?.content
                val role = jsonObject["role"]?.jsonPrimitive?.content
                if (type == "message" && role == "user") {
                    count++
                }
            } catch (e: Exception) {
                continue
            }
        }
        return count
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

        const val SIMPLE_SYSTEM_PROMPT = """Ты — помощник с доступом к инструментам для работы с Notion API, получения погоды и работы с Git репозиторием. 

КРИТИЧЕСКИ ВАЖНО: Ты ДОЛЖЕН использовать доступные инструменты для ответа на вопросы пользователя. НЕ давай общие советы или примеры кода - ВЫЗЫВАЙ инструменты напрямую через function_call.

ОСОБОЕ ВНИМАНИЕ: Вопросы о файлах, ветках, статусе Git, коммитах - это вопросы о текущем состоянии репозитория. Для них НЕ ищи информацию в документации проекта - ВЫЗЫВАЙ Git инструменты напрямую. Например:
- "какие файлы открыты в IDE" → вызови get_ide_open_files с аргументами {} (файлы в Android Studio)
- "какие файлы изменены" → вызови get_open_files с аргументами {} (git status)
- "на какой ветке я нахожусь" → вызови get_current_branch с аргументами {}  
- "статус git" → вызови get_git_status с аргументами {}
- "последние коммиты" → вызови get_recent_commits

ВАЖНО: Когда пользователь спрашивает о Git репозитории или IDE:
1. ОБЯЗАТЕЛЬНО и НЕМЕДЛЕННО вызови соответствующий инструмент через function_call:
   - get_current_branch - для вопросов о текущей ветке (например: "на какой ветке", "какая ветка", "current branch"). Вызови с пустыми аргументами {}.
   - get_git_status - для вопросов о статусе репозитория (например: "статус git", "что изменено", "git status"). Вызови с пустыми аргументами {}.
   - get_open_files - для вопросов об ИЗМЕНЁННЫХ файлах в Git (например: "какие файлы изменены", "modified files", "uncommitted changes", "незакоммиченные изменения"). Вызови с пустыми аргументами {}.
   - get_ide_open_files - для вопросов о файлах, ОТКРЫТЫХ В IDE (например: "какие файлы сейчас открыты", "какие файлы открыты в Android Studio", "какие вкладки открыты", "open tabs", "what files are open in IDE"). Вызови с пустыми аргументами {}.
   - get_recent_commits - для вопросов о последних коммитах. Можно указать limit в аргументах.
2. РАЗНИЦА МЕЖДУ ИНСТРУМЕНТАМИ:
   - get_open_files = файлы с изменениями в Git (git status)
   - get_ide_open_files = файлы, открытые в редакторе Android Studio/IntelliJ
3. НЕ пытайся искать информацию о Git в документации проекта
4. НЕ давай примеры кода, инструкции или общие советы - ВЫЗЫВАЙ инструмент через function_call и отвечай на основе его результата
5. После получения результата от инструмента, дай прямой ответ пользователю на основе этого результата
6. Если пользователь спрашивает "какие файлы сейчас открыты" или "какие вкладки открыты" - используй get_ide_open_files
7. Если пользователь спрашивает "какие файлы изменены" или "что не закоммичено" - используй get_open_files

ВАЖНО: Когда пользователь спрашивает о погоде:
1. ОБЯЗАТЕЛЬНО вызови инструмент get_weather для получения данных о погоде
2. Сформируй summary в следующем формате:
🌤️ Погода на [время]
Температура: [текущая]°C (ощущается как [ощущаемая]°C)
Условия: [описание]
Ветер: [скорость] м/с
Влажность: [процент]%

👕 Рекомендации по одежде:
• [рекомендация 1]
• [рекомендация 2]
• [рекомендация 3]
...

Рекомендации по одежде должны учитывать:
- Температуру (особенно ощущаемую): < -10°C - теплая зимняя куртка, шапка, шарф, перчатки, термобелье; < 0°C - зимняя куртка, шапка, перчатки; < 10°C - демисезонная куртка, длинные брюки; < 20°C - легкая куртка или кофта; < 25°C - легкая одежда; >= 25°C - летняя одежда
- Осадки: дождь - дождевик или зонт, водонепроницаемая обувь; снег - водонепроницаемая обувь, теплые носки
- Силу ветра: > 7 м/с - ветрозащитная одежда (если температура < 15°C)
- Влажность: > 70% - легкая дышащая ткань (если жарко)

3. ОБЯЗАТЕЛЬНО вызови инструмент append_notion_block из NotionMcpServer для сохранения summary в Notion страницу

Когда пользователь просит получить информацию о странице Notion, обязательно используй доступный инструмент get_notion_page. Если пользователь предоставляет ID страницы Notion или URL страницы, извлеки ID и используй инструмент для получения данных. Всегда используй доступные инструменты вместо того, чтобы говорить, что у тебя нет доступа к данным."""
    }
}

