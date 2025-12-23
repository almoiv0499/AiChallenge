package org.example.agent

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.example.client.OpenRouterClient
import org.example.config.OpenRouterConfig
import org.example.embedding.RagService
import org.example.models.*
import org.example.storage.HistoryStorage
import org.example.tools.ToolRegistry
import org.example.ui.ConsoleUI
import org.example.agent.android.DeviceSearchExecutor

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
    private var ragEnabled: Boolean = true
    private var comparisonMode: Boolean = false

    init {
        addSystemPrompt()
        loadSavedSummary()
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
                    toolCalls = emptyList(),
                    temperature = temperature
                )
            }
        }
        
        // Если включен режим сравнения, выполняем сравнение
        if (comparisonMode) {
            if (ragService == null) {
                println("⚠️ Режим сравнения требует RAG сервис, но он не инициализирован")
                println("   Продолжаем в обычном режиме без сравнения\n")
            } else {
                return compareWithAndWithoutRag(userMessage)
            }
        }
        
        // RAG: поиск релевантного контекста в локальной БД
        if (ragEnabled) {
            val ragContext = ragService?.searchRelevantContext(userMessage)
            if (ragContext != null) {
                println("📚 Найден релевантный контекст в локальной базе знаний")
                // Добавляем контекст как системное сообщение перед пользовательским запросом
                addRagContext(ragContext)
            }
        }
        
        addUserMessage(userMessage)
        return executeAgentLoop()
    }
    
    /**
     * Сравнивает ответы с RAG и без RAG
     */
    suspend fun compareWithAndWithoutRag(userMessage: String): ChatResponse {
        val ragContext = ragService?.searchRelevantContext(userMessage)
        
        // Сохраняем текущее состояние истории
        val savedHistory = conversationHistory.toList()
        val savedUserMessageCount = userMessageCount
        
        // 1. Ответ БЕЗ RAG
        ConsoleUI.printComparisonStep("БЕЗ RAG")
        conversationHistory.clear()
        // Фильтруем историю, удаляя все RAG контексты
        val historyWithoutRag = filterOutRagContexts(savedHistory)
        conversationHistory.addAll(historyWithoutRag)
        userMessageCount = savedUserMessageCount
        addUserMessage(userMessage)
        val answerWithoutRag = executeAgentLoop()
        
        // 2. Ответ С RAG
        ConsoleUI.printComparisonStep("С RAG")
        conversationHistory.clear()
        conversationHistory.addAll(savedHistory)
        userMessageCount = savedUserMessageCount
        if (ragContext != null) {
            println("📚 Найден релевантный контекст в локальной базе знаний")
            addRagContext(ragContext)
        }
        addUserMessage(userMessage)
        val answerWithRag = executeAgentLoop()
        
        // Восстанавливаем историю
        conversationHistory.clear()
        conversationHistory.addAll(savedHistory)
        userMessageCount = savedUserMessageCount
        
        // Выводим сравнение
        ConsoleUI.printRagComparison(
            question = userMessage,
            answerWithRag = answerWithRag,
            answerWithoutRag = answerWithoutRag,
            ragContext = ragContext
        )
        
        // Восстанавливаем историю и добавляем финальный ответ с RAG
        conversationHistory.clear()
        conversationHistory.addAll(savedHistory)
        userMessageCount = savedUserMessageCount
        if (ragContext != null) {
            addRagContext(ragContext)
        }
        addUserMessage(userMessage)
        addAssistantMessage(answerWithRag.response)
        
        // Возвращаем ответ с RAG как основной
        return answerWithRag
    }
    
    /**
     * Включает/выключает RAG
     */
    fun setRagEnabled(enabled: Boolean) {
        ragEnabled = enabled
    }
    
    /**
     * Включает/выключает режим сравнения
     */
    fun setComparisonMode(enabled: Boolean) {
        comparisonMode = enabled
    }
    
    /**
     * Получает текущее состояние RAG
     */
    fun isRagEnabled(): Boolean = ragEnabled
    
    /**
     * Получает текущий режим сравнения
     */
    fun isComparisonMode(): Boolean = comparisonMode
    
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

    fun clearHistory() {
        conversationHistory.clear()
        userMessageCount = 0
        historyStorage.clearAllSummaries()
        addSystemPrompt()
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
                            finalMessageText = text
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
                    apiResponse = apiResponse,
                    temperature = temperature
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

    private fun addRagContext(context: String) {
        val msg = OpenRouterInputMessage(
            role = "system",
            content = listOf(OpenRouterInputContentItem(type = "input_text", text = context))
        )
        conversationHistory.add(json.encodeToJsonElement(OpenRouterInputMessage.serializer(), msg))
    }
    
    /**
     * Фильтрует историю, удаляя все RAG контексты (системные сообщения с RAG информацией)
     */
    private fun filterOutRagContexts(history: List<JsonElement>): List<JsonElement> {
        val filtered = mutableListOf<JsonElement>()
        for (element in history) {
            var shouldAdd = true
            try {
                val jsonObject = when {
                    element is JsonNull -> {
                        // Добавляем JsonNull как есть
                        shouldAdd = true
                        null
                    }
                    element is JsonObject -> element
                    else -> {
                        // Добавляем другие типы как есть
                        shouldAdd = true
                        null
                    }
                }
                
                if (jsonObject != null) {
                    val type = jsonObject["type"]?.jsonPrimitive?.content
                    val role = jsonObject["role"]?.jsonPrimitive?.content
                    
                    // Пропускаем системные сообщения с RAG контекстом
                    if (type == "message" && role == "system") {
                        val message = json.decodeFromJsonElement(OpenRouterInputMessage.serializer(), element)
                        val text = message.content.firstOrNull()?.text ?: ""
                        
                        // Проверяем, является ли это RAG контекстом
                        // RAG контекст содержит фразу "Релевантная информация из локальной базы знаний"
                        // или начинается с "[1] Источник:" (формат RAG контекста)
                        val isRagContext = text.contains("Релевантная информация из локальной базы знаний", ignoreCase = true) ||
                                text.contains("[1] Источник:", ignoreCase = true) ||
                                (text.contains("Сходство:", ignoreCase = true) && text.contains("Источник:", ignoreCase = true))
                        
                        if (isRagContext) {
                            // Пропускаем этот элемент (не добавляем в отфильтрованную историю)
                            shouldAdd = false
                        }
                    }
                }
            } catch (e: Exception) {
                // В случае ошибки парсинга добавляем элемент как есть
                shouldAdd = true
            }
            
            if (shouldAdd) {
                filtered.add(element)
            }
        }
        return filtered
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

        const val SIMPLE_SYSTEM_PROMPT = """Ты — помощник с доступом к инструментам для работы с Notion API и получения погоды. 

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

