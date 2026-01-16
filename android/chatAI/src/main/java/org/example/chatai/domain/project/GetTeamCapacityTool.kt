package org.example.chatai.domain.project

import android.util.Log
import kotlinx.coroutines.runBlocking
import org.example.chatai.domain.api.OpenRouterPropertyDefinition
import org.example.chatai.domain.api.OpenRouterToolDefinition
import org.example.chatai.domain.api.OpenRouterToolParameters
import org.example.chatai.domain.tools.AgentTool

private const val TAG = "GetTeamCapacityTool"

/**
 * Инструмент для получения загрузки команды (Team MCP).
 */
class GetTeamCapacityTool(
    private val client: ProjectTaskClient
) : AgentTool {
    override val name = "get_team_capacity"
    override val description = "Получить загрузку команды: количество задач на каждого участника, процент загрузки, фактически затраченные часы."
    
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
            val capacity = runBlocking { client.getTeamCapacity() }
            
            buildString {
                appendLine("👥 Загрузка команды")
                appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                if (capacity.members.isEmpty()) {
                    appendLine("Нет данных о загрузке команды")
                } else {
                    capacity.members.forEach { member ->
                        appendLine("📧 ${member.email}:")
                        appendLine("   Всего задач: ${member.totalTasks}")
                        appendLine("   В работе: ${member.tasksInProgress} | Выполнено: ${member.completedTasks}")
                        member.estimatedHours?.let { appendLine("   Оценка: $it часов") }
                        member.actualHours?.let { appendLine("   Фактически: $it часов") }
                        appendLine("   Загрузка: ${String.format("%.1f", member.utilizationRate)}%")
                        appendLine()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при получении загрузки команды", e)
            val errorMessage = when {
                e.message?.contains("timeout", ignoreCase = true) == true || 
                e.message?.contains("Connect timeout", ignoreCase = true) == true -> {
                    "⚠️ Не удалось подключиться к Project Task API серверу. " +
                    "Убедитесь, что сервер запущен на порту 8084."
                }
                e.message?.contains("Connection refused", ignoreCase = true) == true -> {
                    "⚠️ Project Task API сервер не запущен. Запустите терминальную версию приложения."
                }
                else -> "❌ Ошибка при получении загрузки команды: ${e.message}"
            }
            errorMessage
        }
    }
}
