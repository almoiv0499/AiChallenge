package org.example.support

import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.io.File

/**
 * JSON-based хранилище CRM данных.
 * Позволяет работать с пользователями и тикетами.
 */
class CrmStorage(
    private val dataFile: String = "crm_data.json",
    initialData: CrmData? = null
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }
    
    private var data: CrmData
    
    init {
        data = loadData() ?: initialData ?: createSampleData()
        saveData()
    }
    
    // ==================== ПОЛЬЗОВАТЕЛИ ====================
    
    /**
     * Получить пользователя по ID.
     */
    fun getUser(userId: String): User? = data.users.find { it.id == userId }
    
    /**
     * Получить пользователя по email.
     */
    fun getUserByEmail(email: String): User? = data.users.find { it.email.equals(email, ignoreCase = true) }
    
    /**
     * Получить всех пользователей.
     */
    fun getAllUsers(): List<User> = data.users.toList()
    
    /**
     * Поиск пользователей по имени или email.
     */
    fun searchUsers(query: String): List<User> {
        val lowerQuery = query.lowercase()
        return data.users.filter { user ->
            user.name.lowercase().contains(lowerQuery) ||
            user.email.lowercase().contains(lowerQuery)
        }
    }
    
    /**
     * Добавить нового пользователя.
     */
    fun addUser(user: User): Boolean {
        if (data.users.any { it.id == user.id || it.email == user.email }) {
            return false
        }
        data = data.copy(users = data.users + user)
        saveData()
        return true
    }
    
    /**
     * Обновить пользователя.
     */
    fun updateUser(user: User): Boolean {
        val index = data.users.indexOfFirst { it.id == user.id }
        if (index == -1) return false
        data = data.copy(users = data.users.toMutableList().apply { set(index, user) })
        saveData()
        return true
    }
    
    // ==================== ТИКЕТЫ ====================
    
    /**
     * Получить тикет по ID.
     */
    fun getTicket(ticketId: String): Ticket? = data.tickets.find { it.id == ticketId }
    
    /**
     * Получить все тикеты.
     */
    fun getAllTickets(): List<Ticket> = data.tickets.toList()
    
    /**
     * Получить тикеты пользователя.
     */
    fun getUserTickets(userId: String): List<Ticket> = 
        data.tickets.filter { it.userId == userId }.sortedByDescending { it.createdAt }
    
    /**
     * Получить активные тикеты пользователя.
     */
    fun getActiveTickets(userId: String): List<Ticket> =
        getUserTickets(userId).filter { 
            it.status in listOf(TicketStatus.OPEN, TicketStatus.IN_PROGRESS, TicketStatus.WAITING_FOR_CUSTOMER)
        }
    
    /**
     * Получить закрытые тикеты пользователя.
     */
    fun getResolvedTickets(userId: String): List<Ticket> =
        getUserTickets(userId).filter { 
            it.status in listOf(TicketStatus.RESOLVED, TicketStatus.CLOSED)
        }
    
    /**
     * Поиск тикетов по теме или описанию.
     */
    fun searchTickets(query: String): List<Ticket> {
        val lowerQuery = query.lowercase()
        return data.tickets.filter { ticket ->
            ticket.subject.lowercase().contains(lowerQuery) ||
            ticket.description.lowercase().contains(lowerQuery) ||
            ticket.messages.any { it.content.lowercase().contains(lowerQuery) }
        }
    }
    
    /**
     * Поиск тикетов по категории.
     */
    fun getTicketsByCategory(category: TicketCategory): List<Ticket> =
        data.tickets.filter { it.category == category }
    
    /**
     * Поиск тикетов по статусу.
     */
    fun getTicketsByStatus(status: TicketStatus): List<Ticket> =
        data.tickets.filter { it.status == status }
    
    /**
     * Создать новый тикет.
     */
    fun createTicket(ticket: Ticket): Boolean {
        if (data.tickets.any { it.id == ticket.id }) {
            return false
        }
        data = data.copy(tickets = data.tickets + ticket)
        saveData()
        return true
    }
    
    /**
     * Обновить тикет.
     */
    fun updateTicket(ticket: Ticket): Boolean {
        val index = data.tickets.indexOfFirst { it.id == ticket.id }
        if (index == -1) return false
        data = data.copy(tickets = data.tickets.toMutableList().apply { set(index, ticket) })
        saveData()
        return true
    }
    
    /**
     * Добавить сообщение к тикету.
     */
    fun addMessageToTicket(ticketId: String, message: TicketMessage): Boolean {
        val ticket = getTicket(ticketId) ?: return false
        val updatedTicket = ticket.copy(
            messages = ticket.messages + message,
            updatedAt = System.currentTimeMillis()
        )
        return updateTicket(updatedTicket)
    }
    
    /**
     * Изменить статус тикета.
     */
    fun updateTicketStatus(ticketId: String, status: TicketStatus): Boolean {
        val ticket = getTicket(ticketId) ?: return false
        val updatedTicket = ticket.copy(
            status = status,
            updatedAt = System.currentTimeMillis(),
            resolvedAt = if (status in listOf(TicketStatus.RESOLVED, TicketStatus.CLOSED)) 
                System.currentTimeMillis() else ticket.resolvedAt
        )
        return updateTicket(updatedTicket)
    }
    
    // ==================== КОНТЕКСТ ====================
    
    /**
     * Получить полный контекст пользователя для ассистента.
     */
    fun getUserContext(userId: String): UserContext? {
        val user = getUser(userId) ?: return null
        val allTickets = getUserTickets(userId)
        return UserContext(
            user = user,
            activeTickets = allTickets.filter { 
                it.status !in listOf(TicketStatus.RESOLVED, TicketStatus.CLOSED)
            },
            recentTickets = allTickets.filter { 
                it.status in listOf(TicketStatus.RESOLVED, TicketStatus.CLOSED)
            }.take(5),
            totalTicketCount = allTickets.size
        )
    }
    
    /**
     * Получить контекст по email пользователя.
     */
    fun getUserContextByEmail(email: String): UserContext? {
        val user = getUserByEmail(email) ?: return null
        return getUserContext(user.id)
    }
    
    // ==================== СТАТИСТИКА ====================
    
    /**
     * Получить статистику по тикетам.
     */
    fun getTicketStats(): TicketStats {
        val tickets = data.tickets
        return TicketStats(
            total = tickets.size,
            open = tickets.count { it.status == TicketStatus.OPEN },
            inProgress = tickets.count { it.status == TicketStatus.IN_PROGRESS },
            waitingForCustomer = tickets.count { it.status == TicketStatus.WAITING_FOR_CUSTOMER },
            resolved = tickets.count { it.status == TicketStatus.RESOLVED },
            closed = tickets.count { it.status == TicketStatus.CLOSED },
            byCategory = TicketCategory.entries.associateWith { cat ->
                tickets.count { it.category == cat }
            },
            byPriority = TicketPriority.entries.associateWith { priority ->
                tickets.count { it.priority == priority }
            }
        )
    }
    
    // ==================== PERSISTENCE ====================
    
    private fun loadData(): CrmData? {
        val file = File(dataFile)
        if (!file.exists()) return null
        return try {
            json.decodeFromString<CrmData>(file.readText())
        } catch (e: Exception) {
            println("⚠️ Ошибка загрузки CRM данных: ${e.message}")
            null
        }
    }
    
    private fun saveData() {
        try {
            File(dataFile).writeText(json.encodeToString(data))
        } catch (e: Exception) {
            println("⚠️ Ошибка сохранения CRM данных: ${e.message}")
        }
    }
    
    /**
     * Создать демонстрационные данные.
     */
    private fun createSampleData(): CrmData {
        val now = System.currentTimeMillis()
        val dayMs = 24 * 60 * 60 * 1000L
        
        val users = listOf(
            User(
                id = "user_001",
                email = "ivan@example.com",
                name = "Иван Петров",
                subscriptionPlan = "pro",
                createdAt = now - 30 * dayMs,
                lastLogin = now - 1 * dayMs,
                tags = listOf("VIP", "frequent_issues")
            ),
            User(
                id = "user_002",
                email = "anna@example.com",
                name = "Анна Сидорова",
                subscriptionPlan = "enterprise",
                createdAt = now - 60 * dayMs,
                lastLogin = now - 2 * dayMs,
                tags = listOf("VIP")
            ),
            User(
                id = "user_003",
                email = "test@example.com",
                name = "Тестовый Пользователь",
                subscriptionPlan = "free",
                createdAt = now - 7 * dayMs,
                lastLogin = now,
                tags = listOf("new_user")
            ),
            User(
                id = "user_004",
                email = "dmitry@example.com",
                name = "Дмитрий Козлов",
                subscriptionPlan = "pro",
                createdAt = now - 90 * dayMs,
                lastLogin = now - 5 * dayMs
            )
        )
        
        val tickets = listOf(
            // Тикет с проблемой авторизации - АКТИВНЫЙ
            Ticket(
                id = "TKT-001",
                userId = "user_001",
                subject = "Не работает авторизация через Google",
                description = "При попытке войти через Google появляется ошибка 'Invalid OAuth token'. Пробовал разные браузеры - результат тот же.",
                status = TicketStatus.OPEN,
                priority = TicketPriority.HIGH,
                category = TicketCategory.AUTHORIZATION,
                createdAt = now - 2 * dayMs,
                updatedAt = now - 1 * dayMs,
                messages = listOf(
                    TicketMessage(
                        id = "msg_001_1",
                        ticketId = "TKT-001",
                        senderType = MessageSenderType.USER,
                        senderId = "user_001",
                        content = "Пытаюсь войти через Google OAuth, получаю ошибку Invalid OAuth token. Использую Chrome последней версии."
                    ),
                    TicketMessage(
                        id = "msg_001_2",
                        ticketId = "TKT-001",
                        senderType = MessageSenderType.SUPPORT,
                        senderId = "support_agent_1",
                        content = "Попробуйте очистить cookies и кэш браузера, затем повторите попытку."
                    ),
                    TicketMessage(
                        id = "msg_001_3",
                        ticketId = "TKT-001",
                        senderType = MessageSenderType.USER,
                        senderId = "user_001",
                        content = "Очистил cookies, не помогло. Та же ошибка."
                    )
                ),
                metadata = mapOf(
                    "browser" to "Chrome 120",
                    "os" to "Windows 11",
                    "error_code" to "OAUTH_INVALID_TOKEN"
                ),
                tags = listOf("oauth", "google")
            ),
            
            // Тикет с проблемой оплаты - В РАБОТЕ
            Ticket(
                id = "TKT-002",
                userId = "user_002",
                subject = "Не прошла оплата подписки",
                description = "Пытаюсь продлить Enterprise подписку, но оплата не проходит. Карта точно рабочая.",
                status = TicketStatus.IN_PROGRESS,
                priority = TicketPriority.CRITICAL,
                category = TicketCategory.PAYMENT,
                createdAt = now - 1 * dayMs,
                updatedAt = now,
                messages = listOf(
                    TicketMessage(
                        id = "msg_002_1",
                        ticketId = "TKT-002",
                        senderType = MessageSenderType.USER,
                        senderId = "user_002",
                        content = "Оплата не проходит, показывает 'Payment declined'. Карта Visa, работает везде."
                    )
                ),
                metadata = mapOf(
                    "payment_method" to "Visa",
                    "error_code" to "PAYMENT_DECLINED",
                    "amount" to "9999 RUB"
                ),
                tags = listOf("urgent", "enterprise")
            ),
            
            // Технический тикет - ОЖИДАЕТ ОТВЕТА КЛИЕНТА
            Ticket(
                id = "TKT-003",
                userId = "user_003",
                subject = "Приложение вылетает при открытии",
                description = "После обновления приложение крашится сразу после запуска на Android.",
                status = TicketStatus.WAITING_FOR_CUSTOMER,
                priority = TicketPriority.MEDIUM,
                category = TicketCategory.TECHNICAL,
                createdAt = now - 3 * dayMs,
                updatedAt = now - 1 * dayMs,
                messages = listOf(
                    TicketMessage(
                        id = "msg_003_1",
                        ticketId = "TKT-003",
                        senderType = MessageSenderType.USER,
                        senderId = "user_003",
                        content = "Приложение крашится на Samsung Galaxy S21, Android 14."
                    ),
                    TicketMessage(
                        id = "msg_003_2",
                        ticketId = "TKT-003",
                        senderType = MessageSenderType.SUPPORT,
                        senderId = "support_agent_2",
                        content = "Пожалуйста, пришлите логи краша из Settings -> About -> Send crash logs."
                    )
                ),
                metadata = mapOf(
                    "device" to "Samsung Galaxy S21",
                    "os_version" to "Android 14",
                    "app_version" to "2.5.0"
                )
            ),
            
            // Закрытый тикет с авторизацией
            Ticket(
                id = "TKT-004",
                userId = "user_001",
                subject = "Забыл пароль, не приходит письмо для сброса",
                description = "Запрашиваю сброс пароля, но письмо не приходит уже 2 часа.",
                status = TicketStatus.RESOLVED,
                priority = TicketPriority.MEDIUM,
                category = TicketCategory.AUTHORIZATION,
                createdAt = now - 10 * dayMs,
                updatedAt = now - 9 * dayMs,
                resolvedAt = now - 9 * dayMs,
                messages = listOf(
                    TicketMessage(
                        id = "msg_004_1",
                        ticketId = "TKT-004",
                        senderType = MessageSenderType.USER,
                        senderId = "user_001",
                        content = "Письмо для сброса пароля не приходит."
                    ),
                    TicketMessage(
                        id = "msg_004_2",
                        ticketId = "TKT-004",
                        senderType = MessageSenderType.SUPPORT,
                        senderId = "support_agent_1",
                        content = "Проверьте папку Спам. Если нет письма, сообщите точный email."
                    ),
                    TicketMessage(
                        id = "msg_004_3",
                        ticketId = "TKT-004",
                        senderType = MessageSenderType.USER,
                        senderId = "user_001",
                        content = "Нашел в спаме, спасибо!"
                    )
                )
            ),
            
            // Запрос функции
            Ticket(
                id = "TKT-005",
                userId = "user_004",
                subject = "Добавьте двухфакторную аутентификацию",
                description = "Хотелось бы иметь возможность включить 2FA для дополнительной безопасности.",
                status = TicketStatus.OPEN,
                priority = TicketPriority.LOW,
                category = TicketCategory.FEATURE_REQUEST,
                createdAt = now - 5 * dayMs,
                updatedAt = now - 5 * dayMs,
                messages = listOf(
                    TicketMessage(
                        id = "msg_005_1",
                        ticketId = "TKT-005",
                        senderType = MessageSenderType.USER,
                        senderId = "user_004",
                        content = "Было бы здорово иметь 2FA через Google Authenticator или SMS."
                    )
                ),
                tags = listOf("feature", "security")
            ),
            
            // Баг-репорт
            Ticket(
                id = "TKT-006",
                userId = "user_002",
                subject = "Ошибка синхронизации данных между устройствами",
                description = "Данные, введенные на телефоне, не появляются на десктопе и наоборот.",
                status = TicketStatus.IN_PROGRESS,
                priority = TicketPriority.HIGH,
                category = TicketCategory.BUG_REPORT,
                createdAt = now - 4 * dayMs,
                updatedAt = now,
                messages = listOf(
                    TicketMessage(
                        id = "msg_006_1",
                        ticketId = "TKT-006",
                        senderType = MessageSenderType.USER,
                        senderId = "user_002",
                        content = "Синхронизация не работает уже 3 дня. Пробовала выходить и заходить обратно."
                    ),
                    TicketMessage(
                        id = "msg_006_2",
                        ticketId = "TKT-006",
                        senderType = MessageSenderType.SUPPORT,
                        senderId = "support_agent_1",
                        content = "Мы зафиксировали проблему, команда разработки работает над исправлением."
                    )
                ),
                metadata = mapOf(
                    "devices" to "iPhone 15, MacBook Pro",
                    "app_version_mobile" to "2.5.0",
                    "app_version_desktop" to "2.4.8"
                ),
                tags = listOf("sync", "critical_bug")
            )
        )
        
        return CrmData(users = users, tickets = tickets)
    }
}

/**
 * Данные CRM.
 */
@kotlinx.serialization.Serializable
data class CrmData(
    val users: List<User> = emptyList(),
    val tickets: List<Ticket> = emptyList()
)

/**
 * Статистика по тикетам.
 */
data class TicketStats(
    val total: Int,
    val open: Int,
    val inProgress: Int,
    val waitingForCustomer: Int,
    val resolved: Int,
    val closed: Int,
    val byCategory: Map<TicketCategory, Int>,
    val byPriority: Map<TicketPriority, Int>
) {
    fun toFormattedString(): String = buildString {
        appendLine("📊 СТАТИСТИКА ТИКЕТОВ")
        appendLine("Всего: $total")
        appendLine("• Открытые: $open")
        appendLine("• В работе: $inProgress")
        appendLine("• Ожидают ответа клиента: $waitingForCustomer")
        appendLine("• Решенные: $resolved")
        appendLine("• Закрытые: $closed")
        appendLine("\nПо категориям:")
        byCategory.forEach { (cat, count) ->
            if (count > 0) appendLine("  • $cat: $count")
        }
        appendLine("\nПо приоритету:")
        byPriority.forEach { (priority, count) ->
            if (count > 0) appendLine("  • $priority: $count")
        }
    }
}
