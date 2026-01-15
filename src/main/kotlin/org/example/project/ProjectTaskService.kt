package org.example.project

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Сервис для управления задачами проекта
 */
class ProjectTaskService(private val storage: ProjectTaskStorage) {
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    private val dateOnlyFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    
    /**
     * Создать задачу
     */
    fun createTask(request: CreateTaskRequest, createdBy: String? = null): ProjectTask {
        val now = LocalDateTime.now().format(dateFormatter)
        val task = ProjectTask(
            id = UUID.randomUUID().toString(),
            title = request.title,
            description = request.description,
            status = TaskStatus.TODO.name,
            priority = request.priority.uppercase(),
            assignee = request.assignee,
            dueDate = request.dueDate,
            tags = request.tags,
            dependencies = request.dependencies,
            createdAt = now,
            updatedAt = now,
            createdBy = createdBy,
            estimatedHours = request.estimatedHours,
            milestone = request.milestone,
            epic = request.epic
        )
        
        if (storage.saveOrUpdateTask(task)) {
            return task
        } else {
            throw RuntimeException("Failed to create task")
        }
    }
    
    /**
     * Обновить задачу
     */
    fun updateTask(taskId: String, request: UpdateTaskRequest): ProjectTask? {
        val existingTask = storage.getTaskById(taskId) ?: return null
        
        val updatedTask = existingTask.copy(
            title = request.title ?: existingTask.title,
            description = request.description ?: existingTask.description,
            status = request.status?.uppercase() ?: existingTask.status,
            priority = request.priority?.uppercase() ?: existingTask.priority,
            assignee = request.assignee ?: existingTask.assignee,
            dueDate = request.dueDate ?: existingTask.dueDate,
            tags = request.tags ?: existingTask.tags,
            dependencies = request.dependencies ?: existingTask.dependencies,
            updatedAt = LocalDateTime.now().format(dateFormatter),
            estimatedHours = request.estimatedHours ?: existingTask.estimatedHours,
            actualHours = request.actualHours ?: existingTask.actualHours,
            milestone = request.milestone ?: existingTask.milestone,
            epic = request.epic ?: existingTask.epic
        )
        
        if (storage.saveOrUpdateTask(updatedTask)) {
            return updatedTask
        } else {
            throw RuntimeException("Failed to update task")
        }
    }
    
    /**
     * Получить задачу по ID
     */
    fun getTask(taskId: String): ProjectTask? {
        return storage.getTaskById(taskId)
    }
    
    /**
     * Получить задачи с фильтрами
     */
    fun getTasks(filters: TaskFilters? = null, page: Int = 1, pageSize: Int = 50): TasksResponse {
        val (tasks, total) = storage.getTasks(filters, page, pageSize)
        return TasksResponse(
            tasks = tasks,
            total = total,
            page = page,
            pageSize = pageSize
        )
    }
    
    /**
     * Удалить задачу
     */
    fun deleteTask(taskId: String): Boolean {
        return storage.deleteTask(taskId)
    }
    
    /**
     * Получить статус проекта
     */
    fun getProjectStatus(): ProjectStatus {
        val allTasks = storage.getAllTasks()
        
        if (allTasks.isEmpty()) {
            return ProjectStatus(
                totalTasks = 0,
                tasksByStatus = emptyMap(),
                tasksByPriority = emptyMap(),
                overdueTasks = 0,
                tasksByAssignee = emptyMap(),
                completionRate = 0.0
            )
        }
        
        val tasksByStatus = allTasks.groupingBy { it.status }.eachCount()
        val tasksByPriority = allTasks.groupingBy { it.priority }.eachCount()
        val overdueTasks = allTasks.count { it.isOverdue() }
        val tasksByAssignee = allTasks
            .filter { it.assignee != null }
            .groupingBy { it.assignee!! }
            .eachCount()
        
        val doneTasks = allTasks.count { it.status == TaskStatus.DONE.name }
        val completionRate = if (allTasks.isNotEmpty()) {
            (doneTasks.toDouble() / allTasks.size) * 100.0
        } else {
            0.0
        }
        
        // Ближайшие дедлайны (7 дней)
        val sevenDaysFromNow = LocalDate.now().plusDays(7)
        val upcomingDeadlines = allTasks
            .filter { 
                it.dueDate != null && 
                it.status != TaskStatus.DONE.name && 
                it.status != TaskStatus.CANCELLED.name
            }
            .filter { task ->
                try {
                    val dueDate = LocalDate.parse(task.dueDate, dateOnlyFormatter)
                    dueDate.isAfter(LocalDate.now().minusDays(1)) && dueDate.isBefore(sevenDaysFromNow.plusDays(1))
                } catch (e: Exception) {
                    false
                }
            }
            .sortedBy { task ->
                try {
                    LocalDate.parse(task.dueDate, dateOnlyFormatter)
                } catch (e: Exception) {
                    LocalDate.MAX
                }
            }
            .take(10)
        
        // Заблокированные задачи
        val blockedTasks = allTasks.filter { it.status == TaskStatus.BLOCKED.name }
        
        // Критические задачи
        val criticalTasks = allTasks
            .filter { 
                it.priority == TaskPriority.CRITICAL.name && 
                it.status != TaskStatus.DONE.name && 
                it.status != TaskStatus.CANCELLED.name
            }
            .sortedBy { task ->
                try {
                    task.dueDate?.let { LocalDate.parse(it, dateOnlyFormatter) } ?: LocalDate.MAX
                } catch (e: Exception) {
                    LocalDate.MAX
                }
            }
        
        return ProjectStatus(
            totalTasks = allTasks.size,
            tasksByStatus = tasksByStatus,
            tasksByPriority = tasksByPriority,
            overdueTasks = overdueTasks,
            tasksByAssignee = tasksByAssignee,
            completionRate = completionRate,
            upcomingDeadlines = upcomingDeadlines,
            blockedTasks = blockedTasks,
            criticalTasks = criticalTasks
        )
    }
    
    /**
     * Получить загрузку команды
     */
    fun getTeamCapacity(): TeamCapacityResponse {
        val allTasks = storage.getAllTasks()
        
        val assignees = allTasks
            .filter { it.assignee != null }
            .map { it.assignee!! }
            .distinct()
        
        val teamCapacity = assignees.map { assignee ->
            val assigneeTasks = allTasks.filter { it.assignee == assignee }
            val tasksByStatus = assigneeTasks.groupingBy { it.status }.eachCount()
            val overdueTasks = assigneeTasks.count { it.isOverdue() }
            val totalEstimated = assigneeTasks.sumOf { it.estimatedHours ?: 0.0 }
            val totalActual = assigneeTasks.sumOf { it.actualHours ?: 0.0 }
            
            // Процент загрузки (можно улучшить логику)
            val workload = if (totalEstimated > 0) {
                (totalActual / totalEstimated * 100.0).coerceIn(0.0, 200.0)
            } else {
                0.0
            }
            
            TeamCapacity(
                assignee = assignee,
                totalTasks = assigneeTasks.size,
                tasksByStatus = tasksByStatus,
                estimatedHours = if (totalEstimated > 0) totalEstimated else null,
                actualHours = if (totalActual > 0) totalActual else null,
                overdueTasks = overdueTasks,
                workload = workload
            )
        }
        
        val totalEstimatedHours = allTasks.sumOf { it.estimatedHours ?: 0.0 }
        val totalActualHours = allTasks.sumOf { it.actualHours ?: 0.0 }
        
        return TeamCapacityResponse(
            team = teamCapacity,
            totalEstimatedHours = if (totalEstimatedHours > 0) totalEstimatedHours else null,
            totalActualHours = if (totalActualHours > 0) totalActualHours else null
        )
    }
}
