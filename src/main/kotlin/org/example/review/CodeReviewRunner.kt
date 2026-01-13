package org.example.review

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.example.config.AppConfig
import org.example.embedding.DocumentIndexStorage
import org.example.embedding.EmbeddingClient
import org.example.embedding.RagService
import java.io.File

/**
 * Точка входа для запуска AI Code Review в CI.
 * 
 * Использование:
 * gradlew runCodeReview --args="--pr=123 --repo=owner/repo --base-sha=abc --head-sha=def --output=review.json"
 * 
 * Переменные окружения:
 * - OPENROUTER_API_KEY: API ключ OpenRouter
 * - GITHUB_TOKEN: Токен для GitHub API
 */
fun main(args: Array<String>) = runBlocking {
    println("🤖 AI Code Review Pipeline")
    println("═".repeat(60))
    
    // Парсим аргументы
    val params = parseArgs(args)
    
    val prNumber = params["pr"]?.toIntOrNull()
    val repo = params["repo"]
    val baseSha = params["base-sha"]
    val headSha = params["head-sha"]
    val outputFile = params["output"] ?: "review-output.json"
    
    // Валидация
    if (prNumber == null || repo == null) {
        printUsage()
        System.exit(1)
        return@runBlocking
    }
    
    val (owner, repoName) = repo.split("/").let { 
        if (it.size == 2) it[0] to it[1] 
        else {
            println("❌ Invalid repo format. Expected: owner/repo")
            System.exit(1)
            return@runBlocking
        }
    }
    
    println("📋 PR: #$prNumber in $owner/$repoName")
    println("📋 Base: ${baseSha?.take(7) ?: "N/A"} → Head: ${headSha?.take(7) ?: "N/A"}")
    println()
    
    // Загружаем API ключи
    val openRouterKey = System.getenv("OPENROUTER_API_KEY") 
        ?: try { AppConfig.loadApiKey() } catch (e: Exception) { null }
    val githubToken = System.getenv("GITHUB_TOKEN")
    
    if (openRouterKey == null) {
        println("❌ OPENROUTER_API_KEY не найден")
        System.exit(1)
        return@runBlocking
    }
    
    if (githubToken == null) {
        println("❌ GITHUB_TOKEN не найден")
        System.exit(1)
        return@runBlocking
    }
    
    val json = Json { 
        prettyPrint = true 
        encodeDefaults = true
    }
    
    try {
        // Инициализируем клиенты
        println("🔧 Инициализация...")
        val githubClient = GitHubClient(githubToken)
        
        // Опционально: RAG сервис
        val ragService = try {
            val storage = DocumentIndexStorage()
            if (storage.getAllDocuments().isNotEmpty()) {
                val embeddingClient = EmbeddingClient(openRouterKey)
                RagService(embeddingClient, storage).also {
                    println("   ✅ RAG сервис инициализирован (${storage.getAllDocuments().size} документов)")
                }
            } else {
                println("   ⚠️ RAG индекс пуст, продолжаем без контекста документации")
                null
            }
        } catch (e: Exception) {
            println("   ⚠️ RAG недоступен: ${e.message}")
            null
        }
        
        val reviewService = CodeReviewService(openRouterKey, ragService)
        
        // Получаем данные PR
        println("\n📥 Получение данных PR...")
        val prInfo = githubClient.getPullRequest(owner, repoName, prNumber)
        println("   ✅ PR: ${prInfo.title}")
        
        val diff = githubClient.getPullRequestDiff(owner, repoName, prNumber)
        println("   ✅ Diff: ${diff.length} символов")
        
        val files = githubClient.getPullRequestFiles(owner, repoName, prNumber)
        println("   ✅ Файлов изменено: ${files.size}")
        
        // Получаем содержимое ключевых файлов
        println("\n📄 Загрузка содержимого файлов...")
        val fileContents = mutableMapOf<String, String>()
        val relevantFiles = files
            .filter { it.status != "removed" }
            .filter { it.filename.endsWith(".kt") || it.filename.endsWith(".java") || it.filename.endsWith(".ts") || it.filename.endsWith(".js") }
            .take(5)
        
        for (file in relevantFiles) {
            val content = githubClient.getFileContent(owner, repoName, file.filename, headSha ?: prInfo.head.sha)
            if (content != null) {
                fileContents[file.filename] = content
                println("   ✅ ${file.filename}")
            }
        }
        
        // Выполняем review
        println("\n🧠 Выполнение AI Code Review...")
        val result = reviewService.reviewPullRequest(prInfo, diff, files, fileContents)
        
        // Выводим результат
        println("\n" + "═".repeat(60))
        println("📊 РЕЗУЛЬТАТ REVIEW")
        println("═".repeat(60))
        println()
        println("📝 Summary: ${result.summary}")
        println("🎯 Verdict: ${result.verdict.uppercase()}")
        println()
        
        if (result.issues.isNotEmpty()) {
            println("⚠️ Issues (${result.issues.size}):")
            result.issues.forEach { issue ->
                val emoji = when(issue.severity) {
                    "critical" -> "🚨"
                    "security" -> "🔒"
                    "performance" -> "⚡"
                    "logic" -> "⚠️"
                    "style" -> "✨"
                    else -> "📝"
                }
                println("   $emoji [${issue.severity}] ${issue.file}:${issue.line}")
                println("      ${issue.title}")
            }
            println()
        }
        
        if (result.positiveNotes.isNotEmpty()) {
            println("✨ Positive notes:")
            result.positiveNotes.forEach { note ->
                println("   - $note")
            }
            println()
        }
        
        // Сохраняем результат в файл
        val outputJson = json.encodeToString(result)
        File(outputFile).writeText(outputJson)
        println("💾 Результат сохранён в: $outputFile")
        
        // Закрываем ресурсы
        githubClient.close()
        reviewService.close()
        
        println("\n✅ Code Review завершён!")
        
    } catch (e: Exception) {
        println("\n❌ Ошибка: ${e.message}")
        e.printStackTrace()
        
        // Сохраняем ошибку в output файл
        val errorResult = CodeReviewResult(
            summary = "Review failed: ${e.message}",
            verdict = "comment",
            issues = emptyList(),
            positiveNotes = emptyList()
        )
        File(outputFile).writeText(json.encodeToString(errorResult))
        
        System.exit(1)
    }
}

private fun parseArgs(args: Array<String>): Map<String, String> {
    return args.mapNotNull { arg ->
        if (arg.startsWith("--")) {
            val parts = arg.substring(2).split("=", limit = 2)
            if (parts.size == 2) parts[0] to parts[1] else null
        } else null
    }.toMap()
}

private fun printUsage() {
    println("""
        Usage: gradlew runCodeReview --args="OPTIONS"
        
        Required options:
          --pr=NUMBER          PR number
          --repo=OWNER/REPO    Repository (e.g., user/project)
        
        Optional options:
          --base-sha=SHA       Base commit SHA
          --head-sha=SHA       Head commit SHA
          --output=FILE        Output file (default: review-output.json)
        
        Environment variables:
          OPENROUTER_API_KEY   OpenRouter API key
          GITHUB_TOKEN         GitHub token with PR read access
        
        Example:
          gradlew runCodeReview --args="--pr=123 --repo=owner/repo --output=review.json"
    """.trimIndent())
}
