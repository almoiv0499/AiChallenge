package org.example.chatai.domain.project

import android.util.Log
import kotlinx.coroutines.runBlocking
import org.example.chatai.domain.api.OpenRouterToolDefinition
import org.example.chatai.domain.api.OpenRouterToolParameters
import org.example.chatai.domain.tools.AgentTool

private const val TAG = "GetProjectStatusTool"

/**
 * Инструмент для получения статуса проекта (Team MCP).
 */
class GetProjectStatusTool(
    private val client: ProjectTaskClient
) : AgentTool {
    override val name = "get_project_status"
    override val description = "Получить общий статус проекта: статистику по задачам, просроченные задачи, процент выполнения, критические задачи и ближайшие дедлайны."
    
    override fun getDefinition() = OpenRouterToolDefinition(
        name = name,
        description = description,
        type = "function",
        parameters = OpenRouterToolParameters(
            properties = emptyMap()
        )
    )
    
    override fun execute(arguments: Map<String, String>): String {
        return try {
            val status = runBlocking { client.getProjectStatus() }
            
            buildString {
                appendLine("📊 Статус проекта")
                appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                appendLine("Всего задач: ${status.totalTasks}")
                appendLine("Процент выполнения: ${String.format("%.1f", status.completionRate)}%")
                appendLine("Просрочено: ${status.overdueTasks}")
                appendLine()
                appendLine("По статусам:")
                status.tasksByStatus.forEach { (stat, count) ->
                    appendLine("  • $stat: $count")
                }
                appendLine()
                appendLine("По приоритетам:")
                status.tasksByPriority.forEach { (priority, count) ->
                    appendLine("  • $priority: $count")
                }
                if (status.criticalTasks.isNotEmpty()) {
                    appendLine()
                    appendLine("🚨 Критические задачи (${status.criticalTasks.size}):")
                    status.criticalTasks.take(5).forEach { task ->
                        appendLine("  • ${task.title} (ID: ${task.id})")
                        task.dueDate?.let { appendLine("    Дедлайн: $it") }
                    }
                }
                if (status.upcomingDeadlines.isNotEmpty()) {
                    appendLine()
                    appendLine("📅 Ближайшие дедлайны (${status.upcomingDeadlines.size}):")
                    status.upcomingDeadlines.take(5).forEach { task ->
                        appendLine("  • ${task.title} (ID: ${task.id})")
                        appendLine("    Дедлайн: ${task.dueDate}")
                    }
                }
                if (status.blockedTasks.isNotEmpty()) {
                    appendLine()
                    appendLine("🚫 Заблокированные задачи (${status.blockedTasks.size}):")
                    status.blockedTasks.take(5).forEach { task ->
                        appendLine("  • ${task.title} (ID: ${task.id})")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при получении статуса проекта", e)
            val errorMessage = when {
                e.message?.contains("timeout", ignoreCase = true) == true || 
                e.message?.contains("Connect timeout", ignoreCase = true) == true -> {
                    "⚠️ Не удалось подключиться к Project Task API серверу. " +
                    "Убедитесь, что сервер запущен на порту 8084."
                }
                e.message?.contains("Connection refused", ignoreCase = true) == true -> {
                    "⚠️ Project Task API сервер не запущен. Запустите терминальную версию приложения."
                }
                else -> "❌ Ошибка при получении статуса проекта: ${e.message}"
            }
            errorMessage
        }
    }
}
