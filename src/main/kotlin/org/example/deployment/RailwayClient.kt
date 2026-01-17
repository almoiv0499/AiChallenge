package org.example.deployment

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Клиент для работы с Railway API
 * Документация: https://docs.railway.app/develop/api
 */
class RailwayClient(
    private val apiToken: String
) {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            // Railway API возвращает JSON - используем стандартную JSON сериализацию
        }
        
        defaultRequest {
            header(HttpHeaders.Authorization, "Bearer $apiToken")
        }
    }
    
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    /**
     * Получить информацию о проекте
     */
    suspend fun getProject(projectId: String): Project? {
        return try {
            val response: HttpResponse = client.get("https://api.railway.app/v1/projects/$projectId")
            if (response.status.isSuccess()) {
                json.decodeFromString<ProjectResponse>(response.bodyAsText()).project
            } else {
                null
            }
        } catch (e: Exception) {
            println("Ошибка при получении проекта: ${e.message}")
            null
        }
    }
    
    /**
     * Получить список сервисов в проекте
     */
    suspend fun getServices(projectId: String): List<Service> {
        return try {
            val response: HttpResponse = client.get("https://api.railway.app/v1/projects/$projectId/services")
            if (response.status.isSuccess()) {
                json.decodeFromString<ServicesResponse>(response.bodyAsText()).services
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            println("Ошибка при получении сервисов: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Установить переменную окружения для сервиса
     */
    suspend fun setVariable(
        serviceId: String,
        name: String,
        value: String
    ): Boolean {
        return try {
            val request = SetVariableRequest(
                name = name,
                value = value
            )
            
            val response: HttpResponse = client.post("https://api.railway.app/v1/variables") {
                setBody(json.encodeToString(SetVariableRequest.serializer(), request))
                parameter("serviceId", serviceId)
                header(HttpHeaders.ContentType, ContentType.Application.Json)
            }
            
            response.status.isSuccess()
        } catch (e: Exception) {
            println("Ошибка при установке переменной $name: ${e.message}")
            false
        }
    }
    
    /**
     * Запустить деплой для сервиса
     */
    suspend fun triggerDeployment(serviceId: String): Deployment? {
        return try {
            val request = TriggerDeploymentRequest(
                serviceId = serviceId
            )
            
            val response: HttpResponse = client.post("https://api.railway.app/v1/deployments") {
                setBody(json.encodeToString(TriggerDeploymentRequest.serializer(), request))
                header(HttpHeaders.ContentType, ContentType.Application.Json)
            }
            
            if (response.status.isSuccess()) {
                json.decodeFromString<DeploymentResponse>(response.bodyAsText()).deployment
            } else {
                println("Ошибка деплоя: ${response.status} - ${response.bodyAsText()}")
                null
            }
        } catch (e: Exception) {
            println("Ошибка при запуске деплоя: ${e.message}")
            null
        }
    }
    
    /**
     * Получить статус деплоя
     */
    suspend fun getDeploymentStatus(deploymentId: String): DeploymentStatus? {
        return try {
            val response: HttpResponse = client.get("https://api.railway.app/v1/deployments/$deploymentId")
            if (response.status.isSuccess()) {
                val deployment = json.decodeFromString<DeploymentResponse>(response.bodyAsText()).deployment
                when {
                    deployment.status == "SUCCESS" -> DeploymentStatus.SUCCESS
                    deployment.status == "FAILED" -> DeploymentStatus.FAILED
                    deployment.status == "BUILDING" || deployment.status == "DEPLOYING" -> DeploymentStatus.IN_PROGRESS
                    else -> DeploymentStatus.UNKNOWN
                }
            } else {
                null
            }
        } catch (e: Exception) {
            println("Ошибка при получении статуса деплоя: ${e.message}")
            null
        }
    }
    
    fun close() {
        client.close()
    }
}

@Serializable
data class Project(
    val id: String,
    val name: String,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

@Serializable
data class ProjectResponse(
    val project: Project
)

@Serializable
data class Service(
    val id: String,
    val name: String,
    val projectId: String,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

@Serializable
data class ServicesResponse(
    val services: List<Service>
)

@Serializable
data class SetVariableRequest(
    val name: String,
    val value: String
)

@Serializable
data class TriggerDeploymentRequest(
    val serviceId: String
)

@Serializable
data class Deployment(
    val id: String,
    val serviceId: String,
    val status: String,
    val createdAt: String? = null
)

@Serializable
data class DeploymentResponse(
    val deployment: Deployment
)

enum class DeploymentStatus {
    SUCCESS,
    FAILED,
    IN_PROGRESS,
    UNKNOWN
}
