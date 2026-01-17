package org.example.project

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * REST API сервер для управления задачами проекта
 */
class ProjectTaskApiServer(
    private val taskService: ProjectTaskService
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }
    
    fun configureApiServer(application: Application) {
        application.install(ContentNegotiation) {
            json(this@ProjectTaskApiServer.json)
        }
        application.install(CORS) {
            anyHost()
            allowHeader("Content-Type")
            allowHeader("Authorization")
        }
        
        application.routing {
            // Создать задачу
            post("/api/tasks") {
                try {
                    val request = call.receive<CreateTaskRequest>()
                    val task = taskService.createTask(request)
                    call.respond(HttpStatusCode.Created, task)
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        buildJsonObject {
                            put("error", "Failed to create task: ${e.message}")
                        }
                    )
                }
            }
            
            // Получить задачи с фильтрами
            get("/api/tasks") {
                try {
                    val status = call.request.queryParameters.getAll("status")
                    val priority = call.request.queryParameters.getAll("priority")
                    val assignee = call.request.queryParameters["assignee"]
                    val tags = call.request.queryParameters.getAll("tags")
                    val milestone = call.request.queryParameters["milestone"]
                    val epic = call.request.queryParameters["epic"]
                    val overdue = call.request.queryParameters["overdue"]?.toBoolean()
                    val search = call.request.queryParameters["search"]
                    val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
                    val pageSize = call.request.queryParameters["pageSize"]?.toIntOrNull() ?: 50
                    
                    val filters = TaskFilters(
                        status = status,
                        priority = priority,
                        assignee = assignee,
                        tags = tags,
                        milestone = milestone,
                        epic = epic,
                        overdue = overdue,
                        search = search
                    )
                    
                    val response = taskService.getTasks(filters, page, pageSize)
                    call.respond(response)
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        buildJsonObject {
                            put("error", "Failed to get tasks: ${e.message}")
                        }
                    )
                }
            }
            
            // Получить задачу по ID
            get("/api/tasks/{id}") {
                try {
                    val taskId = call.parameters["id"] ?: throw IllegalArgumentException("Task ID is required")
                    val task = taskService.getTask(taskId)
                    if (task != null) {
                        call.respond(task)
                    } else {
                        call.respond(HttpStatusCode.NotFound, buildJsonObject {
                            put("error", "Task not found: $taskId")
                        })
                    }
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        buildJsonObject {
                            put("error", "Failed to get task: ${e.message}")
                        }
                    )
                }
            }
            
            // Обновить задачу
            put("/api/tasks/{id}") {
                try {
                    val taskId = call.parameters["id"] ?: throw IllegalArgumentException("Task ID is required")
                    val request = call.receive<UpdateTaskRequest>()
                    val task = taskService.updateTask(taskId, request)
                    if (task != null) {
                        call.respond(task)
                    } else {
                        call.respond(HttpStatusCode.NotFound, buildJsonObject {
                            put("error", "Task not found: $taskId")
                        })
                    }
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        buildJsonObject {
                            put("error", "Failed to update task: ${e.message}")
                        }
                    )
                }
            }
            
            // Удалить задачу
            delete("/api/tasks/{id}") {
                try {
                    val taskId = call.parameters["id"] ?: throw IllegalArgumentException("Task ID is required")
                    val deleted = taskService.deleteTask(taskId)
                    if (deleted) {
                        call.respond(HttpStatusCode.NoContent)
                    } else {
                        call.respond(HttpStatusCode.NotFound, buildJsonObject {
                            put("error", "Task not found: $taskId")
                        })
                    }
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        buildJsonObject {
                            put("error", "Failed to delete task: ${e.message}")
                        }
                    )
                }
            }
            
            // Получить статус проекта
            get("/api/project/status") {
                try {
                    val status = taskService.getProjectStatus()
                    call.respond(status)
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        buildJsonObject {
                            put("error", "Failed to get project status: ${e.message}")
                        }
                    )
                }
            }
            
            // Получить загрузку команды
            get("/api/team/capacity") {
                try {
                    val capacity = taskService.getTeamCapacity()
                    call.respond(capacity)
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        buildJsonObject {
                            put("error", "Failed to get team capacity: ${e.message}")
                        }
                    )
                }
            }
            
            // Health check
            get("/api/health") {
                call.respond(buildJsonObject {
                    put("status", "ok")
                    put("service", "Project Task API")
                })
            }
            
            // Deployment test endpoint
            get("/api/deployment/test") {
                val version = try {
                    java.io.File("VERSION").readText().trim()
                } catch (e: Exception) {
                    "unknown"
                }
                
                call.respond(buildJsonObject {
                    put("status", "success")
                    put("message", "Deployment test endpoint is working!")
                    put("version", version)
                    put("timestamp", System.currentTimeMillis())
                    put("environment", System.getenv("RAILWAY_ENVIRONMENT") ?: "local")
                    put("deployment", buildJsonObject {
                        put("platform", "Railway")
                        put("status", "active")
                    })
                })
            }
            
            // Version info endpoint
            get("/api/version") {
                val version = try {
                    java.io.File("VERSION").readText().trim()
                } catch (e: Exception) {
                    "1.0.0"
                }
                
                call.respond(buildJsonObject {
                    put("version", version)
                    put("application", "OpenRouter Agent")
                    put("deployment", "Railway")
                })
            }
        }
    }
}
