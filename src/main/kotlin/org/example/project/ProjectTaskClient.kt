package org.example.project

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * HTTP клиент для работы с Project Task API
 */
class ProjectTaskClient(private val baseUrl: String = "http://localhost:8084/api") {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }
    
    private val httpClient: HttpClient = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 30000
            connectTimeoutMillis = 15000
            socketTimeoutMillis = 30000
        }
        install(ContentNegotiation) {
            json(json)
        }
    }
    
    /**
     * Создать задачу
     */
    suspend fun createTask(request: CreateTaskRequest): ProjectTask {
        return withContext(Dispatchers.IO) {
            val response = httpClient.post("$baseUrl/tasks") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            
            if (!response.status.isSuccess()) {
                val errorBody = response.bodyAsText()
                throw ProjectTaskException("Failed to create task: HTTP ${response.status.value} - $errorBody")
            }
            
            json.decodeFromString(ProjectTask.serializer(), response.bodyAsText())
        }
    }
    
    /**
     * Получить задачу по ID
     */
    suspend fun getTask(taskId: String): ProjectTask? {
        return withContext(Dispatchers.IO) {
            try {
                val response = httpClient.get("$baseUrl/tasks/$taskId")
                
                if (response.status.value == 404) {
                    return@withContext null
                }
                
                if (!response.status.isSuccess()) {
                    val errorBody = response.bodyAsText()
                    throw ProjectTaskException("Failed to get task: HTTP ${response.status.value} - $errorBody")
                }
                
                json.decodeFromString(ProjectTask.serializer(), response.bodyAsText())
            } catch (e: Exception) {
                if (e is ProjectTaskException) throw e
                throw ProjectTaskException("Failed to get task: ${e.message}", e)
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
        milestone: String? = null,
        epic: String? = null,
        overdue: Boolean? = null,
        search: String? = null,
        page: Int = 1,
        pageSize: Int = 50
    ): TasksResponse {
        return withContext(Dispatchers.IO) {
            val queryParams = mutableListOf<String>()
            status?.forEach { queryParams.add("status=$it") }
            priority?.forEach { queryParams.add("priority=$it") }
            assignee?.let { queryParams.add("assignee=$it") }
            tags?.forEach { queryParams.add("tags=$it") }
            milestone?.let { queryParams.add("milestone=$it") }
            epic?.let { queryParams.add("epic=$it") }
            overdue?.let { queryParams.add("overdue=$it") }
            search?.let { queryParams.add("search=$it") }
            queryParams.add("page=$page")
            queryParams.add("pageSize=$pageSize")
            
            val url = if (queryParams.isNotEmpty()) {
                "$baseUrl/tasks?${queryParams.joinToString("&")}"
            } else {
                "$baseUrl/tasks"
            }
            
            val response = httpClient.get(url)
            
            if (!response.status.isSuccess()) {
                val errorBody = response.bodyAsText()
                throw ProjectTaskException("Failed to get tasks: HTTP ${response.status.value} - $errorBody")
            }
            
            json.decodeFromString(TasksResponse.serializer(), response.bodyAsText())
        }
    }
    
    /**
     * Обновить задачу
     */
    suspend fun updateTask(taskId: String, request: UpdateTaskRequest): ProjectTask? {
        return withContext(Dispatchers.IO) {
            try {
                val response = httpClient.put("$baseUrl/tasks/$taskId") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }
                
                if (response.status.value == 404) {
                    return@withContext null
                }
                
                if (!response.status.isSuccess()) {
                    val errorBody = response.bodyAsText()
                    throw ProjectTaskException("Failed to update task: HTTP ${response.status.value} - $errorBody")
                }
                
                json.decodeFromString(ProjectTask.serializer(), response.bodyAsText())
            } catch (e: Exception) {
                if (e is ProjectTaskException) throw e
                throw ProjectTaskException("Failed to update task: ${e.message}", e)
            }
        }
    }
    
    /**
     * Удалить задачу
     */
    suspend fun deleteTask(taskId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val response = httpClient.delete("$baseUrl/tasks/$taskId")
                
                if (response.status.value == 404) {
                    return@withContext false
                }
                
                response.status.isSuccess()
            } catch (e: Exception) {
                throw ProjectTaskException("Failed to delete task: ${e.message}", e)
            }
        }
    }
    
    /**
     * Получить статус проекта
     */
    suspend fun getProjectStatus(): ProjectStatus {
        return withContext(Dispatchers.IO) {
            val response = httpClient.get("$baseUrl/project/status")
            
            if (!response.status.isSuccess()) {
                val errorBody = response.bodyAsText()
                throw ProjectTaskException("Failed to get project status: HTTP ${response.status.value} - $errorBody")
            }
            
            json.decodeFromString(ProjectStatus.serializer(), response.bodyAsText())
        }
    }
    
    /**
     * Получить загрузку команды
     */
    suspend fun getTeamCapacity(): TeamCapacityResponse {
        return withContext(Dispatchers.IO) {
            val response = httpClient.get("$baseUrl/team/capacity")
            
            if (!response.status.isSuccess()) {
                val errorBody = response.bodyAsText()
                throw ProjectTaskException("Failed to get team capacity: HTTP ${response.status.value} - $errorBody")
            }
            
            json.decodeFromString(TeamCapacityResponse.serializer(), response.bodyAsText())
        }
    }
    
    fun close() {
        httpClient.close()
    }
}

/**
 * Исключение для ошибок Project Task API
 */
class ProjectTaskException(message: String, cause: Throwable? = null) : Exception(message, cause)
