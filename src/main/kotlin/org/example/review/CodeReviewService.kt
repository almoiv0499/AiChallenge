package org.example.review

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.example.embedding.RagService

/**
 * Сервис для выполнения AI Code Review.
 * Объединяет MCP (git данные), RAG (контекст) и LLM (анализ).
 */
class CodeReviewService(
    private val openRouterApiKey: String,
    private val ragService: RagService? = null
) {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }
    
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(this@CodeReviewService.json)
        }
    }
    
    /**
     * Выполняет code review для PR.
     */
    suspend fun reviewPullRequest(
        prInfo: PullRequestInfo,
        diff: String,
        files: List<PullRequestFile>,
        fileContents: Map<String, String>
    ): CodeReviewResult {
        // 1. Получаем RAG контекст
        val ragContext = getRagContext(files, diff)
        
        // 2. Формируем prompt
        val systemPrompt = buildSystemPrompt()
        val userPrompt = buildUserPrompt(prInfo, diff, files, fileContents, ragContext)
        
        // 3. Вызываем LLM
        val llmResponse = callLLM(systemPrompt, userPrompt)
        
        // 4. Парсим результат
        return parseReviewResult(llmResponse)
    }
    
    private suspend fun getRagContext(files: List<PullRequestFile>, diff: String): String {
        if (ragService == null || !ragService.hasDocuments()) {
            return "No documentation context available."
        }
        
        val contexts = mutableListOf<String>()
        
        // Поиск по style guide
        val styleResults = ragService.search("code style conventions formatting", limit = 2, minSimilarity = 0.6)
        if (styleResults.isNotEmpty()) {
            contexts.add("## Style Guide\n" + styleResults.joinToString("\n\n") { it.text.take(500) })
        }
        
        // Поиск по архитектуре для измененных модулей
        val modules = files.map { it.filename.split("/").take(3).joinToString("/") }.distinct().take(3)
        for (module in modules) {
            val archResults = ragService.search("architecture $module design", limit = 1, minSimilarity = 0.6)
            if (archResults.isNotEmpty()) {
                contexts.add("## Architecture ($module)\n" + archResults.first().text.take(300))
            }
        }
        
        return if (contexts.isNotEmpty()) {
            contexts.joinToString("\n\n")
        } else {
            "No relevant documentation found."
        }
    }
    
    private fun buildSystemPrompt(): String = """
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
You MUST respond with ONLY valid JSON (no markdown, no explanation), following this exact structure:
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
- If no issues found, return empty issues array and verdict "approve"
""".trimIndent()
    
    private fun buildUserPrompt(
        prInfo: PullRequestInfo,
        diff: String,
        files: List<PullRequestFile>,
        fileContents: Map<String, String>,
        ragContext: String
    ): String {
        val filesStats = files.joinToString("\n") { 
            "- ${it.filename}: +${it.additions}/-${it.deletions}" 
        }
        
        // Ограничиваем размер diff
        val truncatedDiff = if (diff.length > 50000) {
            diff.take(50000) + "\n\n... [diff truncated, ${diff.length - 50000} chars omitted]"
        } else diff
        
        // Ограничиваем содержимое файлов
        val truncatedContents = fileContents.entries.take(5).joinToString("\n\n") { (path, content) ->
            val truncated = if (content.length > 5000) {
                content.take(5000) + "\n// ... [truncated]"
            } else content
            "### $path\n```\n$truncated\n```"
        }
        
        return """
## PR Context
- PR #${prInfo.number}: ${prInfo.title}
- Author: ${prInfo.user.login}
- Base: ${prInfo.base.ref} ← Head: ${prInfo.head.ref}
- Changed files: ${files.size}

## Files Changed
$filesStats

## Project Context (from documentation)
$ragContext

## Git Diff
```diff
$truncatedDiff
```

## File Contents (for context)
$truncatedContents

Now review this PR and respond with JSON only.
""".trimIndent()
    }
    
    private suspend fun callLLM(systemPrompt: String, userPrompt: String): String {
        val requestBody = buildJsonObject {
            put("model", "z-ai/glm-4.6v")  // GLM-4.6V для code review
            put("max_tokens", 4096)
            put("temperature", 0.1)
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                add(buildJsonObject {
                    put("role", "user")
                    put("content", userPrompt)
                })
            })
        }
        
        val response = client.post("https://openrouter.ai/api/v1/chat/completions") {
            header("Authorization", "Bearer $openRouterApiKey")
            header("HTTP-Referer", "https://github.com/ai-code-review")
            header("X-Title", "AI Code Review Pipeline")
            contentType(ContentType.Application.Json)
            setBody(requestBody.toString())
        }
        
        val responseText = response.bodyAsText()
        val responseJson = json.parseToJsonElement(responseText).jsonObject
        
        return responseJson["choices"]?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("message")
            ?.jsonObject?.get("content")
            ?.jsonPrimitive?.content
            ?: throw Exception("No response from LLM: $responseText")
    }
    
    private fun parseReviewResult(llmResponse: String): CodeReviewResult {
        // Извлекаем JSON из ответа (на случай если LLM добавил markdown)
        val jsonContent = extractJson(llmResponse)
        
        return try {
            json.decodeFromString<CodeReviewResult>(jsonContent)
        } catch (e: Exception) {
            // Fallback если парсинг не удался
            CodeReviewResult(
                summary = "Unable to parse review response. Raw response: ${llmResponse.take(200)}",
                verdict = "comment",
                issues = emptyList(),
                positiveNotes = emptyList()
            )
        }
    }
    
    private fun extractJson(text: String): String {
        // Ищем JSON в markdown блоке
        val jsonBlockRegex = Regex("""```(?:json)?\s*(\{[\s\S]*?\})\s*```""")
        val blockMatch = jsonBlockRegex.find(text)
        if (blockMatch != null) {
            return blockMatch.groupValues[1]
        }
        
        // Ищем просто JSON объект
        val jsonRegex = Regex("""\{[\s\S]*\}""")
        val jsonMatch = jsonRegex.find(text)
        if (jsonMatch != null) {
            return jsonMatch.value
        }
        
        return text
    }
    
    fun close() {
        client.close()
    }
}

@Serializable
data class CodeReviewResult(
    val summary: String,
    val verdict: String,
    val issues: List<CodeIssue> = emptyList(),
    @kotlinx.serialization.SerialName("positive_notes")
    val positiveNotes: List<String> = emptyList()
)

@Serializable
data class CodeIssue(
    val severity: String,
    val file: String,
    val line: Int,
    val title: String,
    val description: String,
    val suggestion: String? = null
)
