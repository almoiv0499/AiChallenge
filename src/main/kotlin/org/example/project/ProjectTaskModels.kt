package org.example.project

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Приоритет задачи
 */
enum class TaskPriority {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

/**
 * Статус задачи
 */
enum class TaskStatus {
    TODO,
    IN_PROGRESS,
    IN_REVIEW,
    BLOCKED,
    DONE,
    CANCELLED
}

/**
 * Модель задачи проекта
 */
@Serializable
data class ProjectTask(
    val id: String,
    val title: String,
    val description: String? = null,
    val status: String, // TaskStatus as string
    val priority: String, // TaskPriority as string
    val assignee: String? = null,
    val dueDate: String? = null, // ISO 8601 format
    val tags: List<String> = emptyList(),
    val dependencies: List<String> = emptyList(), // IDs of dependent tasks
    val createdAt: String, // ISO 8601 format
    val updatedAt: String, // ISO 8601 format
    val createdBy: String? = null,
    val estimatedHours: Double? = null,
    val actualHours: Double? = null,
    val milestone: String? = null,
    val epic: String? = null
) {
    fun toTaskStatus(): TaskStatus {
        return try {
            TaskStatus.valueOf(status.uppercase())
        } catch (e: Exception) {
            TaskStatus.TODO
        }
    }
    
    fun toTaskPriority(): TaskPriority {
        return try {
            TaskPriority.valueOf(priority.uppercase())
        } catch (e: Exception) {
            TaskPriority.MEDIUM
        }
    }
    
    fun isOverdue(): Boolean {
        if (dueDate == null || status == TaskStatus.DONE.name || status == TaskStatus.CANCELLED.name) {
            return false
        }
        val due = LocalDate.parse(dueDate, DateTimeFormatter.ISO_LOCAL_DATE)
        return LocalDate.now().isAfter(due)
    }
}

/**
 * Запрос на создание задачи
 */
@Serializable
data class CreateTaskRequest(
    val title: String,
    val description: String? = null,
    val priority: String = TaskPriority.MEDIUM.name,
    val assignee: String? = null,
    val dueDate: String? = null,
    val tags: List<String> = emptyList(),
    val dependencies: List<String> = emptyList(),
    val estimatedHours: Double? = null,
    val milestone: String? = null,
    val epic: String? = null
)

/**
 * Запрос на обновление задачи
 */
@Serializable
data class UpdateTaskRequest(
    val title: String? = null,
    val description: String? = null,
    val status: String? = null,
    val priority: String? = null,
    val assignee: String? = null,
    val dueDate: String? = null,
    val tags: List<String>? = null,
    val dependencies: List<String>? = null,
    val estimatedHours: Double? = null,
    val actualHours: Double? = null,
    val milestone: String? = null,
    val epic: String? = null
)

/**
 * Фильтры для получения задач
 */
@Serializable
data class TaskFilters(
    val status: List<String>? = null,
    val priority: List<String>? = null,
    val assignee: String? = null,
    val tags: List<String>? = null,
    val milestone: String? = null,
    val epic: String? = null,
    val overdue: Boolean? = null,
    val search: String? = null // Поиск по title и description
)

/**
 * Ответ со списком задач
 */
@Serializable
data class TasksResponse(
    val tasks: List<ProjectTask>,
    val total: Int,
    val page: Int = 1,
    val pageSize: Int = 50
)

/**
 * Статус проекта
 */
@Serializable
data class ProjectStatus(
    val totalTasks: Int,
    val tasksByStatus: Map<String, Int>,
    val tasksByPriority: Map<String, Int>,
    val overdueTasks: Int,
    val tasksByAssignee: Map<String, Int>,
    val completionRate: Double, // Процент выполненных задач
    val averageCompletionTime: Double? = null, // Среднее время выполнения в днях
    val upcomingDeadlines: List<ProjectTask> = emptyList(), // Ближайшие дедлайны (7 дней)
    val blockedTasks: List<ProjectTask> = emptyList(),
    val criticalTasks: List<ProjectTask> = emptyList()
)

/**
 * Загрузка команды
 */
@Serializable
data class TeamCapacity(
    val assignee: String,
    val totalTasks: Int,
    val tasksByStatus: Map<String, Int>,
    val estimatedHours: Double?,
    val actualHours: Double?,
    val overdueTasks: Int,
    val workload: Double // Процент загрузки (0-100)
)

/**
 * Ответ с загрузкой команды
 */
@Serializable
data class TeamCapacityResponse(
    val team: List<TeamCapacity>,
    val totalEstimatedHours: Double?,
    val totalActualHours: Double?
)
