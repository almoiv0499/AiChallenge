package org.example.chatai.domain.project

import android.util.Log
import kotlinx.coroutines.runBlocking
import org.example.chatai.domain.api.OpenRouterPropertyDefinition
import org.example.chatai.domain.api.OpenRouterToolDefinition
import org.example.chatai.domain.api.OpenRouterToolParameters
import org.example.chatai.domain.tools.AgentTool

private const val TAG = "CreateProjectTaskTool"

/**
 * Инструмент для создания задачи проекта (Team MCP).
 */
class CreateProjectTaskTool(
    private val client: ProjectTaskClient
) : AgentTool {
    override val name = "create_project_task"
    override val description = "Создать новую задачу проекта. Используй для добавления задач в систему управления проектом."
    
    override fun getDefinition() = OpenRouterToolDefinition(
        name = name,
        description = description,
        type = "function",
        parameters = OpenRouterToolParameters(
            properties = mapOf(
                "title" to OpenRouterPropertyDefinition(
                    type = "string",
                    description = "Название задачи (обязательно)"
                ),
                "description" to OpenRouterPropertyDefinition(
                    type = "string",
                    description = "Описание задачи"
                ),
                "priority" to OpenRouterPropertyDefinition(
                    type = "string",
                    description = "Приоритет задачи",
                    enum = listOf("LOW", "MEDIUM", "HIGH", "CRITICAL")
                ),
                "assignee" to OpenRouterPropertyDefinition(
                    type = "string",
                    description = "Email исполнителя задачи"
                ),
                "dueDate" to OpenRouterPropertyDefinition(
                    type = "string",
                    description = "Дедлайн задачи в формате YYYY-MM-DD (например, 2025-12-31)"
                ),
                "tags" to OpenRouterPropertyDefinition(
                    type = "string",
                    description = "Теги задачи через запятую (например, 'backend,urgent')"
                ),
                "estimatedHours" to OpenRouterPropertyDefinition(
                    type = "number",
                    description = "Оценка времени в часах"
                ),
                "milestone" to OpenRouterPropertyDefinition(
                    type = "string",
                    description = "Milestone задачи"
                ),
                "epic" to OpenRouterPropertyDefinition(
                    type = "string",
                    description = "Epic задачи"
                )
            ),
            required = listOf("title")
        )
    )
    
    override fun execute(arguments: Map<String, String>): String {
        return try {
            val title = arguments["title"] ?: return "Ошибка: не указано название задачи"
            val description = arguments["description"]
            val priority = arguments["priority"] ?: "MEDIUM"
            val assignee = arguments["assignee"]
            val dueDate = arguments["dueDate"]
            val tagsStr = arguments["tags"]
            val tags = if (tagsStr != null) tagsStr.split(",").map { it.trim() }.filter { it.isNotEmpty() } else emptyList()
            val estimatedHours = arguments["estimatedHours"]?.toDoubleOrNull()
            val milestone = arguments["milestone"]
            val epic = arguments["epic"]
            
            val request = CreateTaskRequest(
                title = title,
                description = description,
                priority = priority.uppercase(),
                assignee = assignee,
                dueDate = dueDate,
                tags = tags,
                estimatedHours = estimatedHours,
                milestone = milestone,
                epic = epic
            )
            
            val task = runBlocking { client.createTask(request) }
            
            buildString {
                appendLine("✅ Задача успешно создана!")
                appendLine("ID: ${task.id}")
                appendLine("Название: ${task.title}")
                appendLine("Статус: ${task.status} | Приоритет: ${task.priority}")
                task.assignee?.let { appendLine("Исполнитель: $it") }
                task.dueDate?.let { appendLine("Дедлайн: $it") }
                if (task.tags.isNotEmpty()) {
                    appendLine("Теги: ${task.tags.joinToString(", ")}")
                }
                task.estimatedHours?.let { appendLine("Оценка: $it часов") }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при создании задачи", e)
            val errorMessage = when {
                e.message?.contains("timeout", ignoreCase = true) == true || 
                e.message?.contains("Connect timeout", ignoreCase = true) == true -> {
                    "⚠️ Не удалось подключиться к Project Task API серверу. " +
                    "Убедитесь, что сервер запущен на порту 8084."
                }
                e.message?.contains("Connection refused", ignoreCase = true) == true -> {
                    "⚠️ Project Task API сервер не запущен. Запустите терминальную версию приложения."
                }
                else -> "❌ Ошибка при создании задачи: ${e.message}"
            }
            errorMessage
        }
    }
}
