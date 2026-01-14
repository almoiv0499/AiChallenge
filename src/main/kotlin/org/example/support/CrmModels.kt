package org.example.support

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * Модели данных CRM для сервиса поддержки.
 */

/**
 * Пользователь системы.
 */
@Serializable
data class User(
    val id: String,
    val email: String,
    val name: String,
    @SerialName("plan") val subscriptionPlan: String = "free", // free, pro, enterprise
    @SerialName("created_at") val createdAt: Long = System.currentTimeMillis(),
    @SerialName("last_login") val lastLogin: Long? = null,
    val tags: List<String> = emptyList(), // VIP, new_user, frequent_issues
    val metadata: Map<String, String> = emptyMap()
)

/**
 * Статус тикета.
 */
@Serializable
enum class TicketStatus {
    @SerialName("open") OPEN,
    @SerialName("in_progress") IN_PROGRESS,
    @SerialName("waiting_for_customer") WAITING_FOR_CUSTOMER,
    @SerialName("resolved") RESOLVED,
    @SerialName("closed") CLOSED
}

/**
 * Приоритет тикета.
 */
@Serializable
enum class TicketPriority {
    @SerialName("low") LOW,
    @SerialName("medium") MEDIUM,
    @SerialName("high") HIGH,
    @SerialName("critical") CRITICAL
}

/**
 * Категория тикета.
 */
@Serializable
enum class TicketCategory {
    @SerialName("authorization") AUTHORIZATION,       // Проблемы с авторизацией
    @SerialName("payment") PAYMENT,                   // Проблемы с оплатой
    @SerialName("technical") TECHNICAL,               // Технические проблемы
    @SerialName("feature_request") FEATURE_REQUEST,   // Запрос функции
    @SerialName("bug_report") BUG_REPORT,            // Отчет об ошибке
    @SerialName("general") GENERAL                    // Общий вопрос
}

/**
 * Тикет службы поддержки.
 */
@Serializable
data class Ticket(
    val id: String,
    @SerialName("user_id") val userId: String,
    val subject: String,
    val description: String,
    val status: TicketStatus = TicketStatus.OPEN,
    val priority: TicketPriority = TicketPriority.MEDIUM,
    val category: TicketCategory = TicketCategory.GENERAL,
    @SerialName("created_at") val createdAt: Long = System.currentTimeMillis(),
    @SerialName("updated_at") val updatedAt: Long = System.currentTimeMillis(),
    @SerialName("resolved_at") val resolvedAt: Long? = null,
    val messages: List<TicketMessage> = emptyList(),
    val tags: List<String> = emptyList(), // urgent, duplicate, escalated
    val metadata: Map<String, String> = emptyMap() // error_code, browser, os, etc.
)

/**
 * Сообщение в тикете.
 */
@Serializable
data class TicketMessage(
    val id: String,
    @SerialName("ticket_id") val ticketId: String,
    @SerialName("sender_type") val senderType: MessageSenderType,
    @SerialName("sender_id") val senderId: String,
    val content: String,
    @SerialName("created_at") val createdAt: Long = System.currentTimeMillis(),
    val attachments: List<String> = emptyList()
)

/**
 * Тип отправителя сообщения.
 */
@Serializable
enum class MessageSenderType {
    @SerialName("user") USER,
    @SerialName("support") SUPPORT,
    @SerialName("bot") BOT
}

/**
 * Контекст пользователя для ассистента.
 * Содержит полную информацию о пользователе и его тикетах.
 */
data class UserContext(
    val user: User,
    val activeTickets: List<Ticket>,
    val recentTickets: List<Ticket>, // Последние закрытые тикеты
    val totalTicketCount: Int
) {
    /**
     * Формирует текстовое описание контекста для ассистента.
     */
    fun toContextString(): String = buildString {
        appendLine("=== КОНТЕКСТ ПОЛЬЗОВАТЕЛЯ ===")
        appendLine("Имя: ${user.name}")
        appendLine("Email: ${user.email}")
        appendLine("Тарифный план: ${user.subscriptionPlan}")
        if (user.tags.isNotEmpty()) {
            appendLine("Теги: ${user.tags.joinToString(", ")}")
        }
        
        if (activeTickets.isNotEmpty()) {
            appendLine("\n📋 АКТИВНЫЕ ТИКЕТЫ:")
            activeTickets.forEach { ticket ->
                appendLine("  • [${ticket.id}] ${ticket.subject}")
                appendLine("    Статус: ${ticket.status}, Приоритет: ${ticket.priority}")
                appendLine("    Категория: ${ticket.category}")
                appendLine("    Описание: ${ticket.description}")
                if (ticket.metadata.isNotEmpty()) {
                    appendLine("    Метаданные: ${ticket.metadata}")
                }
                if (ticket.messages.isNotEmpty()) {
                    appendLine("    Последнее сообщение: ${ticket.messages.last().content.take(100)}...")
                }
            }
        }
        
        if (recentTickets.isNotEmpty()) {
            appendLine("\n📁 НЕДАВНИЕ ЗАКРЫТЫЕ ТИКЕТЫ:")
            recentTickets.take(3).forEach { ticket ->
                appendLine("  • [${ticket.id}] ${ticket.subject} - ${ticket.status}")
            }
        }
        
        appendLine("\nВсего тикетов пользователя: $totalTicketCount")
    }
}

/**
 * Результат поиска в CRM.
 */
data class CrmSearchResult(
    val users: List<User> = emptyList(),
    val tickets: List<Ticket> = emptyList(),
    val totalFound: Int = 0
)
