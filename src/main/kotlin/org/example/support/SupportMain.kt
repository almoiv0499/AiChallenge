package org.example.support

import kotlinx.coroutines.runBlocking
import org.example.client.OpenRouterClient
import org.example.config.OpenRouterConfig
import org.example.embedding.DocumentIndexStorage
import org.example.embedding.DocumentIndexer
import org.example.embedding.EmbeddingClient
import org.example.embedding.RagService

/**
 * Главный класс для запуска сервиса поддержки.
 * Демонстрирует интеграцию RAG + CRM для ответов на вопросы пользователей.
 */
fun main() = runBlocking {
    println("═".repeat(60))
    println("🎧 СЕРВИС ПОДДЕРЖКИ")
    println("═".repeat(60))
    println()
    
    // 1. Проверяем API ключ
    val apiKey = System.getenv("OPENROUTER_API_KEY")
    if (apiKey.isNullOrBlank()) {
        println("❌ Ошибка: не установлена переменная окружения OPENROUTER_API_KEY")
        println("   Установите ключ: export OPENROUTER_API_KEY=your_key")
        return@runBlocking
    }
    
    // 2. Инициализируем компоненты
    println("🔧 Инициализация компонентов...")
    
    // RAG компоненты (используется для совместимости, основной поиск - текстовый)
    val embeddingClient = EmbeddingClient(apiKey)
    val storage = DocumentIndexStorage("support_docs_index.db")
    val indexer = DocumentIndexer(embeddingClient, storage)
    val ragService = RagService(embeddingClient, storage, indexer)
    
    // CRM
    val crmStorage = CrmStorage("support_crm_data.json")
    
    // OpenRouter клиент
    val openRouterClient = OpenRouterClient(apiKey)
    
    // Support Assistant (использует текстовый поиск по документации)
    val assistant = SupportAssistant(
        client = openRouterClient,
        ragService = ragService,
        crmStorage = crmStorage,
        model = OpenRouterConfig.DEFAULT_MODEL
    )
    
    println()
    
    // Интерактивный режим
    println("═".repeat(60))
    println("💬 ИНТЕРАКТИВНЫЙ РЕЖИМ")
    println("═".repeat(60))
    println("Команды:")
    println("  • 'user <email>' - установить контекст пользователя")
    println("  • 'ticket <id>' - обработать тикет (TKT-001, TKT-002, ...)")
    println("  • 'stats' - показать статистику тикетов")
    println("  • 'tickets' - показать все тикеты")
    println("  • 'clear' - очистить историю")
    println("  • 'exit' - выход")
    println()
    println("Доступные email: ${crmStorage.getAllUsers().map { it.email }.joinToString(", ")}")
    println("Доступные тикеты: ${crmStorage.getAllTickets().map { it.id }.joinToString(", ")}")
    println()
    
    var currentUserId: String? = null
    
    while (true) {
        print("❓ > ")
        val input = readLine()?.trim() ?: break
        
        when {
            input.equals("exit", ignoreCase = true) -> {
                println("👋 До свидания!")
                break
            }
            input.equals("stats", ignoreCase = true) -> {
                println(crmStorage.getTicketStats().toFormattedString())
            }
            input.equals("tickets", ignoreCase = true) -> {
                println("\n📋 ВСЕ ТИКЕТЫ:")
                crmStorage.getAllTickets().forEach { ticket ->
                    println("  [${ticket.id}] ${ticket.subject}")
                    println("      Статус: ${ticket.status}, Категория: ${ticket.category}")
                    println("      Ошибка: ${ticket.metadata["error_code"] ?: "не указана"}")
                    println()
                }
            }
            input.equals("clear", ignoreCase = true) -> {
                assistant.clearHistory()
            }
            input.startsWith("user ", ignoreCase = true) -> {
                val email = input.substringAfter("user ").trim()
                val user = crmStorage.getUserByEmail(email)
                if (user != null) {
                    currentUserId = user.id
                    println("✅ Контекст установлен: ${user.name} (${user.email})")
                    val context = crmStorage.getUserContext(user.id)
                    if (context != null && context.activeTickets.isNotEmpty()) {
                        println("   Активные тикеты: ${context.activeTickets.map { it.id }.joinToString(", ")}")
                    }
                } else {
                    println("❌ Пользователь не найден: $email")
                    println("   Доступные: ${crmStorage.getAllUsers().map { it.email }.joinToString(", ")}")
                }
            }
            input.startsWith("ticket ", ignoreCase = true) -> {
                val ticketId = input.substringAfter("ticket ").trim().uppercase()
                val response = assistant.handleTicket(ticketId)
                println(response.toFormattedString())
            }
            input.isNotBlank() -> {
                val response = assistant.answerQuestion(input, currentUserId)
                println(response.toFormattedString())
            }
        }
    }
    
    // Закрываем ресурсы
    openRouterClient.close()
    embeddingClient.close()
}
