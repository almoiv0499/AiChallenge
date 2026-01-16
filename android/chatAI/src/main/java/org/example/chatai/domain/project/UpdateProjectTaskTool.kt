package org.example.chatai.domain.project

import android.util.Log
import kotlinx.coroutines.runBlocking
import org.example.chatai.domain.api.OpenRouterPropertyDefinition
import org.example.chatai.domain.api.OpenRouterToolDefinition
import org.example.chatai.domain.api.OpenRouterToolParameters
import org.example.chatai.domain.tools.AgentTool

private const val TAG = "UpdateProjectTaskTool"

/**
 * Инструмент для обновления задачи проекта (Team MCP).
 */
class UpdateProjectTaskTool(
    private val client: ProjectTaskClient
) : AgentTool {
    override val name = "update_project_task"
    override val description = "Обновить задачу проекта. Можно изменить статус, приоритет, исполнителя, дедлайн и другие параметры."
    
    override fun getDefinition() = OpenRouterToolDefinition(
        name = name,
        description = description,
        type = "function",
        parameters = OpenRouterToolParameters(
            properties = mapOf(
                "taskId" to OpenRouterPropertyDefinition(
                    type = "string",
                    description = "ID задачи для обновления (обязательно)"
                ),
                "status" to OpenRouterPropertyDefinition(
                    type = "string",
                    description = "Новый статус (TODO, IN_PROGRESS, IN_REVIEW, BLOCKED, DONE, CANCELLED)",
                    enum = listOf("TODO", "IN_PROGRESS", "IN_REVIEW", "BLOCKED", "DONE", "CANCELLED")
                ),
                "priority" to OpenRouterPropertyDefinition(
                    type = "string",
                    description = "Новый приоритет",
                    enum = listOf("LOW", "MEDIUM", "HIGH", "CRITICAL")
                ),
                "assignee" to OpenRouterPropertyDefinition(
                    type = "string",
                    description = "Email нового исполнителя"
                ),
                "dueDate" to OpenRouterPropertyDefinition(
                    type = "string",
                    description = "Новый дедлайн в формате YYYY-MM-DD"
                ),
                "actualHours" to OpenRouterPropertyDefinition(
                    type = "number",
                    description = "Фактически затраченные часы"
                )
            ),
            required = listOf("taskId")
        )
    )
    
    override fun execute(arguments: Map<String, String>): String {
        return try {
            val taskId = arguments["taskId"] ?: return "Ошибка: не указан ID задачи"
            
            val request = UpdateTaskRequest(
                status = arguments["status"]?.uppercase(),
                priority = arguments["priority"]?.uppercase(),
                assignee = arguments["assignee"],
                dueDate = arguments["dueDate"],
                actualHours = arguments["actualHours"]?.toDoubleOrNull()
            )
            
            val task = runBlocking { client.updateTask(taskId, request) }
            
            if (task == null) {
                return "❌ Задача с ID $taskId не найдена"
            }
            
            buildString {
                appendLine("✅ Задача успешно обновлена!")
                appendLine("ID: ${task.id}")
                appendLine("Название: ${task.title}")
                appendLine("Статус: ${task.status}")
                appendLine("Приоритет: ${task.priority}")
                task.assignee?.let { appendLine("Исполнитель: $it") }
                task.dueDate?.let { appendLine("Дедлайн: $it") }
                task.actualHours?.let { appendLine("Фактические часы: $it") }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при обновлении задачи", e)
            val errorMessage = when {
                e.message?.contains("timeout", ignoreCase = true) == true || 
                e.message?.contains("Connect timeout", ignoreCase = true) == true -> {
                    "⚠️ Не удалось подключиться к Project Task API серверу. " +
                    "Убедитесь, что сервер запущен на порту 8084."
                }
                e.message?.contains("Connection refused", ignoreCase = true) == true -> {
                    "⚠️ Project Task API сервер не запущен. Запустите терминальную версию приложения."
                }
                else -> "❌ Ошибка при обновлении задачи: ${e.message}"
            }
            errorMessage
        }
    }
}
