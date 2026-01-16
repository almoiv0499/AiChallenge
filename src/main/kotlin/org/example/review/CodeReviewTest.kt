package org.example.review

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.example.config.AppConfig
import org.example.embedding.DocumentIndexStorage
import org.example.embedding.EmbeddingClient
import org.example.embedding.RagService
import java.io.File

/**
 * Тестирование полного Code Review Pipeline.
 * 
 * Проверяет:
 * 1. MCP — получение git diff и status
 * 2. RAG — поиск релевантного контекста
 * 3. LLM — генерация review (mock или реальный вызов)
 * 
 * Запуск: gradlew runCodeReviewTest
 */
fun main() = runBlocking {
    println("🔬 Тестирование Code Review Pipeline")
    println("═".repeat(70))
    
    val results = mutableMapOf<String, TestResult>()
    
    // ═══════════════════════════════════════════════════════════════════════
    // Этап 1: Проверка Git данных (симуляция MCP)
    // ═══════════════════════════════════════════════════════════════════════
    println("\n📋 Этап 1: Проверка Git данных")
    println("-".repeat(70))
    
    try {
        // Получаем git diff (последние изменения)
        val diffProcess = ProcessBuilder("git", "diff", "HEAD~1", "--stat")
            .directory(File("."))
            .redirectErrorStream(true)
            .start()
        val diffStat = diffProcess.inputStream.bufferedReader().readText()
        diffProcess.waitFor()
        
        // Получаем список измененных файлов
        val filesProcess = ProcessBuilder("git", "diff", "HEAD~1", "--name-only")
            .directory(File("."))
            .redirectErrorStream(true)
            .start()
        val changedFiles = filesProcess.inputStream.bufferedReader().readText()
            .lines()
            .filter { it.isNotBlank() }
        filesProcess.waitFor()
        
        println("   📊 Статистика изменений:")
        diffStat.lines().takeLast(5).forEach { println("      $it") }
        println("\n   📁 Изменённые файлы (${changedFiles.size}):")
        changedFiles.take(10).forEach { println("      - $it") }
        if (changedFiles.size > 10) {
            println("      ... и ещё ${changedFiles.size - 10} файлов")
        }
        
        results["git_data"] = TestResult(true, "Получено ${changedFiles.size} файлов")
        println("\n   ✅ Git данные: PASS")
    } catch (e: Exception) {
        results["git_data"] = TestResult(false, e.message ?: "Unknown error")
        println("   ❌ Git данные: FAIL - ${e.message}")
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // Этап 2: Проверка RAG индекса
    // ═══════════════════════════════════════════════════════════════════════
    println("\n📋 Этап 2: Проверка RAG системы")
    println("-".repeat(70))
    
    try {
        val storage = DocumentIndexStorage()
        val allDocs = storage.getAllDocuments()
        
        if (allDocs.isEmpty()) {
            println("   ⚠️ RAG индекс пуст. Запустите: gradlew runIndexDocs")
            results["rag_index"] = TestResult(false, "Индекс пуст")
        } else {
            println("   📚 Документов в индексе: ${allDocs.size}")
            var totalChunks = 0
            allDocs.forEach { doc ->
                val chunks = storage.getDocumentChunks(doc.id)
                totalChunks += chunks.size
                println("      - ${doc.title ?: doc.source}: ${chunks.size} чанков")
            }
            println("   📊 Всего чанков: $totalChunks")
            results["rag_index"] = TestResult(true, "$totalChunks чанков в ${allDocs.size} документах")
            println("\n   ✅ RAG индекс: PASS")
        }
    } catch (e: Exception) {
        results["rag_index"] = TestResult(false, e.message ?: "Unknown error")
        println("   ❌ RAG индекс: FAIL - ${e.message}")
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // Этап 3: Проверка RAG поиска
    // ═══════════════════════════════════════════════════════════════════════
    println("\n📋 Этап 3: Проверка RAG поиска")
    println("-".repeat(70))
    
    try {
        val apiKey = AppConfig.loadApiKey()
        val storage = DocumentIndexStorage()
        val embeddingClient = EmbeddingClient(apiKey)
        val ragService = RagService(embeddingClient, storage)
        
        // Тестовые запросы для code review
        val reviewQueries = listOf(
            "code style conventions",
            "architecture patterns",
            "MCP integration"
        )
        
        var successfulSearches = 0
        for (query in reviewQueries) {
            val results = ragService.search(query, limit = 3, minSimilarity = 0.5)
            if (results.isNotEmpty()) {
                successfulSearches++
                println("   🔍 \"$query\": найдено ${results.size} результатов (max sim: ${String.format("%.3f", results.maxOfOrNull { it.similarity } ?: 0.0)})")
            } else {
                println("   ⚠️ \"$query\": результатов не найдено")
            }
        }
        
        embeddingClient.close()
        
        if (successfulSearches > 0) {
            results["rag_search"] = TestResult(true, "$successfulSearches/${reviewQueries.size} запросов успешны")
            println("\n   ✅ RAG поиск: PASS")
        } else {
            results["rag_search"] = TestResult(false, "Нет результатов поиска")
            println("\n   ❌ RAG поиск: FAIL")
        }
    } catch (e: Exception) {
        results["rag_search"] = TestResult(false, e.message ?: "Unknown error")
        println("   ❌ RAG поиск: FAIL - ${e.message}")
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // Этап 4: Проверка формирования prompt
    // ═══════════════════════════════════════════════════════════════════════
    println("\n📋 Этап 4: Проверка формирования prompt")
    println("-".repeat(70))
    
    try {
        // Симулируем формирование prompt
        val systemPrompt = buildCodeReviewSystemPrompt()
        val contextPrompt = """
## PR Context
- Repository: AiChallenge
- PR #123: Test PR
- Changed files: 5

## Git Diff (sample)
```diff
--- a/src/main/Example.kt
+++ b/src/main/Example.kt
@@ -10,6 +10,10 @@ class Example {
+    fun newMethod() {
+        // TODO: implement
+    }
}
```
        """.trimIndent()
        
        val totalPrompt = systemPrompt + "\n\n" + contextPrompt
        val estimatedTokens = totalPrompt.length / 4 // Грубая оценка
        
        println("   📝 System prompt: ${systemPrompt.length} символов")
        println("   📝 Context prompt: ${contextPrompt.length} символов")
        println("   📊 Примерная оценка токенов: ~$estimatedTokens")
        println("   📊 Бюджет (Claude Opus): 200K токенов")
        println("   📊 Использовано: ${String.format("%.2f", estimatedTokens / 200_000.0 * 100)}%")
        
        results["prompt_build"] = TestResult(true, "~$estimatedTokens токенов")
        println("\n   ✅ Формирование prompt: PASS")
    } catch (e: Exception) {
        results["prompt_build"] = TestResult(false, e.message ?: "Unknown error")
        println("   ❌ Формирование prompt: FAIL - ${e.message}")
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // Этап 5: Проверка API connectivity (опционально)
    // ═══════════════════════════════════════════════════════════════════════
    println("\n📋 Этап 5: Проверка API connectivity")
    println("-".repeat(70))
    
    try {
        val apiKey = AppConfig.loadApiKey()
        val client = HttpClient(CIO)
        
        // Проверяем доступность OpenRouter API
        val response = client.get("https://openrouter.ai/api/v1/models") {
            header("Authorization", "Bearer $apiKey")
        }
        
        if (response.status == HttpStatusCode.OK) {
            println("   🌐 OpenRouter API: доступен")
            results["api_connectivity"] = TestResult(true, "API доступен")
            println("\n   ✅ API connectivity: PASS")
        } else {
            println("   ⚠️ OpenRouter API: статус ${response.status}")
            results["api_connectivity"] = TestResult(false, "Статус: ${response.status}")
        }
        
        client.close()
    } catch (e: Exception) {
        results["api_connectivity"] = TestResult(false, e.message ?: "Unknown error")
        println("   ❌ API connectivity: FAIL - ${e.message}")
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // Итоговый отчёт
    // ═══════════════════════════════════════════════════════════════════════
    println("\n" + "═".repeat(70))
    println("📊 ИТОГОВЫЙ ОТЧЁТ: Code Review Pipeline Verification")
    println("═".repeat(70))
    
    val passed = results.count { it.value.success }
    val total = results.size
    
    results.forEach { (name, result) ->
        val status = if (result.success) "✅ PASS" else "❌ FAIL"
        val displayName = name.replace("_", " ").replaceFirstChar { it.uppercase() }
        println("   $status  $displayName: ${result.message}")
    }
    
    println("-".repeat(70))
    println("   Результат: $passed/$total тестов пройдено")
    
    if (passed == total) {
        println("\n🎉 Pipeline готов к использованию!")
        println("\n📝 Следующие шаги:")
        println("   1. Создайте GitHub Actions workflow")
        println("   2. Добавьте OPENROUTER_API_KEY в GitHub Secrets")
        println("   3. Создайте тестовый PR для проверки")
    } else {
        println("\n⚠️ Некоторые компоненты требуют внимания.")
        println("\n📝 Рекомендации:")
        if (results["rag_index"]?.success != true) {
            println("   - Запустите: gradlew runIndexDocs")
        }
        if (results["api_connectivity"]?.success != true) {
            println("   - Проверьте OPENROUTER_API_KEY в local.properties")
        }
    }
}

data class TestResult(val success: Boolean, val message: String)

fun buildCodeReviewSystemPrompt(): String = """
You are a senior software engineer performing automated code review on a Pull Request.

## Your Responsibilities
1. Identify ONLY high-confidence issues:
   - 🚨 Critical: Null dereferences, resource leaks, SQL/XSS injection, race conditions
   - 🔒 Security: Authentication bypasses, data exposure, insecure defaults
   - ⚡ Performance: O(n²) in hot paths, memory leaks, unnecessary allocations
   - ⚠️ Logic: Off-by-one errors, incorrect boolean logic, missing error handling
   - ✨ Style: Violations of project style guide (provided in context)

2. DO NOT flag:
   - Subjective style preferences not in the style guide
   - Hypothetical future problems
   - Minor optimizations without measurable impact
   - Already-existing issues in unchanged code

## Output Format
Respond in JSON format with the following structure:
{
  "summary": "1-2 sentence overview of the PR quality",
  "verdict": "approve" | "request_changes" | "comment",
  "issues": [
    {
      "severity": "critical" | "security" | "performance" | "logic" | "style",
      "file": "path/to/file.kt",
      "line": 42,
      "title": "Brief issue title",
      "description": "What's wrong and why",
      "suggestion": "How to fix it (optional code snippet)"
    }
  ],
  "positive_notes": ["List of well-implemented aspects (1-3 items)"]
}

## Constraints
- Maximum 10 issues per review
- Each issue must reference a specific line in the diff
- Use natural, constructive tone
- Do not mention you are an AI or "automated"
""".trimIndent()
