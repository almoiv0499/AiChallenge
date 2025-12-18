package org.example.notion

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class NotionClient(private val apiKey: String) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false // Don't encode null/default values
        explicitNulls = false // Omit null values from JSON
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
        defaultRequest {
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            header("Notion-Version", "2022-06-28")
            contentType(ContentType.Application.Json)
        }
    }
    
    private val baseUrl = "https://api.notion.com/v1"

    /**
     * Retrieve database metadata
     * 
     * GET /v1/databases/{database_id}
     * Returns the database object with its properties schema.
     */
    suspend fun getDatabase(databaseId: String): NotionDatabase {
        return withContext(Dispatchers.IO) {
            val formattedDatabaseId = formatIdForApi(databaseId)
            val response = httpClient.get("$baseUrl/databases/$formattedDatabaseId")
            
            if (!response.status.isSuccess()) {
                val errorBody = response.bodyAsText()
                val errorMessage = try {
                    val errorResponse = json.decodeFromString(NotionErrorResponse.serializer(), errorBody)
                    when (errorResponse.code) {
                        "object_not_found" -> {
                            "Database not found or not accessible.\n" +
                            "Requested ID: $formattedDatabaseId\n" +
                            "Ensure:\n" +
                            "1. The database exists in your workspace\n" +
                            "2. The database is shared with your integration (open database in Notion → three dots → Connections → add your integration)\n" +
                            "3. The database ID is correct"
                        }
                        "unauthorized" -> "Unauthorized. Check your API key."
                        "restricted_resource" -> "Access to resource is restricted. Ensure the integration has necessary permissions."
                        else -> "${errorResponse.message} (code: ${errorResponse.code})"
                    }
                } catch (e: Exception) {
                    "HTTP ${response.status.value}: $errorBody"
                }
                throw NotionException(errorMessage)
            }
            
            val responseText = response.bodyAsText()
            json.decodeFromString(NotionDatabase.serializer(), responseText)
        }
    }

    /**
     * Query a database
     * 
     * POST /v1/databases/{database_id}/query
     * Returns a list of pages from the database.
     * 
     * Example request body (empty - returns all pages):
     * {}
     * 
     * Usage:
     * ```kotlin
     * // Empty request - returns all pages
     * val response = client.queryDatabase(databaseId)
     * 
     * // Or explicitly
     * val response = client.queryDatabase(databaseId, DatabaseQueryRequest())
     * ```
     * 
     * @param databaseId The database ID to query
     * @param request Optional query request with filters, sorts, and pagination.
     *                Defaults to empty request {} which returns all pages.
     * @return DatabaseQueryResponse containing the list of pages
     */
    suspend fun queryDatabase(
        databaseId: String,
        request: DatabaseQueryRequest = DatabaseQueryRequest()
    ): DatabaseQueryResponse {
        return withContext(Dispatchers.IO) {
            val formattedDatabaseId = formatIdForApi(databaseId)
            val response = httpClient.post("$baseUrl/databases/$formattedDatabaseId/query") {
                setBody(request)
            }

            if (!response.status.isSuccess()) {
                val errorBody = response.bodyAsText()
                val errorMessage = try {
                    val errorResponse = json.decodeFromString(NotionErrorResponse.serializer(), errorBody)
                    when (errorResponse.code) {
                        "object_not_found" -> {
                            "Database not found or not accessible.\n" +
                            "Requested ID: $formattedDatabaseId\n" +
                            "Ensure:\n" +
                            "1. The database exists in your workspace\n" +
                            "2. The database is shared with your integration\n" +
                            "3. The database ID is correct"
                        }
                        "unauthorized" -> "Unauthorized. Check your API key."
                        "restricted_resource" -> "Access to resource is restricted."
                        else -> "${errorResponse.message} (code: ${errorResponse.code})"
                    }
                } catch (e: Exception) {
                    "HTTP ${response.status.value}: $errorBody"
                }
                throw NotionException(errorMessage)
            }
            
            val responseText = response.bodyAsText()
            json.decodeFromString(DatabaseQueryResponse.serializer(), responseText)
        }
    }

    suspend fun getPage(pageId: String): NotionPage {
        return withContext(Dispatchers.IO) {
            val formattedPageId = formatIdForApi(pageId)
            val response = httpClient.get("$baseUrl/pages/$formattedPageId")
            if (!response.status.isSuccess()) {
                val errorBody = response.bodyAsText()
                val errorMessage = try {
                    val errorResponse = json.decodeFromString(NotionErrorResponse.serializer(), errorBody)
                    when (errorResponse.code) {
                        "object_not_found" -> {
                            "Страница не найдена или недоступна для интеграции.\n" +
                            "Запрошенный ID: $formattedPageId\n" +
                            "Убедитесь, что:\n" +
                            "1. Страница существует в вашем workspace\n" +
                            "2. Страница доступна для вашей интеграции (откройте страницу в Notion → три точки → Connections → добавьте вашу интеграцию)\n" +
                            "3. ID страницы указан правильно (проверьте URL страницы)"
                        }
                        "unauthorized" -> "Не авторизован. Проверьте правильность API ключа."
                        "restricted_resource" -> "Доступ к ресурсу ограничен. Убедитесь, что интеграция имеет необходимые права."
                        else -> "${errorResponse.message} (код: ${errorResponse.code})"
                    }
                } catch (e: Exception) {
                    "HTTP ${response.status.value}: $errorBody"
                }
                throw NotionException(errorMessage)
            }
            val responseText = response.bodyAsText()
            json.decodeFromString(NotionPage.serializer(), responseText)
        }
    }

    /**
     * Update a page
     * 
     * PATCH /v1/pages/{page_id}
     * Updates the properties, icon, cover, or archived status of a page.
     * Only provided fields in the request will be updated (partial update).
     * 
     * @param pageId The ID of the page to update
     * @param request The update request containing properties, icon, cover, or archived status
     * @return The updated page object
     * 
     * @see https://developers.notion.com/reference/patch-page
     * 
     * Example usage:
     * ```kotlin
     * import kotlinx.serialization.json.*
     * 
     * // Update a select property
     * val selectUpdate = json.encodeToJsonElement(SelectPropertyUpdate(
     *     select = NotionSelect(name = "In Progress")
     * )) as JsonObject
     * val updateRequest = PageUpdateRequest(
     *     properties = mapOf("Status" to selectUpdate)
     * )
     * val updatedPage = client.updatePage(pageId, updateRequest)
     * 
     * // Update a date property
     * val dateUpdate = json.encodeToJsonElement(DatePropertyUpdate(
     *     date = NotionDate(start = "2025-12-31")
     * )) as JsonObject
     * val dateRequest = PageUpdateRequest(
     *     properties = mapOf("Due Date" to dateUpdate)
     * )
     * val updatedPageWithDate = client.updatePage(pageId, dateRequest)
     * 
     * // Archive a page
     * val archiveRequest = PageUpdateRequest(archived = true)
     * val archivedPage = client.updatePage(pageId, archiveRequest)
     * ```
     */
    suspend fun updatePage(pageId: String, request: PageUpdateRequest): NotionPage {
        return withContext(Dispatchers.IO) {
            val formattedPageId = formatIdForApi(pageId)
            val response = httpClient.patch("$baseUrl/pages/$formattedPageId") {
                setBody(request)
            }
            
            if (!response.status.isSuccess()) {
                val errorBody = response.bodyAsText()
                val errorMessage = try {
                    val errorResponse = json.decodeFromString(NotionErrorResponse.serializer(), errorBody)
                    when (errorResponse.code) {
                        "object_not_found" -> {
                            "Page not found or not accessible.\n" +
                            "Requested ID: $formattedPageId\n" +
                            "Ensure:\n" +
                            "1. The page exists in your workspace\n" +
                            "2. The page is shared with your integration\n" +
                            "3. The page ID is correct"
                        }
                        "unauthorized" -> "Unauthorized. Check your API key."
                        "restricted_resource" -> "Access to resource is restricted. Ensure the integration has necessary permissions."
                        "validation_error" -> "Validation error: ${errorResponse.message}"
                        "rate_limited" -> "Rate limit exceeded. Please try again later."
                        else -> "${errorResponse.message} (code: ${errorResponse.code})"
                    }
                } catch (e: Exception) {
                    "HTTP ${response.status.value}: $errorBody"
                }
                throw NotionException(errorMessage)
            }
            
            val responseText = response.bodyAsText()
            json.decodeFromString(NotionPage.serializer(), responseText)
        }
    }

    /**
     * Update a block
     * 
     * PATCH /v1/blocks/{block_id}
     * Updates the content of a block. Only provided fields will be updated.
     * 
     * @param blockId The ID of the block to update
     * @param request The update request containing block content and optional archived status
     * @return The updated block object (as JsonObject, since block structure varies by type)
     * 
     * @see https://developers.notion.com/reference/update-a-block
     * 
     * Example usage:
     * ```kotlin
     * // Update a paragraph block
     * val updateRequest = BlockUpdateRequest(
     *     paragraph = ParagraphBlockUpdate(
     *         richText = listOf(
     *             NotionRichText(
     *                 type = "text",
     *                 text = NotionText(content = "Updated paragraph text"),
     *                 plainText = "Updated paragraph text"
     *             )
     *         )
     *     )
     * )
     * val updatedBlock = client.updateBlock(blockId, updateRequest)
     * 
     * // Update a to-do block and mark as checked
     * val toDoRequest = BlockUpdateRequest(
     *     toDo = ToDoBlockUpdate(
     *         richText = listOf(
     *             NotionRichText(
     *                 type = "text",
     *                 text = NotionText(content = "Completed task"),
     *                 plainText = "Completed task"
     *             )
     *         ),
     *         checked = true
     *     )
     * )
     * val updatedToDo = client.updateBlock(blockId, toDoRequest)
     * 
     * // Archive a block
     * val archiveRequest = BlockUpdateRequest(archived = true)
     * val archivedBlock = client.updateBlock(blockId, archiveRequest)
     * ```
     */
    suspend fun updateBlock(blockId: String, request: BlockUpdateRequest): JsonObject {
        return withContext(Dispatchers.IO) {
            val formattedBlockId = formatIdForApi(blockId)
            val response = httpClient.patch("$baseUrl/blocks/$formattedBlockId") {
                setBody(request)
            }
            
            if (!response.status.isSuccess()) {
                val errorBody = response.bodyAsText()
                val errorMessage = try {
                    val errorResponse = json.decodeFromString(NotionErrorResponse.serializer(), errorBody)
                    when (errorResponse.code) {
                        "object_not_found" -> {
                            "Block not found or not accessible.\n" +
                            "Requested ID: $formattedBlockId\n" +
                            "Ensure:\n" +
                            "1. The block exists in your workspace\n" +
                            "2. The block is accessible to your integration\n" +
                            "3. The block ID is correct"
                        }
                        "unauthorized" -> "Unauthorized. Check your API key."
                        "restricted_resource" -> "Access to resource is restricted. Ensure the integration has necessary permissions."
                        "validation_error" -> "Validation error: ${errorResponse.message}"
                        "rate_limited" -> "Rate limit exceeded. Please try again later."
                        else -> "${errorResponse.message} (code: ${errorResponse.code})"
                    }
                } catch (e: Exception) {
                    "HTTP ${response.status.value}: $errorBody"
                }
                throw NotionException(errorMessage)
            }
            
            val responseText = response.bodyAsText()
            val parsedElement = json.parseToJsonElement(responseText)
            when (parsedElement) {
                is JsonObject -> parsedElement
                else -> throw NotionException("Unexpected response format: expected JsonObject, got ${parsedElement::class.simpleName}")
            }
        }
    }

    /**
     * Append block children to a page or block
     * 
     * PATCH /v1/blocks/{block_id}/children
     * Adds new blocks as children of an existing block (page or block).
     * 
     * @param blockId The ID of the parent block (page or block) to append children to
     * @param children List of block objects to append
     * @return Response containing the created blocks
     * 
     * @see https://developers.notion.com/reference/patch-block-children
     */
    suspend fun appendBlockChildren(blockId: String, children: List<JsonObject>): JsonObject {
        return withContext(Dispatchers.IO) {
            val formattedBlockId = formatIdForApi(blockId)
            val childrenArray = JsonArray(children)
            val requestBody = buildJsonObject {
                put("children", childrenArray)
            }
            
            val response = httpClient.patch("$baseUrl/blocks/$formattedBlockId/children") {
                setBody(requestBody)
            }
            
            if (!response.status.isSuccess()) {
                val errorBody = response.bodyAsText()
                val errorMessage = try {
                    val errorResponse = json.decodeFromString(NotionErrorResponse.serializer(), errorBody)
                    when (errorResponse.code) {
                        "object_not_found" -> {
                            "Block or page not found or not accessible.\n" +
                            "Requested ID: $formattedBlockId\n" +
                            "Ensure:\n" +
                            "1. The block/page exists in your workspace\n" +
                            "2. The block/page is accessible to your integration\n" +
                            "3. The block/page ID is correct"
                        }
                        "unauthorized" -> "Unauthorized. Check your API key."
                        "restricted_resource" -> "Access to resource is restricted. Ensure the integration has necessary permissions."
                        "validation_error" -> "Validation error: ${errorResponse.message}"
                        "rate_limited" -> "Rate limit exceeded. Please try again later."
                        else -> "${errorResponse.message} (code: ${errorResponse.code})"
                    }
                } catch (e: Exception) {
                    "HTTP ${response.status.value}: $errorBody"
                }
                throw NotionException(errorMessage)
            }
            
            val responseText = response.bodyAsText()
            val parsedElement = json.parseToJsonElement(responseText)
            when (parsedElement) {
                is JsonObject -> parsedElement
                else -> throw NotionException("Unexpected response format: expected JsonObject, got ${parsedElement::class.simpleName}")
            }
        }
    }

    private fun formatIdForApi(id: String): String {
        val trimmed = id.trim()
        val uuidPattern = Regex("""^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$""", RegexOption.IGNORE_CASE)
        if (uuidPattern.matches(trimmed)) {
            return trimmed
        }
        val cleanId = trimmed.replace("-", "").replace("_", "")
        return if (cleanId.length == 32) {
            "${cleanId.substring(0, 8)}-${cleanId.substring(8, 12)}-${cleanId.substring(12, 16)}-${cleanId.substring(16, 20)}-${cleanId.substring(20, 32)}"
        } else {
            trimmed
        }
    }


    fun close() {
        httpClient.close()
    }
}

class NotionException(message: String) : Exception(message)
