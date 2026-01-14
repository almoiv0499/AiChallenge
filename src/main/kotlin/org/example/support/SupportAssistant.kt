package org.example.support

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.example.client.OpenRouterClient
import org.example.config.OpenRouterConfig
import org.example.embedding.RagService
import org.example.embedding.SearchResult
import org.example.models.*
import java.io.File

/**
 * Ассистент службы поддержки.
 * Интегрирует RAG (поиск по документации) и CRM (контекст пользователя/тикета)
 * для ответов на вопросы пользователей.
 */
class SupportAssistant(
    private val client: OpenRouterClient,
    private val ragService: RagService,
    private val crmStorage: CrmStorage,
    private val model: String = OpenRouterConfig.DEFAULT_MODEL
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val conversationHistory = mutableListOf<JsonElement>()
    
    // Кэш документации для поиска без эмбеддингов
    private val documentationCache: Map<String, String> by lazy { loadDocumentation() }

    init {
        addSystemPrompt()
        println("🤖 Support Assistant инициализирован")
        println("   📚 RAG: ${if (ragService.hasDocuments()) "✅ ${ragService.getDocumentCount()} документов" else "⚠️ Используется текстовый поиск"}")
        println("   📄 Документация: ${documentationCache.size} файлов загружено")
        println("   👥 CRM: ${crmStorage.getAllUsers().size} пользователей, ${crmStorage.getAllTickets().size} тикетов")
    }

    /**
     * Обработка вопроса пользователя.
     */
    suspend fun answerQuestion(
        question: String,
        userId: String? = null,
        ticketId: String? = null
    ): SupportResponse {
        println("\n❓ Вопрос: $question")
        
        // 1. Собираем контекст из CRM
        val crmContext = buildCrmContext(userId, ticketId)
        
        // 2. Ищем релевантную информацию в документации
        val relevantDocs = findRelevantDocumentation(question, ticketId)
        
        // 3. Формируем расширенный промпт с контекстом
        val enrichedPrompt = buildEnrichedPrompt(question, crmContext, relevantDocs)
        
        // 4. Получаем ответ от LLM
        val response = generateResponse(enrichedPrompt)
        
        // 5. Если есть тикет, добавляем ответ в историю
        if (ticketId != null) {
            addResponseToTicket(ticketId, response)
        }
        
        return SupportResponse(
            answer = response,
            ragSources = relevantDocs.map { it.source }.distinct(),
            userContext = crmContext != null,
            ticketId = ticketId
        )
    }

    /**
     * Получить контекст для ответа на конкретный тикет.
     * Использует данные тикета для поиска релевантной документации.
     */
    suspend fun handleTicket(ticketId: String): SupportResponse {
        val ticket = crmStorage.getTicket(ticketId)
            ?: return SupportResponse(
                answer = "Тикет не найден: $ticketId",
                ragSources = emptyList(),
                userContext = false,
                ticketId = ticketId
            )
        
        println("\n📋 Обработка тикета: ${ticket.id}")
        println("   Тема: ${ticket.subject}")
        println("   Категория: ${ticket.category}")
        println("   Код ошибки: ${ticket.metadata["error_code"] ?: "не указан"}")
        
        // Формируем запрос из данных тикета
        val searchQuery = buildTicketSearchQuery(ticket)
        
        return answerQuestion(searchQuery, userId = ticket.userId, ticketId = ticketId)
    }

    /**
     * Очистить историю разговора.
     */
    fun clearHistory() {
        conversationHistory.clear()
        addSystemPrompt()
        println("🗑️ История разговора очищена")
    }

    // ==================== ПОИСК В ДОКУМЕНТАЦИИ ====================

    /**
     * Загружает документацию из файлов в память.
     */
    private fun loadDocumentation(): Map<String, String> {
        val docs = mutableMapOf<String, String>()
        val docsDir = File("docs/support")
        
        if (docsDir.exists()) {
            docsDir.listFiles { file -> file.extension == "md" }?.forEach { file ->
                try {
                    docs[file.path] = file.readText()
                    println("   📄 Загружен: ${file.name}")
                } catch (e: Exception) {
                    println("   ⚠️ Ошибка загрузки ${file.name}: ${e.message}")
                }
            }
        }
        
        return docs
    }

    /**
     * Ищет релевантную документацию по ключевым словам.
     */
    private suspend fun findRelevantDocumentation(
        query: String, 
        ticketId: String? = null
    ): List<DocumentSection> {
        val results = mutableListOf<DocumentSection>()
        
        // Собираем ключевые слова для поиска
        val keywords = extractKeywords(query, ticketId)
        
        // Ищем в кэше документации
        for ((source, content) in documentationCache) {
            val sections = findMatchingSections(content, keywords, source)
            results.addAll(sections)
        }
        
        // Сортируем по релевантности (количеству совпадений)
        val sortedResults = results
            .sortedByDescending { it.relevanceScore }
            .take(5)
        
        if (sortedResults.isNotEmpty()) {
            println("📚 Найдено ${sortedResults.size} релевантных разделов документации")
        }
        
        return sortedResults
    }

    /**
     * Извлекает ключевые слова из запроса и тикета.
     */
    private fun extractKeywords(query: String, ticketId: String?): List<String> {
        val keywords = mutableSetOf<String>()
        
        // Ключевые слова из запроса
        val queryWords = query.lowercase()
            .replace(Regex("[^a-zа-яё0-9_\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length > 3 }
        keywords.addAll(queryWords)
        
        // Если есть тикет, добавляем данные из него
        if (ticketId != null) {
            val ticket = crmStorage.getTicket(ticketId)
            if (ticket != null) {
                // Код ошибки - важный ключ для поиска
                ticket.metadata["error_code"]?.let { keywords.add(it.lowercase()) }
                
                // Категория
                keywords.add(ticket.category.name.lowercase())
                
                // Теги
                keywords.addAll(ticket.tags.map { it.lowercase() })
                
                // Ключевые слова из метаданных
                ticket.metadata["browser"]?.let { keywords.add(it.lowercase()) }
                ticket.metadata["device"]?.let { keywords.add(it.lowercase()) }
            }
        }
        
        // Добавляем синонимы и связанные термины
        val synonyms = mapOf(
            "авторизация" to listOf("oauth", "вход", "логин", "аутентификация", "2fa"),
            "оплата" to listOf("платеж", "карта", "подписка", "payment", "declined"),
            "краш" to listOf("вылетает", "падает", "crash", "ошибка"),
            "синхронизация" to listOf("sync", "данные", "устройства")
        )
        
        val expandedKeywords = keywords.toMutableSet()
        for (keyword in keywords) {
            synonyms[keyword]?.let { expandedKeywords.addAll(it) }
        }
        
        return expandedKeywords.toList()
    }

    /**
     * Находит разделы документации, соответствующие ключевым словам.
     */
    private fun findMatchingSections(
        content: String,
        keywords: List<String>,
        source: String
    ): List<DocumentSection> {
        val sections = mutableListOf<DocumentSection>()
        val lowerContent = content.lowercase()
        
        // Разбиваем на секции по заголовкам
        val sectionPattern = Regex("(?m)^#{1,3}\\s+(.+?)$")
        val matches = sectionPattern.findAll(content).toList()
        
        for (i in matches.indices) {
            val match = matches[i]
            val sectionTitle = match.groupValues[1]
            val sectionStart = match.range.first
            val sectionEnd = if (i < matches.size - 1) matches[i + 1].range.first else content.length
            val sectionContent = content.substring(sectionStart, sectionEnd).trim()
            val lowerSectionContent = sectionContent.lowercase()
            
            // Подсчитываем релевантность
            var score = 0
            for (keyword in keywords) {
                if (lowerSectionContent.contains(keyword)) {
                    score += when {
                        sectionTitle.lowercase().contains(keyword) -> 10 // В заголовке - высокий вес
                        else -> 1
                    }
                }
            }
            
            if (score > 0) {
                sections.add(DocumentSection(
                    title = sectionTitle,
                    content = sectionContent.take(1500), // Ограничиваем размер
                    source = source,
                    relevanceScore = score
                ))
            }
        }
        
        return sections
    }

    /**
     * Формирует поисковый запрос из данных тикета.
     */
    private fun buildTicketSearchQuery(ticket: Ticket): String = buildString {
        append(ticket.subject)
        append(". ")
        append(ticket.description)
        
        // Добавляем код ошибки, если есть
        ticket.metadata["error_code"]?.let {
            append(" Код ошибки: $it.")
        }
        
        // Последнее сообщение пользователя
        ticket.messages
            .filter { it.senderType == MessageSenderType.USER }
            .lastOrNull()
            ?.let { append(" ${it.content}") }
    }

    // ==================== ПОСТРОЕНИЕ ПРОМПТА ====================

    private fun buildCrmContext(userId: String?, ticketId: String?): String? {
        val contextParts = mutableListOf<String>()
        
        if (userId != null) {
            val userContext = crmStorage.getUserContext(userId)
            if (userContext != null) {
                contextParts.add(userContext.toContextString())
            }
        }
        
        if (ticketId != null) {
            val ticket = crmStorage.getTicket(ticketId)
            if (ticket != null) {
                contextParts.add(buildTicketContext(ticket))
            }
        }
        
        return contextParts.takeIf { it.isNotEmpty() }?.joinToString("\n\n")
    }

    private fun buildTicketContext(ticket: Ticket): String = buildString {
        appendLine("=== ТЕКУЩИЙ ТИКЕТ ===")
        appendLine("ID: ${ticket.id}")
        appendLine("Тема: ${ticket.subject}")
        appendLine("Описание: ${ticket.description}")
        appendLine("Статус: ${ticket.status}")
        appendLine("Приоритет: ${ticket.priority}")
        appendLine("Категория: ${ticket.category}")
        
        if (ticket.metadata.isNotEmpty()) {
            appendLine("\nТехнические данные:")
            ticket.metadata.forEach { (key, value) ->
                appendLine("  • $key: $value")
            }
        }
        
        if (ticket.messages.isNotEmpty()) {
            appendLine("\nИстория переписки:")
            ticket.messages.takeLast(5).forEach { msg ->
                val sender = when (msg.senderType) {
                    MessageSenderType.USER -> "Клиент"
                    MessageSenderType.SUPPORT -> "Поддержка"
                    MessageSenderType.BOT -> "Бот"
                }
                appendLine("  [$sender]: ${msg.content}")
            }
        }
    }

    private fun buildEnrichedPrompt(
        question: String,
        crmContext: String?,
        relevantDocs: List<DocumentSection>
    ): String = buildString {
        // CRM контекст
        if (crmContext != null) {
            appendLine("📋 КОНТЕКСТ КЛИЕНТА И ТИКЕТА:")
            appendLine(crmContext)
            appendLine()
        }
        
        // Документация (RAG)
        if (relevantDocs.isNotEmpty()) {
            appendLine("📚 РЕЛЕВАНТНАЯ ДОКУМЕНТАЦИЯ (используй эту информацию для ответа):")
            relevantDocs.forEachIndexed { index, doc ->
                appendLine("--- Раздел ${index + 1}: ${doc.title} ---")
                appendLine(doc.content)
                appendLine()
            }
        }
        
        // Вопрос/проблема
        appendLine("❓ ПРОБЛЕМА КЛИЕНТА:")
        appendLine(question)
        appendLine()
        appendLine("📝 ЗАДАЧА: На основе предоставленной документации и контекста тикета, сформулируй подробный ответ с конкретными шагами решения проблемы.")
    }

    // ==================== ГЕНЕРАЦИЯ ОТВЕТА ====================

    private suspend fun generateResponse(prompt: String): String {
        addUserMessage(prompt)
        
        val request = OpenRouterRequest(
            model = model,
            input = conversationHistory.toList(),
            tools = null,
            temperature = OpenRouterConfig.Temperature.DEFAULT
        )
        
        return try {
            val response = client.createResponse(request)
            val text = response.output
                ?.firstOrNull()
                ?.content
                ?.firstOrNull { it.type == "output_text" || it.type == "text" }
                ?.text
                ?: "Не удалось получить ответ"
            
            addAssistantMessage(text)
            text
        } catch (e: Exception) {
            "Ошибка генерации ответа: ${e.message}"
        }
    }

    private fun addResponseToTicket(ticketId: String, response: String) {
        val message = TicketMessage(
            id = "msg_bot_${System.currentTimeMillis()}",
            ticketId = ticketId,
            senderType = MessageSenderType.BOT,
            senderId = "support_assistant",
            content = response
        )
        crmStorage.addMessageToTicket(ticketId, message)
        println("💾 Ответ добавлен к тикету $ticketId")
    }

    // ==================== ИСТОРИЯ РАЗГОВОРА ====================

    private fun addSystemPrompt() {
        val systemPrompt = """
Ты — ассистент службы поддержки. Твоя задача — помогать пользователям решать проблемы на основе предоставленной документации.

КРИТИЧЕСКИ ВАЖНО:
1. ВСЕГДА используй информацию из раздела "РЕЛЕВАНТНАЯ ДОКУМЕНТАЦИЯ" для формирования ответа
2. Если в документации есть конкретные шаги решения - приводи их полностью
3. Учитывай технические данные из тикета (код ошибки, браузер, устройство)
4. Персонализируй ответ: обращайся к клиенту по имени, учитывай его тарифный план

ФОРМАТ ОТВЕТА:
1. Краткое описание проблемы (1-2 предложения)
2. Пошаговое решение из документации
3. Если проблема не решается - рекомендация обратиться в поддержку

Отвечай на русском языке, вежливо и профессионально.
        """.trimIndent()
        
        val message = OpenRouterInputMessage(
            role = "system",
            content = listOf(OpenRouterInputContentItem(type = "input_text", text = systemPrompt))
        )
        conversationHistory.add(json.encodeToJsonElement(OpenRouterInputMessage.serializer(), message))
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
}

/**
 * Раздел документации.
 */
data class DocumentSection(
    val title: String,
    val content: String,
    val source: String,
    val relevanceScore: Int
)

/**
 * Ответ ассистента поддержки.
 */
data class SupportResponse(
    val answer: String,
    val ragSources: List<String>,
    val userContext: Boolean,
    val ticketId: String?
) {
    fun toFormattedString(): String = buildString {
        appendLine("═".repeat(60))
        appendLine("🤖 ОТВЕТ АССИСТЕНТА")
        appendLine("═".repeat(60))
        appendLine()
        appendLine(answer)
        appendLine()
        
        if (ragSources.isNotEmpty()) {
            appendLine("📚 Источники:")
            ragSources.forEach { source ->
                appendLine("  • $source")
            }
        }
        
        if (ticketId != null) {
            appendLine()
            appendLine("📋 Тикет: $ticketId")
        }
        
        appendLine("═".repeat(60))
    }
}

/**
 * Сессия поддержки для интерактивного общения.
 */
class SupportSession(
    private val assistant: SupportAssistant,
    private val userId: String?,
    private val userContext: UserContext?
) {
    init {
        println("\n" + "═".repeat(60))
        println("🎧 СЕССИЯ ПОДДЕРЖКИ НАЧАТА")
        if (userContext != null) {
            println("👤 Клиент: ${userContext.user.name} (${userContext.user.email})")
            println("📋 Активных тикетов: ${userContext.activeTickets.size}")
        }
        println("═".repeat(60))
    }

    suspend fun ask(question: String): SupportResponse {
        return assistant.answerQuestion(question, userId)
    }

    suspend fun handleTicket(ticketId: String): SupportResponse {
        return assistant.handleTicket(ticketId)
    }

    fun end() {
        println("\n" + "═".repeat(60))
        println("👋 Сессия поддержки завершена")
        println("═".repeat(60))
    }
}
