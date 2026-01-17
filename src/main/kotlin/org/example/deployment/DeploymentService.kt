package org.example.deployment

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

/**
 * Сервис для автоматического деплоя приложения на Railway
 */
class DeploymentService(
    private val railwayClient: RailwayClient,
    private val projectId: String,
    private val serviceId: String
) {
    /**
     * Выполнить полный цикл деплоя:
     * 1. Проверка проекта и сервиса
     * 2. Установка переменных окружения
     * 3. Запуск деплоя
     * 4. Ожидание завершения
     */
    suspend fun deploy(
        environmentVariables: Map<String, String> = emptyMap(),
        waitForCompletion: Boolean = true
    ): DeploymentResult {
        println("🚀 Начинаем деплой на Railway...")
        
        // 1. Проверка проекта
        val project = railwayClient.getProject(projectId)
        if (project == null) {
            return DeploymentResult.error("Проект $projectId не найден")
        }
        println("✅ Проект найден: ${project.name}")
        
        // 2. Проверка сервиса
        val services = railwayClient.getServices(projectId)
        val service = services.find { it.id == serviceId }
        if (service == null) {
            return DeploymentResult.error("Сервис $serviceId не найден в проекте")
        }
        println("✅ Сервис найден: ${service.name}")
        
        // 3. Установка переменных окружения
        if (environmentVariables.isNotEmpty()) {
            println("📝 Устанавливаем переменные окружения...")
            var successCount = 0
            environmentVariables.forEach { (name, value) ->
                if (railwayClient.setVariable(serviceId, name, value)) {
                    println("  ✅ $name установлена")
                    successCount++
                } else {
                    println("  ❌ Не удалось установить $name")
                }
                delay(500) // Небольшая задержка между запросами
            }
            println("Установлено $successCount из ${environmentVariables.size} переменных")
        }
        
        // 4. Запуск деплоя
        println("🚀 Запускаем деплой...")
        val deployment = railwayClient.triggerDeployment(serviceId)
        if (deployment == null) {
            return DeploymentResult.error("Не удалось запустить деплой")
        }
        println("✅ Деплой запущен: ${deployment.id}")
        println("   Статус: ${deployment.status}")
        
        // 5. Ожидание завершения (опционально)
        if (waitForCompletion) {
            println("⏳ Ожидаем завершения деплоя...")
            var status: DeploymentStatus? = null
            var attempts = 0
            val maxAttempts = 60 // 5 минут максимум (60 * 5 секунд)
            
            while (attempts < maxAttempts) {
                delay(5000) // Проверяем каждые 5 секунд
                status = railwayClient.getDeploymentStatus(deployment.id)
                
                when (status) {
                    DeploymentStatus.SUCCESS -> {
                        println("✅ Деплой успешно завершен!")
                        return DeploymentResult.success(deployment.id, "Деплой успешно завершен")
                    }
                    DeploymentStatus.FAILED -> {
                        println("❌ Деплой завершился с ошибкой")
                        return DeploymentResult.error("Деплой завершился с ошибкой")
                    }
                    DeploymentStatus.IN_PROGRESS -> {
                        print(".")
                        attempts++
                    }
                    DeploymentStatus.UNKNOWN, null -> {
                        println("⚠️  Не удалось определить статус деплоя")
                        attempts++
                    }
                }
            }
            
            println("\n⏱️  Деплой все еще выполняется (таймаут ожидания)")
            return DeploymentResult.success(
                deployment.id,
                "Деплой запущен, но статус не определен в течение ожидания"
            )
        }
        
        return DeploymentResult.success(deployment.id, "Деплой запущен")
    }
}

data class DeploymentResult(
    val success: Boolean,
    val deploymentId: String?,
    val message: String
) {
    companion object {
        fun success(deploymentId: String, message: String): DeploymentResult {
            return DeploymentResult(true, deploymentId, message)
        }
        
        fun error(message: String): DeploymentResult {
            return DeploymentResult(false, null, message)
        }
    }
}
