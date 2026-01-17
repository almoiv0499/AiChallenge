package org.example.deployment

import org.example.config.AppConfig
import kotlinx.coroutines.runBlocking

/**
 * Главная функция для запуска деплоя приложения на Railway
 * 
 * Использование:
 *   java -jar app.jar --railway-token=xxx --project-id=xxx --service-id=xxx
 * 
 * Или через переменные окружения:
 *   RAILWAY_TOKEN=xxx RAILWAY_PROJECT_ID=xxx RAILWAY_SERVICE_ID=xxx java -jar app.jar
 */
fun main(args: Array<String>) = runBlocking {
    println("""
        ╔══════════════════════════════════════════════════════════════╗
        ║          🚀 Railway Deployment Tool 🚀                       ║
        ╚══════════════════════════════════════════════════════════════╝
    """.trimIndent())
    
    // Парсинг аргументов командной строки
    val argsMap = args.associate {
        val parts = it.split("=", limit = 2)
        if (parts.size == 2) {
            parts[0].removePrefix("--") to parts[1]
        } else {
            null to null
        }
    }.filterKeys { it != null }
    
    // Загрузка конфигурации
    val railwayToken = argsMap["railway-token"]
        ?: System.getenv("RAILWAY_TOKEN")
        ?: loadFromProperties("RAILWAY_TOKEN")
        ?: throw IllegalArgumentException("RAILWAY_TOKEN не указан")
    
    val projectId = argsMap["project-id"]
        ?: System.getenv("RAILWAY_PROJECT_ID")
        ?: loadFromProperties("RAILWAY_PROJECT_ID")
        ?: throw IllegalArgumentException("RAILWAY_PROJECT_ID не указан")
    
    val serviceId = argsMap["service-id"]
        ?: System.getenv("RAILWAY_SERVICE_ID")
        ?: loadFromProperties("RAILWAY_SERVICE_ID")
        ?: throw IllegalArgumentException("RAILWAY_SERVICE_ID не указан")
    
    println("Конфигурация:")
    println("  Project ID: $projectId")
    println("  Service ID: $serviceId")
    println("  Token: ${railwayToken.take(10)}...")
    println()
    
    // Создание клиента и сервиса
    val railwayClient = RailwayClient(railwayToken)
    val deploymentService = DeploymentService(railwayClient, projectId, serviceId)
    
    try {
        // Подготовка переменных окружения для деплоя
        val envVars = mutableMapOf<String, String>()
        
        // Загружаем переменные из окружения, если они есть
        System.getenv("OPENROUTER_API_KEY")?.let {
            envVars["OPENROUTER_API_KEY"] = it
        }
        System.getenv("NOTION_API_KEY")?.let {
            envVars["NOTION_API_KEY"] = it
        }
        System.getenv("WEATHER_API_KEY")?.let {
            envVars["WEATHER_API_KEY"] = it
        }
        
        // Выполнение деплоя
        val result = deploymentService.deploy(
            environmentVariables = envVars,
            waitForCompletion = true
        )
        
        println()
        if (result.success) {
            println("✅ Деплой выполнен успешно!")
            println("   Deployment ID: ${result.deploymentId}")
            println("   Сообщение: ${result.message}")
            System.exit(0)
        } else {
            println("❌ Ошибка при деплое:")
            println("   ${result.message}")
            System.exit(1)
        }
    } catch (e: Exception) {
        println("❌ Критическая ошибка: ${e.message}")
        e.printStackTrace()
        System.exit(1)
    } finally {
        railwayClient.close()
    }
}

private fun loadFromProperties(key: String): String? {
    return try {
        val properties = java.util.Properties()
        val file = java.io.File("local.properties")
        if (file.exists()) {
            file.inputStream().use { properties.load(it) }
            properties.getProperty(key)
        } else {
            null
        }
    } catch (e: Exception) {
        null
    }
}
