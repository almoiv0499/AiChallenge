package org.example.support

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.example.mcp.JsonRpcError
import org.example.mcp.JsonRpcRequest
import org.example.mcp.JsonRpcResponse
import org.example.mcp.McpTool
import org.example.mcp.ToolsListResult

/**
 * MCP сервер для работы с CRM данными.
 * Предоставляет инструменты для получения информации о пользователях и тикетах.
 */
class CrmMcpServer(
    private val crmStorage: CrmStorage
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    fun configureMcpServer(application: Application) {
        application.install(ContentNegotiation) {
            json(this@CrmMcpServer.json)
        }
        application.install(CORS) {
            anyHost()
            allowHeader("Content-Type")
        }
        application.routing {
            post("/crm-mcp") {
                try {
                    val request = call.receive<JsonRpcRequest>()
                    val response = handleRequest(request)
                    call.respond(response)
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        JsonRpcResponse(
                            id = null,
                            error = JsonRpcError(
                                code = -32700,
                                message = "Parse error: ${e.message}"
                            )
                        )
                    )
                }
            }
        }
    }

    suspend fun handleRequest(request: JsonRpcRequest): JsonRpcResponse {
        return withContext(Dispatchers.Default) {
            when (request.method) {
                "initialize" -> handleInitialize(request)
                "notifications/initialized" -> JsonRpcResponse(id = request.id, result = null)
                "tools/list" -> handleToolsList(request)
                "tools/call" -> handleToolCall(request)
                else -> JsonRpcResponse(
                    id = request.id,
                    error = JsonRpcError(
                        code = -32601,
                        message = "Method not found: ${request.method}"
                    )
                )
            }
        }
    }

    private fun handleInitialize(request: JsonRpcRequest): JsonRpcResponse {
        val result = buildJsonObject {
            put("protocolVersion", "2025-06-18")
            put("capabilities", buildJsonObject {
                put("tools", buildJsonObject {
                    put("listChanged", true)
                })
            })
            put("serverInfo", buildJsonObject {
                put("name", "CrmMcpServer")
                put("version", "1.0.0")
            })
        }
        return JsonRpcResponse(id = request.id, result = result)
    }

    private fun handleToolsList(request: JsonRpcRequest): JsonRpcResponse {
        val tools = listOf(
            createGetUserContextTool(),
            createGetTicketTool(),
            createSearchTicketsTool(),
            createGetTicketStatsTool(),
            createGetUserTicketsTool(),
            createUpdateTicketStatusTool(),
            createAddTicketMessageTool()
        )
        val toolsListResult = ToolsListResult(tools = tools)
        val result = json.encodeToJsonElement(ToolsListResult.serializer(), toolsListResult)
        return JsonRpcResponse(id = request.id, result = result)
    }

    private suspend fun handleToolCall(request: JsonRpcRequest): JsonRpcResponse {
        val paramsElement = request.params
        val params = when {
            paramsElement == null -> {
                return JsonRpcResponse(
                    id = request.id,
                    error = JsonRpcError(code = -32602, message = "Invalid params: params is null")
                )
            }
            paramsElement is JsonNull -> {
                return JsonRpcResponse(
                    id = request.id,
                    error = JsonRpcError(code = -32602, message = "Invalid params: params is JsonNull")
                )
            }
            paramsElement is JsonObject -> paramsElement
            else -> {
                return JsonRpcResponse(
                    id = request.id,
                    error = JsonRpcError(code = -32602, message = "Invalid params type")
                )
            }
        }

        val toolNameElement = params["name"]
        val toolName = when {
            toolNameElement == null || toolNameElement is JsonNull -> {
                return JsonRpcResponse(
                    id = request.id,
                    error = JsonRpcError(code = -32602, message = "Tool name is required")
                )
            }
            else -> toolNameElement.jsonPrimitive.content
        }

        val argumentsElement = params["arguments"]
        val arguments = when {
            argumentsElement == null || argumentsElement is JsonNull -> buildJsonObject {}
            argumentsElement is JsonObject -> argumentsElement
            else -> buildJsonObject {}
        }

        val result = when (toolName) {
            "crm_get_user_context" -> executeGetUserContext(arguments)
            "crm_get_ticket" -> executeGetTicket(arguments)
            "crm_search_tickets" -> executeSearchTickets(arguments)
            "crm_get_ticket_stats" -> executeGetTicketStats(arguments)
            "crm_get_user_tickets" -> executeGetUserTickets(arguments)
            "crm_update_ticket_status" -> executeUpdateTicketStatus(arguments)
            "crm_add_ticket_message" -> executeAddTicketMessage(arguments)
            else -> buildErrorResult("Unknown tool: $toolName")
        }
        return JsonRpcResponse(id = request.id, result = result)
    }

    // ==================== TOOL DEFINITIONS ====================

    private fun createGetUserContextTool(): McpTool {
        return McpTool(
            name = "crm_get_user_context",
            description = "Получить полный контекст пользователя: информацию о пользователе, активные и недавние тикеты. Используйте для понимания истории обращений клиента.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("user_id", buildJsonObject {
                        put("type", "string")
                        put("description", "ID пользователя (например: user_001)")
                    })
                    put("email", buildJsonObject {
                        put("type", "string")
                        put("description", "Email пользователя (альтернатива user_id)")
                    })
                })
            }
        )
    }

    private fun createGetTicketTool(): McpTool {
        return McpTool(
            name = "crm_get_ticket",
            description = "Получить детальную информацию о тикете по его ID, включая всю историю переписки.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("ticket_id", buildJsonObject {
                        put("type", "string")
                        put("description", "ID тикета (например: TKT-001)")
                    })
                })
                put("required", buildJsonArray { add("ticket_id") })
            }
        )
    }

    private fun createSearchTicketsTool(): McpTool {
        return McpTool(
            name = "crm_search_tickets",
            description = "Поиск тикетов по ключевым словам, категории или статусу.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("query", buildJsonObject {
                        put("type", "string")
                        put("description", "Поисковый запрос (по теме и описанию)")
                    })
                    put("category", buildJsonObject {
                        put("type", "string")
                        put("enum", buildJsonArray {
                            add("authorization")
                            add("payment")
                            add("technical")
                            add("feature_request")
                            add("bug_report")
                            add("general")
                        })
                        put("description", "Категория тикета")
                    })
                    put("status", buildJsonObject {
                        put("type", "string")
                        put("enum", buildJsonArray {
                            add("open")
                            add("in_progress")
                            add("waiting_for_customer")
                            add("resolved")
                            add("closed")
                        })
                        put("description", "Статус тикета")
                    })
                })
            }
        )
    }

    private fun createGetTicketStatsTool(): McpTool {
        return McpTool(
            name = "crm_get_ticket_stats",
            description = "Получить общую статистику по тикетам: количество по статусам, категориям и приоритетам.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {})
            }
        )
    }

    private fun createGetUserTicketsTool(): McpTool {
        return McpTool(
            name = "crm_get_user_tickets",
            description = "Получить все тикеты конкретного пользователя.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("user_id", buildJsonObject {
                        put("type", "string")
                        put("description", "ID пользователя")
                    })
                    put("status_filter", buildJsonObject {
                        put("type", "string")
                        put("enum", buildJsonArray {
                            add("all")
                            add("active")
                            add("resolved")
                        })
                        put("description", "Фильтр по статусу: all, active (открытые/в работе), resolved (решенные/закрытые)")
                    })
                })
                put("required", buildJsonArray { add("user_id") })
            }
        )
    }

    private fun createUpdateTicketStatusTool(): McpTool {
        return McpTool(
            name = "crm_update_ticket_status",
            description = "Обновить статус тикета.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("ticket_id", buildJsonObject {
                        put("type", "string")
                        put("description", "ID тикета")
                    })
                    put("new_status", buildJsonObject {
                        put("type", "string")
                        put("enum", buildJsonArray {
                            add("open")
                            add("in_progress")
                            add("waiting_for_customer")
                            add("resolved")
                            add("closed")
                        })
                        put("description", "Новый статус тикета")
                    })
                })
                put("required", buildJsonArray { 
                    add("ticket_id")
                    add("new_status")
                })
            }
        )
    }

    private fun createAddTicketMessageTool(): McpTool {
        return McpTool(
            name = "crm_add_ticket_message",
            description = "Добавить сообщение от бота/поддержки к тикету.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("ticket_id", buildJsonObject {
                        put("type", "string")
                        put("description", "ID тикета")
                    })
                    put("message", buildJsonObject {
                        put("type", "string")
                        put("description", "Текст сообщения")
                    })
                })
                put("required", buildJsonArray { 
                    add("ticket_id")
                    add("message")
                })
            }
        )
    }

    // ==================== TOOL IMPLEMENTATIONS ====================

    private fun executeGetUserContext(arguments: JsonObject): JsonElement {
        val userId = arguments["user_id"]?.jsonPrimitive?.content
        val email = arguments["email"]?.jsonPrimitive?.content

        val context = when {
            userId != null -> crmStorage.getUserContext(userId)
            email != null -> crmStorage.getUserContextByEmail(email)
            else -> return buildErrorResult("Укажите user_id или email")
        }

        return if (context != null) {
            buildSuccessResult(context.toContextString())
        } else {
            buildErrorResult("Пользователь не найден")
        }
    }

    private fun executeGetTicket(arguments: JsonObject): JsonElement {
        val ticketId = arguments["ticket_id"]?.jsonPrimitive?.content
            ?: return buildErrorResult("ticket_id обязателен")

        val ticket = crmStorage.getTicket(ticketId)
            ?: return buildErrorResult("Тикет не найден: $ticketId")

        val user = crmStorage.getUser(ticket.userId)
        
        return buildSuccessResult(buildString {
            appendLine("📋 ТИКЕТ: ${ticket.id}")
            appendLine("Тема: ${ticket.subject}")
            appendLine("Статус: ${ticket.status}")
            appendLine("Приоритет: ${ticket.priority}")
            appendLine("Категория: ${ticket.category}")
            appendLine()
            appendLine("👤 Клиент: ${user?.name ?: "Неизвестен"} (${user?.email ?: ticket.userId})")
            appendLine("Тарифный план: ${user?.subscriptionPlan ?: "N/A"}")
            appendLine()
            appendLine("📝 Описание:")
            appendLine(ticket.description)
            
            if (ticket.metadata.isNotEmpty()) {
                appendLine()
                appendLine("🔧 Технические данные:")
                ticket.metadata.forEach { (key, value) ->
                    appendLine("  • $key: $value")
                }
            }
            
            if (ticket.messages.isNotEmpty()) {
                appendLine()
                appendLine("💬 История переписки:")
                ticket.messages.forEach { msg ->
                    val sender = when (msg.senderType) {
                        MessageSenderType.USER -> "👤 Клиент"
                        MessageSenderType.SUPPORT -> "🧑‍💼 Поддержка"
                        MessageSenderType.BOT -> "🤖 Бот"
                    }
                    appendLine("[$sender]: ${msg.content}")
                }
            }
            
            if (ticket.tags.isNotEmpty()) {
                appendLine()
                appendLine("🏷️ Теги: ${ticket.tags.joinToString(", ")}")
            }
        })
    }

    private fun executeSearchTickets(arguments: JsonObject): JsonElement {
        val query = arguments["query"]?.jsonPrimitive?.content
        val categoryStr = arguments["category"]?.jsonPrimitive?.content
        val statusStr = arguments["status"]?.jsonPrimitive?.content

        var tickets = crmStorage.getAllTickets()

        // Фильтр по запросу
        if (!query.isNullOrBlank()) {
            tickets = crmStorage.searchTickets(query)
        }

        // Фильтр по категории
        if (!categoryStr.isNullOrBlank()) {
            val category = try {
                TicketCategory.valueOf(categoryStr.uppercase())
            } catch (e: Exception) {
                return buildErrorResult("Неизвестная категория: $categoryStr")
            }
            tickets = tickets.filter { it.category == category }
        }

        // Фильтр по статусу
        if (!statusStr.isNullOrBlank()) {
            val status = try {
                TicketStatus.valueOf(statusStr.uppercase())
            } catch (e: Exception) {
                return buildErrorResult("Неизвестный статус: $statusStr")
            }
            tickets = tickets.filter { it.status == status }
        }

        return if (tickets.isNotEmpty()) {
            buildSuccessResult(buildString {
                appendLine("🔍 Найдено тикетов: ${tickets.size}")
                appendLine()
                tickets.forEach { ticket ->
                    appendLine("• [${ticket.id}] ${ticket.subject}")
                    appendLine("  Статус: ${ticket.status}, Приоритет: ${ticket.priority}")
                    appendLine("  Категория: ${ticket.category}")
                    appendLine()
                }
            })
        } else {
            buildSuccessResult("Тикеты не найдены по заданным критериям.")
        }
    }

    private fun executeGetTicketStats(arguments: JsonObject): JsonElement {
        val stats = crmStorage.getTicketStats()
        return buildSuccessResult(stats.toFormattedString())
    }

    private fun executeGetUserTickets(arguments: JsonObject): JsonElement {
        val userId = arguments["user_id"]?.jsonPrimitive?.content
            ?: return buildErrorResult("user_id обязателен")
        val statusFilter = arguments["status_filter"]?.jsonPrimitive?.content ?: "all"

        val tickets = when (statusFilter) {
            "active" -> crmStorage.getActiveTickets(userId)
            "resolved" -> crmStorage.getResolvedTickets(userId)
            else -> crmStorage.getUserTickets(userId)
        }

        return if (tickets.isNotEmpty()) {
            buildSuccessResult(buildString {
                appendLine("📋 Тикеты пользователя $userId ($statusFilter): ${tickets.size}")
                appendLine()
                tickets.forEach { ticket ->
                    appendLine("• [${ticket.id}] ${ticket.subject}")
                    appendLine("  Статус: ${ticket.status}, Категория: ${ticket.category}")
                    appendLine()
                }
            })
        } else {
            buildSuccessResult("У пользователя нет тикетов с фильтром: $statusFilter")
        }
    }

    private fun executeUpdateTicketStatus(arguments: JsonObject): JsonElement {
        val ticketId = arguments["ticket_id"]?.jsonPrimitive?.content
            ?: return buildErrorResult("ticket_id обязателен")
        val statusStr = arguments["new_status"]?.jsonPrimitive?.content
            ?: return buildErrorResult("new_status обязателен")

        val status = try {
            TicketStatus.valueOf(statusStr.uppercase())
        } catch (e: Exception) {
            return buildErrorResult("Неизвестный статус: $statusStr")
        }

        val success = crmStorage.updateTicketStatus(ticketId, status)
        return if (success) {
            buildSuccessResult("✅ Статус тикета $ticketId изменен на $status")
        } else {
            buildErrorResult("Не удалось обновить статус тикета $ticketId")
        }
    }

    private fun executeAddTicketMessage(arguments: JsonObject): JsonElement {
        val ticketId = arguments["ticket_id"]?.jsonPrimitive?.content
            ?: return buildErrorResult("ticket_id обязателен")
        val messageText = arguments["message"]?.jsonPrimitive?.content
            ?: return buildErrorResult("message обязателен")

        val message = TicketMessage(
            id = "msg_${System.currentTimeMillis()}",
            ticketId = ticketId,
            senderType = MessageSenderType.BOT,
            senderId = "support_bot",
            content = messageText
        )

        val success = crmStorage.addMessageToTicket(ticketId, message)
        return if (success) {
            buildSuccessResult("✅ Сообщение добавлено к тикету $ticketId")
        } else {
            buildErrorResult("Не удалось добавить сообщение к тикету $ticketId")
        }
    }

    // ==================== HELPERS ====================

    private fun buildSuccessResult(text: String): JsonElement {
        return buildJsonObject {
            put("isError", false)
            put("content", buildJsonArray {
                add(buildJsonObject {
                    put("type", "text")
                    put("text", text)
                })
            })
        }
    }

    private fun buildErrorResult(text: String): JsonElement {
        return buildJsonObject {
            put("isError", true)
            put("content", buildJsonArray {
                add(buildJsonObject {
                    put("type", "text")
                    put("text", text)
                })
            })
        }
    }
}
