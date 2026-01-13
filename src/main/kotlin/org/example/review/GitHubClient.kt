package org.example.review

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Клиент для работы с GitHub API.
 * Используется для получения данных PR и публикации комментариев.
 */
class GitHubClient(
    private val token: String,
    private val baseUrl: String = "https://api.github.com"
) {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }
    
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(this@GitHubClient.json)
        }
    }
    
    /**
     * Получает diff между двумя коммитами.
     */
    suspend fun getPullRequestDiff(
        owner: String,
        repo: String,
        prNumber: Int
    ): String {
        val response = client.get("$baseUrl/repos/$owner/$repo/pulls/$prNumber") {
            header("Authorization", "Bearer $token")
            header("Accept", "application/vnd.github.v3.diff")
            header("X-GitHub-Api-Version", "2022-11-28")
        }
        return response.bodyAsText()
    }
    
    /**
     * Получает список измененных файлов в PR.
     */
    suspend fun getPullRequestFiles(
        owner: String,
        repo: String,
        prNumber: Int
    ): List<PullRequestFile> {
        val response = client.get("$baseUrl/repos/$owner/$repo/pulls/$prNumber/files") {
            header("Authorization", "Bearer $token")
            header("Accept", "application/vnd.github.v3+json")
            header("X-GitHub-Api-Version", "2022-11-28")
        }
        return json.decodeFromString(response.bodyAsText())
    }
    
    /**
     * Получает содержимое файла из репозитория.
     */
    suspend fun getFileContent(
        owner: String,
        repo: String,
        path: String,
        ref: String
    ): String? {
        return try {
            val response = client.get("$baseUrl/repos/$owner/$repo/contents/$path") {
                parameter("ref", ref)
                header("Authorization", "Bearer $token")
                header("Accept", "application/vnd.github.v3.raw")
                header("X-GitHub-Api-Version", "2022-11-28")
            }
            if (response.status == HttpStatusCode.OK) {
                response.bodyAsText()
            } else null
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Получает метаданные PR.
     */
    suspend fun getPullRequest(
        owner: String,
        repo: String,
        prNumber: Int
    ): PullRequestInfo {
        val response = client.get("$baseUrl/repos/$owner/$repo/pulls/$prNumber") {
            header("Authorization", "Bearer $token")
            header("Accept", "application/vnd.github.v3+json")
            header("X-GitHub-Api-Version", "2022-11-28")
        }
        return json.decodeFromString(response.bodyAsText())
    }
    
    /**
     * Публикует комментарий к PR.
     */
    suspend fun createPullRequestComment(
        owner: String,
        repo: String,
        prNumber: Int,
        body: String
    ): Boolean {
        val response = client.post("$baseUrl/repos/$owner/$repo/issues/$prNumber/comments") {
            header("Authorization", "Bearer $token")
            header("Accept", "application/vnd.github.v3+json")
            header("X-GitHub-Api-Version", "2022-11-28")
            contentType(ContentType.Application.Json)
            setBody("""{"body": ${json.encodeToString(kotlinx.serialization.serializer(), body)}}""")
        }
        return response.status == HttpStatusCode.Created
    }
    
    /**
     * Создаёт review с inline комментариями.
     */
    suspend fun createPullRequestReview(
        owner: String,
        repo: String,
        prNumber: Int,
        commitId: String,
        body: String,
        event: String = "COMMENT",
        comments: List<ReviewComment> = emptyList()
    ): Boolean {
        val reviewRequest = CreateReviewRequest(
            commitId = commitId,
            body = body,
            event = event,
            comments = comments
        )
        
        val response = client.post("$baseUrl/repos/$owner/$repo/pulls/$prNumber/reviews") {
            header("Authorization", "Bearer $token")
            header("Accept", "application/vnd.github.v3+json")
            header("X-GitHub-Api-Version", "2022-11-28")
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(CreateReviewRequest.serializer(), reviewRequest))
        }
        return response.status == HttpStatusCode.OK || response.status == HttpStatusCode.Created
    }
    
    fun close() {
        client.close()
    }
}

@Serializable
data class PullRequestFile(
    val sha: String,
    val filename: String,
    val status: String,
    val additions: Int = 0,
    val deletions: Int = 0,
    val changes: Int = 0,
    val patch: String? = null
)

@Serializable
data class PullRequestInfo(
    val number: Int,
    val title: String,
    val body: String? = null,
    val state: String,
    val head: GitRef,
    val base: GitRef,
    val user: GitHubUser
)

@Serializable
data class GitRef(
    val ref: String,
    val sha: String
)

@Serializable
data class GitHubUser(
    val login: String
)

@Serializable
data class CreateReviewRequest(
    val commitId: String,
    val body: String,
    val event: String,
    val comments: List<ReviewComment>
)

@Serializable
data class ReviewComment(
    val path: String,
    val position: Int? = null,
    val line: Int? = null,
    val body: String
)
