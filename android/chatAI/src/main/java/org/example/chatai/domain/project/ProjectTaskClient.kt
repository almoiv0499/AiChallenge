package org.example.chatai.domain.project

import android.util.Log
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val TAG = "ProjectTaskClient"

/**
 * HTTP клиент для работы с Project Task API (Team MCP).
 * Использует Android HTTP клиент для подключения к локальному серверу.
 */
class ProjectTaskClient(
    private val baseUrl: String = "http://10.0.2.2:8084/api" // 10.0.2.2 - адрес localhost из Android эмулятора
) {
    
    private val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            })
        }
        install(Logging) {
            level = LogLevel.INFO
        }
        engine {
            connectTimeout = 5000 // 5 секунд на подключение
            socketTimeout = 10000 // 10 секунд на чтение
        }
    }
    
    /**
     * Создать задачу
     */
    suspend fun createTask(request: CreateTaskRequest): ProjectTask {
        return withContext(Dispatchers.IO) {
            try {
                val response: HttpResponse = client.post("$baseUrl/tasks") {
                    header(HttpHeaders.ContentType, ContentType.Application.Json)
                    setBody(request)
                }
                
                if (!response.status.isSuccess()) {
                    val errorBody = response.bodyAsText()
                    throw ProjectTaskException("Failed to create task: HTTP ${response.status.value} - $errorBody")
                }
                
                response.body()
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка создания задачи", e)
                throw ProjectTaskException("Ошибка создания задачи: ${e.message}", e)
            }
        }
    }
    
    /**
     * Получить задачу по ID
     */
    suspend fun getTask(taskId: String): ProjectTask? {
        return withContext(Dispatchers.IO) {
            try {
                val response: HttpResponse = client.get("$baseUrl/tasks/$taskId")
                
                if (response.status.value == 404) {
                    return@withContext null
                }
                
                if (!response.status.isSuccess()) {
                    val errorBody = response.bodyAsText()
                    throw ProjectTaskException("Failed to get task: HTTP ${response.status.value} - $errorBody")
                }
                
                response.body()
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка получения задачи $taskId", e)
                if (e is ProjectTaskException) throw e
                throw ProjectTaskException("Ошибка получения задачи: ${e.message}", e)
            }
        }
    }
    
    /**
     * Получить список задач с фильтрами
     */
    suspend fun getTasks(
        status: List<String>? = null,
        priority: List<String>? = null,
        assignee: String? = null,
        tags: List<String>? = null,
        overdue: Boolean? = null,
        search: String? = null,
        page: Int = 1,
        pageSize: Int = 50
    ): TasksResponse {
        return withContext(Dispatchers.IO) {
            try {
                val urlBuilder = StringBuilder("$baseUrl/tasks?page=$page&pageSize=$pageSize")
                status?.forEach { urlBuilder.append("&status=$it") }
                priority?.forEach { urlBuilder.append("&priority=$it") }
                assignee?.let { urlBuilder.append("&assignee=$it") }
                tags?.forEach { urlBuilder.append("&tags=$it") }
                overdue?.let { urlBuilder.append("&overdue=$it") }
                search?.let { urlBuilder.append("&search=$it") }
                
                val response: HttpResponse = client.get(urlBuilder.toString())
                
                if (!response.status.isSuccess()) {
                    val errorBody = response.bodyAsText()
                    throw ProjectTaskException("Failed to get tasks: HTTP ${response.status.value} - $errorBody")
                }
                
                response.body()
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка получения задач", e)
                throw ProjectTaskException("Ошибка получения задач: ${e.message}", e)
            }
        }
    }
    
    /**
     * Обновить задачу
     */
    suspend fun updateTask(taskId: String, request: UpdateTaskRequest): ProjectTask? {
        return withContext(Dispatchers.IO) {
            try {
                val response: HttpResponse = client.put("$baseUrl/tasks/$taskId") {
                    header(HttpHeaders.ContentType, ContentType.Application.Json)
                    setBody(request)
                }
                
                if (response.status.value == 404) {
                    return@withContext null
                }
                
                if (!response.status.isSuccess()) {
                    val errorBody = response.bodyAsText()
                    throw ProjectTaskException("Failed to update task: HTTP ${response.status.value} - $errorBody")
                }
                
                response.body()
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка обновления задачи $taskId", e)
                throw ProjectTaskException("Ошибка обновления задачи: ${e.message}", e)
            }
        }
    }
    
    /**
     * Получить статус проекта
     */
    suspend fun getProjectStatus(): ProjectStatus {
        return withContext(Dispatchers.IO) {
            try {
                val response: HttpResponse = client.get("$baseUrl/status")
                
                if (!response.status.isSuccess()) {
                    val errorBody = response.bodyAsText()
                    throw ProjectTaskException("Failed to get project status: HTTP ${response.status.value} - $errorBody")
                }
                
                response.body()
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка получения статуса проекта", e)
                throw ProjectTaskException("Ошибка получения статуса проекта: ${e.message}", e)
            }
        }
    }
    
    /**
     * Получить загрузку команды
     */
    suspend fun getTeamCapacity(): TeamCapacity {
        return withContext(Dispatchers.IO) {
            try {
                val response: HttpResponse = client.get("$baseUrl/team/capacity")
                
                if (!response.status.isSuccess()) {
                    val errorBody = response.bodyAsText()
                    throw ProjectTaskException("Failed to get team capacity: HTTP ${response.status.value} - $errorBody")
                }
                
                response.body()
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка получения загрузки команды", e)
                throw ProjectTaskException("Ошибка получения загрузки команды: ${e.message}", e)
            }
        }
    }
    
    fun close() {
        client.close()
    }
}

/**
 * Исключение для ошибок Project Task API
 */
class ProjectTaskException(message: String, cause: Throwable? = null) : Exception(message, cause)

// Модели данных (упрощенные версии)

@Serializable
data class ProjectTask(
    val id: String,
    val title: String,
    val description: String? = null,
    val status: String,
    val priority: String,
    val assignee: String? = null,
    val dueDate: String? = null,
    val tags: List<String> = emptyList(),
    val estimatedHours: Double? = null,
    val actualHours: Double? = null,
    val milestone: String? = null,
    val epic: String? = null
) {
    fun isOverdue(): Boolean {
        if (dueDate == null || status == "DONE" || status == "CANCELLED") {
            return false
        }
        // Простая проверка - можно улучшить с использованием дат
        return false
    }
}

@Serializable
data class CreateTaskRequest(
    val title: String,
    val description: String? = null,
    val priority: String = "MEDIUM",
    val assignee: String? = null,
    val dueDate: String? = null,
    val tags: List<String> = emptyList(),
    val estimatedHours: Double? = null,
    val milestone: String? = null,
    val epic: String? = null
)

@Serializable
data class UpdateTaskRequest(
    val title: String? = null,
    val description: String? = null,
    val status: String? = null,
    val priority: String? = null,
    val assignee: String? = null,
    val dueDate: String? = null,
    val actualHours: Double? = null
)

@Serializable
data class TasksResponse(
    val tasks: List<ProjectTask>,
    val total: Int,
    val page: Int,
    @SerialName("pageSize") val pageSize: Int
)

@Serializable
data class ProjectStatus(
    val totalTasks: Int,
    @SerialName("completionRate") val completionRate: Double,
    @SerialName("overdueTasks") val overdueTasks: Int,
    @SerialName("tasksByStatus") val tasksByStatus: Map<String, Int>,
    @SerialName("tasksByPriority") val tasksByPriority: Map<String, Int>,
    @SerialName("criticalTasks") val criticalTasks: List<ProjectTask> = emptyList(),
    @SerialName("upcomingDeadlines") val upcomingDeadlines: List<ProjectTask> = emptyList(),
    @SerialName("blockedTasks") val blockedTasks: List<ProjectTask> = emptyList()
)

@Serializable
data class TeamCapacity(
    val members: List<MemberCapacity>
)

@Serializable
data class MemberCapacity(
    val email: String,
    val totalTasks: Int,
    @SerialName("tasksInProgress") val tasksInProgress: Int,
    @SerialName("completedTasks") val completedTasks: Int,
    @SerialName("estimatedHours") val estimatedHours: Double?,
    @SerialName("actualHours") val actualHours: Double?,
    @SerialName("utilizationRate") val utilizationRate: Double
)
