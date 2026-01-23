package org.example.client.ollama

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.example.ui.ConsoleUI

/**
 * Клиент для работы с Ollama API
 * Документация: https://docs.ollama.com/api/introduction
 * 
 * По умолчанию Ollama работает на http://localhost:11434/api
 */
class OllamaClient(
    private val baseUrl: String = "http://localhost:11434/api",
    private val defaultModel: String = "llama3.2"
) {
    private val json = createJsonSerializer()
    private val client = createHttpClient()

    /**
     * Отправляет сообщение в чат с моделью Ollama.
     * @param systemPrompt необязательный системный промпт (добавляется в начало диалога)
     * @param options явные опции; иначе собираются из temperature, maxTokens, numCtx, topP, repeatPenalty
     */
    suspend fun chat(
        message: String,
        model: String = defaultModel,
        conversationHistory: List<OllamaMessage> = emptyList(),
        systemPrompt: String? = null,
        temperature: Double? = null,
        maxTokens: Int? = null,
        numCtx: Int? = null,
        topP: Double? = null,
        repeatPenalty: Double? = null,
        options: OllamaOptions? = null
    ): OllamaChatResponse {
        val messages = buildList {
            if (!systemPrompt.isNullOrBlank()) {
                add(OllamaMessage(role = "system", content = systemPrompt))
            }
            addAll(conversationHistory)
            add(OllamaMessage(role = "user", content = message))
        }

        val opts = options ?: if (temperature != null || maxTokens != null || numCtx != null || topP != null || repeatPenalty != null) {
            OllamaOptions(
                temperature = temperature,
                numPredict = maxTokens,
                numCtx = numCtx,
                topP = topP,
                repeatPenalty = repeatPenalty
            )
        } else null

        val request = OllamaChatRequest(
            model = model,
            messages = messages,
            stream = false,
            options = opts
        )

        return sendChatRequest(request)
    }

    /**
     * Отправляет системный промпт и сообщение пользователя.
     * Поддерживает numCtx, topP, repeatPenalty.
     */
    suspend fun chatWithSystemPrompt(
        systemPrompt: String,
        userMessage: String,
        model: String = defaultModel,
        temperature: Double? = null,
        maxTokens: Int? = null,
        numCtx: Int? = null,
        topP: Double? = null,
        repeatPenalty: Double? = null,
        options: OllamaOptions? = null
    ): OllamaChatResponse {
        val messages = listOf(
            OllamaMessage(role = "system", content = systemPrompt),
            OllamaMessage(role = "user", content = userMessage)
        )

        val opts = options ?: if (temperature != null || maxTokens != null || numCtx != null || topP != null || repeatPenalty != null) {
            OllamaOptions(
                temperature = temperature,
                numPredict = maxTokens,
                numCtx = numCtx,
                topP = topP,
                repeatPenalty = repeatPenalty
            )
        } else null

        val request = OllamaChatRequest(
            model = model,
            messages = messages,
            stream = false,
            options = opts
        )

        return sendChatRequest(request)
    }

    /**
     * Получает список доступных моделей
     * Документация: https://docs.ollama.com/api/tags
     */
    suspend fun listModels(): List<OllamaModelInfo> {
        val response: OllamaListModelsResponse = client.get("$baseUrl/tags") {
            contentType(ContentType.Application.Json)
        }.body()
        return response.models
    }

    /**
     * Получает список запущенных моделей (загруженных в память)
     * Документация: https://docs.ollama.com/api/ps
     */
    suspend fun listRunningModels(): List<OllamaRunningModel> {
        val response: OllamaRunningModelsResponse = client.get("$baseUrl/ps") {
            contentType(ContentType.Application.Json)
        }.body()
        return response.models
    }

    /**
     * Генерирует ответ для предоставленного промпта (без истории диалога).
     * Поддерживает numCtx, topP, repeatPenalty, options.
     */
    suspend fun generate(
        prompt: String,
        model: String = defaultModel,
        systemPrompt: String? = null,
        temperature: Double? = null,
        maxTokens: Int? = null,
        numCtx: Int? = null,
        topP: Double? = null,
        repeatPenalty: Double? = null,
        options: OllamaOptions? = null,
        format: String? = null,
        images: List<String>? = null,
        keepAlive: String? = null,
        think: Boolean? = null
    ): OllamaGenerateResponse {
        val opts = options ?: if (temperature != null || maxTokens != null || numCtx != null || topP != null || repeatPenalty != null) {
            OllamaOptions(
                temperature = temperature,
                numPredict = maxTokens,
                numCtx = numCtx,
                topP = topP,
                repeatPenalty = repeatPenalty
            )
        } else null
        val request = OllamaGenerateRequest(
            model = model,
            prompt = prompt,
            system = systemPrompt,
            stream = false,
            format = format,
            images = images,
            keepAlive = keepAlive,
            think = think,
            options = opts
        )
        return sendGenerateRequest(request)
    }

    /**
     * Отправляет чат-запрос с поддержкой инструментов (tools).
     * Поддерживает numCtx, topP, repeatPenalty, options.
     */
    suspend fun chatWithTools(
        message: String,
        model: String = defaultModel,
        conversationHistory: List<OllamaMessage> = emptyList(),
        systemPrompt: String? = null,
        tools: List<OllamaTool>? = null,
        temperature: Double? = null,
        maxTokens: Int? = null,
        numCtx: Int? = null,
        topP: Double? = null,
        repeatPenalty: Double? = null,
        options: OllamaOptions? = null,
        format: String? = null,
        keepAlive: String? = null,
        think: Boolean? = null
    ): OllamaChatResponse {
        val messages = buildList {
            if (!systemPrompt.isNullOrBlank()) add(OllamaMessage(role = "system", content = systemPrompt))
            addAll(conversationHistory)
            add(OllamaMessage(role = "user", content = message))
        }
        val opts = options ?: if (temperature != null || maxTokens != null || numCtx != null || topP != null || repeatPenalty != null) {
            OllamaOptions(
                temperature = temperature,
                numPredict = maxTokens,
                numCtx = numCtx,
                topP = topP,
                repeatPenalty = repeatPenalty
            )
        } else null
        val request = OllamaChatRequest(
            model = model,
            messages = messages,
            stream = false,
            format = format,
            tools = tools,
            keepAlive = keepAlive,
            think = think,
            options = opts
        )
        return sendChatRequest(request)
    }

    /**
     * Проверяет доступность Ollama API
     */
    suspend fun isAvailable(): Boolean {
        return try {
            val response = client.get("$baseUrl/tags") {
                timeout {
                    requestTimeoutMillis = 5000
                    connectTimeoutMillis = 3000
                }
            }
            response.status.isSuccess()
        } catch (e: Exception) {
            false
        }
    }

    fun close() = client.close()

    private suspend fun sendChatRequest(request: OllamaChatRequest): OllamaChatResponse {
        val startTime = System.currentTimeMillis()
        val body = json.encodeToString(OllamaChatRequest.serializer(), request)
        if (System.getenv("OLLAMA_DEBUG") == "true") {
            println("🦙 [OLLAMA_DEBUG] Chat request options: ${request.options}")
            println("🦙 [OLLAMA_DEBUG] Chat request body (excerpt): ${body.take(500)}...")
        }
        val response = client.post("$baseUrl/chat") {
            contentType(ContentType.Application.Json)
            setBody(io.ktor.http.content.TextContent(body, ContentType.Application.Json))
        }
        val responseTimeMs = System.currentTimeMillis() - startTime

        if (!response.status.isSuccess()) {
            val errorBody = response.bodyAsText()
            throw RuntimeException("Ошибка Ollama API: ${response.status} - $errorBody")
        }

        // Ollama может возвращать NDJSON даже при stream: false
        // Читаем ответ как текст и парсим последнюю строку
        val responseText = response.bodyAsText()
        
        // Отладочная информация
        if (responseText.isBlank()) {
            throw RuntimeException("Пустой ответ от Ollama API. Проверьте, что Ollama запущен и модель доступна.")
        }
        
        val result = parseChatResponse(responseText)
        
        // Логируем информацию о запросе
        logChatResponse(result, responseTimeMs)
        
        return result
    }
    
    /**
     * Парсит ответ от Ollama, который может быть в формате NDJSON
     * Собирает все части streaming ответа и возвращает финальный результат
     */
    private fun parseChatResponse(responseText: String): OllamaChatResponse {
        if (responseText.isBlank()) {
            throw RuntimeException("Пустой ответ от Ollama API")
        }
        
        val lines = responseText.trim().lines().filter { it.isNotBlank() }
        
        if (lines.isEmpty()) {
            throw RuntimeException("Нет данных в ответе от Ollama")
        }
        
        // Если одна строка - обычный JSON
        if (lines.size == 1) {
            return try {
                json.decodeFromString<OllamaChatResponse>(lines[0])
            } catch (e: Exception) {
                throw RuntimeException("Ошибка парсинга ответа Ollama: ${e.message}\nОтвет: ${lines[0]}")
            }
        }
        
        // Множественные строки - NDJSON формат
        // Собираем все части и находим строку с done: true
        var finalResponse: OllamaChatResponse? = null
        val accumulatedMessage = StringBuilder()
        var accumulatedResponse: OllamaChatResponse? = null
        
        for (line in lines) {
            try {
                val partialResponse = json.decodeFromString<OllamaChatResponse>(line)
                
                // Если есть message и message.content, накапливаем его
                val message = partialResponse.message
                if (message != null && message.content != null && message.content.isNotBlank()) {
                    accumulatedMessage.append(message.content)
                    
                    // Создаем накопленный ответ
                    accumulatedResponse = partialResponse.copy(
                        message = OllamaChatMessage(
                            role = message.role,
                            content = accumulatedMessage.toString(),
                            thinking = message.thinking,
                            toolCalls = message.toolCalls,
                            images = message.images
                        )
                    )
                } else if (message == null && partialResponse.done == false) {
                    // Пропускаем строки без message до тех пор, пока не получим message
                    continue
                }
                
                // Если done: true - это финальный ответ
                if (partialResponse.done == true) {
                    finalResponse = accumulatedResponse ?: partialResponse
                    // Если все еще нет накопленного ответа, но есть message - используем его
                    if (finalResponse == null && message != null) {
                        finalResponse = partialResponse
                    }
                    break
                }
                
                // Если это последняя строка, используем накопленный или текущий ответ
                if (line == lines.last()) {
                    finalResponse = accumulatedResponse ?: if (message != null) partialResponse else null
                }
            } catch (e: Exception) {
                // Игнорируем ошибки парсинга промежуточных строк
                println("⚠️ Предупреждение: не удалось распарсить строку: ${e.message}")
            }
        }
        
        return finalResponse ?: throw RuntimeException(
            "Не удалось найти финальный ответ в NDJSON потоке\nОтвет: $responseText"
        )
    }

    private suspend fun sendGenerateRequest(request: OllamaGenerateRequest): OllamaGenerateResponse {
        val startTime = System.currentTimeMillis()
        val body = json.encodeToString(OllamaGenerateRequest.serializer(), request)
        if (System.getenv("OLLAMA_DEBUG") == "true") {
            println("🦙 [OLLAMA_DEBUG] Generate request options: ${request.options}")
            println("🦙 [OLLAMA_DEBUG] Generate request body (excerpt): ${body.take(500)}...")
        }
        val response = client.post("$baseUrl/generate") {
            contentType(ContentType.Application.Json)
            setBody(io.ktor.http.content.TextContent(body, ContentType.Application.Json))
        }
        val responseTimeMs = System.currentTimeMillis() - startTime

        if (!response.status.isSuccess()) {
            val errorBody = response.bodyAsText()
            throw RuntimeException("Ошибка Ollama API: ${response.status} - $errorBody")
        }

        // Ollama может возвращать NDJSON даже при stream: false
        // Читаем ответ как текст и парсим последнюю строку
        val responseText = response.bodyAsText()
        val result = parseGenerateResponse(responseText)
        
        // Логируем информацию о запросе
        logGenerateResponse(result, responseTimeMs)
        
        return result
    }
    
    /**
     * Парсит ответ от Ollama для generate endpoint, который может быть в формате NDJSON
     * Собирает все части streaming ответа и возвращает финальный результат
     */
    private fun parseGenerateResponse(responseText: String): OllamaGenerateResponse {
        if (responseText.isBlank()) {
            throw RuntimeException("Пустой ответ от Ollama API")
        }
        
        val lines = responseText.trim().lines().filter { it.isNotBlank() }
        
        if (lines.isEmpty()) {
            throw RuntimeException("Нет данных в ответе от Ollama")
        }
        
        // Если одна строка - обычный JSON
        if (lines.size == 1) {
            return try {
                json.decodeFromString<OllamaGenerateResponse>(lines[0])
            } catch (e: Exception) {
                throw RuntimeException("Ошибка парсинга ответа Ollama: ${e.message}\nОтвет: ${lines[0]}")
            }
        }
        
        // Множественные строки - NDJSON формат
        // Собираем все части и находим строку с done: true
        var finalResponse: OllamaGenerateResponse? = null
        val accumulatedResponse = StringBuilder()
        
        for (line in lines) {
            try {
                val partialResponse = json.decodeFromString<OllamaGenerateResponse>(line)
                
                // Накапливаем response
                if (partialResponse.response != null) {
                    accumulatedResponse.append(partialResponse.response)
                }
                
                // Если done: true - это финальный ответ
                if (partialResponse.done == true) {
                    finalResponse = partialResponse.copy(
                        response = accumulatedResponse.toString()
                    )
                    break
                }
                
                // Если это последняя строка, используем её
                if (line == lines.last()) {
                    finalResponse = partialResponse.copy(
                        response = accumulatedResponse.toString()
                    )
                }
            } catch (e: Exception) {
                // Игнорируем ошибки парсинга промежуточных строк
                println("⚠️ Предупреждение: не удалось распарсить строку: ${e.message}")
            }
        }
        
        return finalResponse ?: throw RuntimeException(
            "Не удалось найти финальный ответ в NDJSON потоке\nОтвет: $responseText"
        )
    }

    private fun logChatResponse(response: OllamaChatResponse, responseTimeMs: Long) {
        val evalCount = response.evalCount ?: 0
        val promptEvalCount = response.promptEvalCount ?: 0
        val totalTokens = evalCount + promptEvalCount
        
        if (totalTokens > 0) {
            ConsoleUI.printOllamaResponse(
                model = response.model,
                responseTimeMs = responseTimeMs,
                promptTokens = promptEvalCount,
                outputTokens = evalCount,
                totalTokens = totalTokens
            )
        }
        
        // Логируем дополнительную информацию, если доступна
        if (response.doneReason != null) {
            println("   📌 Причина завершения: ${response.doneReason}")
        }
        if (response.message?.thinking != null && response.message.thinking.isNotBlank()) {
            println("   💭 Мышление модели: ${response.message.thinking.take(100)}...")
        }
    }

    private fun logGenerateResponse(response: OllamaGenerateResponse, responseTimeMs: Long) {
        val evalCount = response.evalCount ?: 0
        val promptEvalCount = response.promptEvalCount ?: 0
        val totalTokens = evalCount + promptEvalCount
        
        if (totalTokens > 0) {
            ConsoleUI.printOllamaResponse(
                model = response.model,
                responseTimeMs = responseTimeMs,
                promptTokens = promptEvalCount,
                outputTokens = evalCount,
                totalTokens = totalTokens
            )
        }
        
        // Логируем дополнительную информацию, если доступна
        if (response.doneReason != null) {
            println("   📌 Причина завершения: ${response.doneReason}")
        }
        if (response.thinking != null && response.thinking.isNotBlank()) {
            println("   💭 Мышление модели: ${response.thinking.take(100)}...")
        }
    }

    private fun createJsonSerializer() = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        prettyPrint = false
        isLenient = true
    }

    private fun createHttpClient() = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = REQUEST_TIMEOUT_MS
            connectTimeoutMillis = CONNECT_TIMEOUT_MS
            socketTimeoutMillis = SOCKET_TIMEOUT_MS
        }
        install(ContentNegotiation) { 
            json(json) 
        }
        install(Logging) {
            logger = object : Logger {
                override fun log(message: String) = ConsoleUI.printHttpLog(message)
            }
            level = LogLevel.INFO
        }
    }

    companion object {
        private const val REQUEST_TIMEOUT_MS = 300_000L // 5 минут для больших моделей
        private const val CONNECT_TIMEOUT_MS = 30_000L
        private const val SOCKET_TIMEOUT_MS = 300_000L
    }
}
