package org.example.project

import kotlinx.coroutines.runBlocking
import org.example.models.OpenRouterPropertyDefinition
import org.example.models.OpenRouterTool
import org.example.models.OpenRouterToolParameters
import org.example.tools.AgentTool

/**
 * Инструмент для создания задачи
 */
class CreateProjectTaskTool(private val client: ProjectTaskClient) : AgentTool {
    override val name = "create_project_task"
    override val description = "Создать новую задачу проекта. Используй для добавления задач в систему управления проектом."
    
    override fun getDefinition() = OpenRouterTool(
        name = name,
        description = description,
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
                appendLine("Статус: ${task.status}")
                appendLine("Приоритет: ${task.priority}")
                task.assignee?.let { appendLine("Исполнитель: $it") }
                task.dueDate?.let { appendLine("Дедлайн: $it") }
                if (task.tags.isNotEmpty()) {
                    appendLine("Теги: ${task.tags.joinToString(", ")}")
                }
                task.estimatedHours?.let { appendLine("Оценка: $it часов") }
            }
        } catch (e: Exception) {
            "❌ Ошибка при создании задачи: ${e.message}"
        }
    }
}

/**
 * Инструмент для получения задач
 */
class GetProjectTasksTool(private val client: ProjectTaskClient) : AgentTool {
    override val name = "get_project_tasks"
    override val description = "Получить список задач проекта с возможностью фильтрации. Используй для просмотра задач, поиска по статусу, приоритету, исполнителю и другим параметрам."
    
    override fun getDefinition() = OpenRouterTool(
        name = name,
        description = description,
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
            "❌ Ошибка при получении задач: ${e.message}"
        }
    }
}

/**
 * Инструмент для обновления задачи
 */
class UpdateProjectTaskTool(private val client: ProjectTaskClient) : AgentTool {
    override val name = "update_project_task"
    override val description = "Обновить задачу проекта. Можно изменить статус, приоритет, исполнителя, дедлайн и другие параметры."
    
    override fun getDefinition() = OpenRouterTool(
        name = name,
        description = description,
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
            "❌ Ошибка при обновлении задачи: ${e.message}"
        }
    }
}

/**
 * Инструмент для получения статуса проекта
 */
class GetProjectStatusTool(private val client: ProjectTaskClient) : AgentTool {
    override val name = "get_project_status"
    override val description = "Получить общий статус проекта: статистику по задачам, просроченные задачи, процент выполнения, критические задачи и ближайшие дедлайны."
    
    override fun getDefinition() = OpenRouterTool(
        name = name,
        description = description,
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
            "❌ Ошибка при получении статуса проекта: ${e.message}"
        }
    }
}

/**
 * Инструмент для получения загрузки команды
 */
class GetTeamCapacityTool(private val client: ProjectTaskClient) : AgentTool {
    override val name = "get_team_capacity"
    override val description = "Получить загрузку команды: количество задач на каждого исполнителя, оценка и фактические часы, просроченные задачи, процент загрузки."
    
    override fun getDefinition() = OpenRouterTool(
        name = name,
        description = description,
        parameters = OpenRouterToolParameters(
            properties = emptyMap()
        )
    )
    
    override fun execute(arguments: Map<String, String>): String {
        return try {
            val capacity = runBlocking { client.getTeamCapacity() }
            
            if (capacity.team.isEmpty()) {
                return "👥 В команде нет назначенных задач"
            }
            
            buildString {
                appendLine("👥 Загрузка команды")
                appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                capacity.team.forEach { member ->
                    appendLine("${member.assignee}:")
                    appendLine("  Всего задач: ${member.totalTasks}")
                    appendLine("  Просрочено: ${member.overdueTasks}")
                    member.estimatedHours?.let { appendLine("  Оценка: ${String.format("%.1f", it)} часов") }
                    member.actualHours?.let { appendLine("  Фактически: ${String.format("%.1f", it)} часов") }
                    appendLine("  Загрузка: ${String.format("%.1f", member.workload)}%")
                    appendLine("  По статусам:")
                    member.tasksByStatus.forEach { (status, count) ->
                        appendLine("    • $status: $count")
                    }
                    appendLine()
                }
                capacity.totalEstimatedHours?.let {
                    appendLine("Всего оценка: ${String.format("%.1f", it)} часов")
                }
                capacity.totalActualHours?.let {
                    appendLine("Всего фактически: ${String.format("%.1f", it)} часов")
                }
            }
        } catch (e: Exception) {
            "❌ Ошибка при получении загрузки команды: ${e.message}"
        }
    }
}
