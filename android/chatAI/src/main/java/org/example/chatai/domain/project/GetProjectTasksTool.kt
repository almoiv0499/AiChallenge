package org.example.chatai.domain.project

import android.util.Log
import kotlinx.coroutines.runBlocking
import org.example.chatai.domain.api.OpenRouterPropertyDefinition
import org.example.chatai.domain.api.OpenRouterToolDefinition
import org.example.chatai.domain.api.OpenRouterToolParameters
import org.example.chatai.domain.tools.AgentTool

private const val TAG = "GetProjectTasksTool"

/**
 * Инструмент для получения задач проекта (Team MCP).
 */
class GetProjectTasksTool(
    private val client: ProjectTaskClient
) : AgentTool {
    override val name = "get_project_tasks"
    override val description = "Получить список задач проекта с возможностью фильтрации. Используй для просмотра задач, поиска по статусу, приоритету, исполнителю и другим параметрам."
    
    override fun getDefinition() = OpenRouterToolDefinition(
        name = name,
        description = description,
        type = "function",
        parameters = OpenRouterToolParameters(
            properties = mapOf(
                "status" to OpenRouterPropertyDefinition(
                    type = "string",
                    description = "Фильтр по статусу (TODO, IN_PROGRESS, IN_REVIEW, BLOCKED, DONE, CANCELLED). Можно указать несколько через запятую."
                ),
                "priority" to OpenRouterPropertyDefinition(
                    type = "string",
                    description = "Фильтр по приоритету (LOW, MEDIUM, HIGH, CRITICAL). Можно указать несколько через запятую."
                ),
                "assignee" to OpenRouterPropertyDefinition(
                    type = "string",
                    description = "Фильтр по email исполнителя"
                ),
                "tags" to OpenRouterPropertyDefinition(
                    type = "string",
                    description = "Фильтр по тегам через запятую"
                ),
                "overdue" to OpenRouterPropertyDefinition(
                    type = "boolean",
                    description = "Показать только просроченные задачи (true/false)"
                ),
                "search" to OpenRouterPropertyDefinition(
                    type = "string",
                    description = "Поиск по названию и описанию задачи"
                ),
                "page" to OpenRouterPropertyDefinition(
                    type = "integer",
                    description = "Номер страницы (по умолчанию 1)"
                ),
                "pageSize" to OpenRouterPropertyDefinition(
                    type = "integer",
                    description = "Размер страницы (по умолчанию 50)"
                )
            )
        )
    )
    
    override fun execute(arguments: Map<String, String>): String {
        return try {
            val statusStr = arguments["status"]
            val status = statusStr?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
            val priorityStr = arguments["priority"]
            val priority = priorityStr?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
            val assignee = arguments["assignee"]
            val tagsStr = arguments["tags"]
            val tags = tagsStr?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
            val overdue = arguments["overdue"]?.toBoolean()
            val search = arguments["search"]
            val page = arguments["page"]?.toIntOrNull() ?: 1
            val pageSize = arguments["pageSize"]?.toIntOrNull() ?: 50
            
            val response = runBlocking {
                client.getTasks(
                    status = status,
                    priority = priority,
                    assignee = assignee,
                    tags = tags,
                    overdue = overdue,
                    search = search,
                    page = page,
                    pageSize = pageSize
                )
            }
            
            if (response.tasks.isEmpty()) {
                return "📋 Задач не найдено"
            }
            
            buildString {
                appendLine("📋 Найдено задач: ${response.total} (страница ${response.page}/${(response.total + response.pageSize - 1) / response.pageSize})")
                appendLine()
                response.tasks.forEachIndexed { index, task ->
                    appendLine("${index + 1}. ${task.title}")
                    appendLine("   ID: ${task.id}")
                    appendLine("   Статус: ${task.status} | Приоритет: ${task.priority}")
                    task.assignee?.let { appendLine("   Исполнитель: $it") }
                    task.dueDate?.let {
                        val overdue = if (task.isOverdue()) " ⚠️ ПРОСРОЧЕНО" else ""
                        appendLine("   Дедлайн: $it$overdue")
                    }
                    if (task.tags.isNotEmpty()) {
                        appendLine("   Теги: ${task.tags.joinToString(", ")}")
                    }
                    task.description?.take(100)?.let { appendLine("   Описание: $it${if (it.length == 100) "..." else ""}") }
                    appendLine()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при получении задач", e)
            val errorMessage = when {
                e.message?.contains("timeout", ignoreCase = true) == true || 
                e.message?.contains("Connect timeout", ignoreCase = true) == true -> {
                    "⚠️ Не удалось подключиться к Project Task API серверу. " +
                    "Убедитесь, что сервер запущен на порту 8084 (запустите терминальную версию приложения). " +
                    "Для эмулятора используется адрес 10.0.2.2:8084, для реального устройства - IP адрес компьютера."
                }
                e.message?.contains("Connection refused", ignoreCase = true) == true -> {
                    "⚠️ Project Task API сервер не запущен. Запустите терминальную версию приложения для запуска сервера на порту 8084."
                }
                else -> "❌ Ошибка при получении задач: ${e.message}"
            }
            errorMessage
        }
    }
}
