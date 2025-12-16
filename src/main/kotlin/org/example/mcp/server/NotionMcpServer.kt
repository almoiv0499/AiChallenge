package org.example.mcp.server

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.example.mcp.JsonRpcRequest
import org.example.mcp.JsonRpcResponse
import org.example.mcp.McpTool
import org.example.mcp.ToolsListResult
import org.example.notion.NotionClient

class NotionMcpServer(private val notionClient: NotionClient) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    fun configureMcpServer(application: Application) {
        application.install(ContentNegotiation) {
            json(this@NotionMcpServer.json)
        }
        application.install(CORS) {
            anyHost()
            allowHeader("Content-Type")
        }
        application.routing {
            post("/mcp") {
                try {
                    val request = call.receive<JsonRpcRequest>()
                    val response = handleRequest(request)
                    call.respond(response)
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        JsonRpcResponse(
                            id = null,
                            error = org.example.mcp.JsonRpcError(
                                code = -32700,
                                message = "Parse error: ${e.message}"
                            )
                        )
                    )
                }
            }
        }
    }

    private suspend fun handleRequest(request: JsonRpcRequest): JsonRpcResponse {
        return withContext(Dispatchers.Default) {
            when (request.method) {
                "initialize" -> handleInitialize(request)
                "notifications/initialized" -> JsonRpcResponse(id = request.id, result = null)
                "tools/list" -> handleToolsList(request)
                "tools/call" -> handleToolCall(request)
                else -> JsonRpcResponse(
                    id = request.id,
                    error = org.example.mcp.JsonRpcError(
                        code = -32601,
                        message = "Method not found: ${request.method}"
                    )
                )
            }
        }
    }

    private fun handleInitialize(request: JsonRpcRequest): JsonRpcResponse {
        val result = buildJsonObject {
            put("protocolVersion", "2025-06-18")
            put("capabilities", buildJsonObject {
                put("tools", buildJsonObject {
                    put("listChanged", true)
                })
            })
            put("serverInfo", buildJsonObject {
                put("name", "NotionMcpServer")
                put("version", "1.0.0")
            })
        }
        return JsonRpcResponse(id = request.id, result = result)
    }

    private fun handleToolsList(request: JsonRpcRequest): JsonRpcResponse {
        val tools = listOf(
            createGetPageTool()
        )
        val toolsListResult = ToolsListResult(tools = tools)
        val result = json.encodeToJsonElement(ToolsListResult.serializer(), toolsListResult)
        return JsonRpcResponse(id = request.id, result = result)
    }

    private suspend fun handleToolCall(request: JsonRpcRequest): JsonRpcResponse {
        val paramsElement = request.params
        val params = when {
            paramsElement == null -> {
                return JsonRpcResponse(
                    id = request.id,
                    error = org.example.mcp.JsonRpcError(code = -32602, message = "Invalid params: params is null")
                )
            }
            paramsElement is JsonNull -> {
                return JsonRpcResponse(
                    id = request.id,
                    error = org.example.mcp.JsonRpcError(code = -32602, message = "Invalid params: params is JsonNull")
                )
            }
            paramsElement is JsonObject -> paramsElement
            else -> {
                return JsonRpcResponse(
                    id = request.id,
                    error = org.example.mcp.JsonRpcError(code = -32602, message = "Invalid params: params is not JsonObject, type: ${paramsElement.javaClass.simpleName}")
                )
            }
        }
        val toolNameElement = params["name"]
        val toolName = when {
            toolNameElement == null || toolNameElement is JsonNull -> {
                return JsonRpcResponse(
                    id = request.id,
                    error = org.example.mcp.JsonRpcError(code = -32602, message = "Tool name is required")
                )
            }
            else -> {
                val name = toolNameElement.jsonPrimitive?.content
                name ?: return JsonRpcResponse(
                    id = request.id,
                    error = org.example.mcp.JsonRpcError(code = -32602, message = "Tool name is required")
                )
            }
        }
        val argumentsElement = params["arguments"]
        val arguments = when {
            argumentsElement == null || argumentsElement is JsonNull -> buildJsonObject {}
            argumentsElement is JsonObject -> argumentsElement
            else -> buildJsonObject {}
        }
        val result = when (toolName) {
            "get_notion_page" -> executeGetPage(arguments)
            else -> buildJsonObject {
                put("isError", true)
                put("content", buildJsonArray {
                    add(buildJsonObject {
                        put("type", "text")
                        put("text", "Unknown tool: $toolName")
                    })
                })
            }
        }
        return JsonRpcResponse(id = request.id, result = result)
    }

    private fun createGetPageTool(): McpTool {
        return McpTool(
            name = "get_notion_page",
            description = "Получить информацию о странице из Notion по её ID или URL. Используй этот инструмент ВСЕГДА, когда пользователь просит получить данные о странице Notion. Инструмент автоматически извлекает ID из URL. Возвращает полную информацию о странице включая название, свойства, URL, даты создания и изменения.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("page_id", buildJsonObject {
                        put("type", "string")
                        put("description", "ID страницы Notion в формате UUID (например: be633bf1-dfa0-436d-b259-571129a590e5) или полный URL страницы Notion. Инструмент автоматически извлечет ID из URL.")
                    })
                })
                put("required", buildJsonArray {
                    add("page_id")
                })
            }
        )
    }

    private suspend fun executeGetPage(arguments: JsonObject): JsonElement {
        val pageIdOrUrl = arguments["page_id"]?.jsonPrimitive?.content
            ?: return buildJsonObject {
                put("isError", true)
                put("content", buildJsonArray {
                    add(buildJsonObject {
                        put("type", "text")
                        put("text", "page_id is required")
                    })
                })
            }
        val pageId = extractPageIdFromUrl(pageIdOrUrl)
        return try {
            val page = notionClient.getPage(pageId)
            buildJsonObject {
                put("isError", false)
                put("content", buildJsonArray {
                    add(buildJsonObject {
                        put("type", "text")
                        put("text", json.encodeToString(org.example.notion.NotionPage.serializer(), page))
                    })
                })
            }
        } catch (e: org.example.notion.NotionException) {
            buildJsonObject {
                put("isError", true)
                put("content", buildJsonArray {
                    add(buildJsonObject {
                        put("type", "text")
                        put("text", e.message ?: "Ошибка при получении страницы из Notion")
                    })
                })
            }
        } catch (e: Exception) {
            buildJsonObject {
                put("isError", true)
                put("content", buildJsonArray {
                    add(buildJsonObject {
                        put("type", "text")
                        put("text", "Ошибка при получении страницы: ${e.message ?: "Неизвестная ошибка"}")
                    })
                })
            }
        }
    }

    private fun extractPageIdFromUrl(pageIdOrUrl: String): String {
        val trimmed = pageIdOrUrl.trim()
        val urlPattern = Regex("""notion\.(so|site)/[^/?\s]+-([a-f0-9]{32})(?:[?/]|$)""", RegexOption.IGNORE_CASE)
        val match = urlPattern.find(trimmed)
        if (match != null) {
            val pageIdWithoutDashes = match.groupValues[2]
            return formatPageId(pageIdWithoutDashes)
        }
        val urlPattern2 = Regex("""notion\.(so|site)/[^/?\s]+-([a-f0-9]{32})""", RegexOption.IGNORE_CASE)
        val match2 = urlPattern2.find(trimmed)
        if (match2 != null) {
            val pageIdWithoutDashes2 = match2.groupValues[2]
            return formatPageId(pageIdWithoutDashes2)
        }
        val directIdPattern = Regex("""([a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12})""", RegexOption.IGNORE_CASE)
        val directMatch = directIdPattern.find(trimmed)
        if (directMatch != null) {
            return directMatch.groupValues[1]
        }
        val idWithoutDashesPattern = Regex("""([a-f0-9]{32})""", RegexOption.IGNORE_CASE)
        val idMatch = idWithoutDashesPattern.find(trimmed)
        if (idMatch != null) {
            return formatPageId(idMatch.groupValues[1])
        }
        return trimmed
    }

    private fun formatPageId(pageId: String): String {
        val cleanId = pageId.replace("-", "")
        return if (cleanId.length == 32) {
            "${cleanId.substring(0, 8)}-${cleanId.substring(8, 12)}-${cleanId.substring(12, 16)}-${cleanId.substring(16, 20)}-${cleanId.substring(20, 32)}"
        } else {
            pageId
        }
    }
}
