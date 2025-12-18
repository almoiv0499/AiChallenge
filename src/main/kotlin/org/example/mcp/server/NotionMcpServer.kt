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
import org.example.notion.BlockUpdateRequest
import org.example.notion.PageUpdateRequest
import org.example.notion.NotionRichText
import org.example.notion.NotionText
import org.example.notion.NotionSelect
import org.example.notion.NotionDate
import org.example.notion.SelectPropertyUpdate
import org.example.notion.DatePropertyUpdate
import org.example.notion.TitlePropertyUpdate
import org.example.notion.ParagraphBlockUpdate
import org.example.notion.ToDoBlockUpdate
import org.example.notion.HeadingBlockUpdate
import org.example.notion.ListItemBlockUpdate
import org.example.notion.ToggleBlockUpdate
import org.example.reminder.ReminderService
import org.example.reminder.SummaryGenerator

class NotionMcpServer(
    private val notionClient: NotionClient,
    private val reminderService: ReminderService? = null,
    private val defaultPageId: String? = null
) {
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
        val tools = mutableListOf(
            createGetPageTool()
        )
        if (defaultPageId != null) {
            tools.add(createUpdatePageTool())
            tools.add(createUpdateBlockTool())
            tools.add(createAppendBlockTool())
        }
        if (reminderService != null) {
            tools.add(createGetActiveTasksTool())
        }
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
            "update_notion_page" -> executeUpdatePage(arguments)
            "update_notion_block" -> executeUpdateBlock(arguments)
            "append_notion_block" -> executeAppendBlock(arguments)
            "get_active_tasks" -> executeGetActiveTasks(arguments)
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
            description = "Получить страницу Notion",
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

    private fun createGetActiveTasksTool(): McpTool {
        return McpTool(
            name = "get_active_tasks",
            description = "Получить активные задачи",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {})
                put("required", buildJsonArray {})
            }
        )
    }

    private suspend fun executeGetActiveTasks(arguments: JsonObject): JsonElement {
        if (reminderService == null) {
            return buildJsonObject {
                put("isError", true)
                put("content", buildJsonArray {
                    add(buildJsonObject {
                        put("type", "text")
                        put("text", "Reminder service is not configured. Please set NOTION_DATABASE_ID environment variable.")
                    })
                })
            }
        }
        return try {
            val tasks = reminderService.getAllTasks()
            val activeTasks = tasks.filter { it.status != "Done" }
            val completedTasks = tasks.filter { it.status == "Done" }
            val summary = SummaryGenerator.generateSummary(activeTasks, completedTasks)
            buildJsonObject {
                put("isError", false)
                put("content", buildJsonArray {
                    add(buildJsonObject {
                        put("type", "text")
                        put("text", summary)
                    })
                })
            }
        } catch (e: Exception) {
            buildJsonObject {
                put("isError", true)
                put("content", buildJsonArray {
                    add(buildJsonObject {
                        put("type", "text")
                        put("text", "Ошибка при получении активных задач: ${e.message ?: "Неизвестная ошибка"}")
                    })
                })
            }
        }
    }

    private fun createUpdatePageTool(): McpTool {
        return McpTool(
            name = "update_notion_page",
            description = "Обновить страницу Notion",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("page_id", buildJsonObject {
                        put("type", "string")
                        put("description", "ID страницы Notion (опционально). Если не указан, используется страница из конфигурации NOTION_PAGE_ID.")
                    })
                    put("properties", buildJsonObject {
                        put("type", "object")
                        put("description", "Свойства для обновления. Ключ - имя свойства, значение - объект обновления (select, date, title и т.д.)")
                    })
                    put("archived", buildJsonObject {
                        put("type", "boolean")
                        put("description", "Архивировать (true) или восстановить (false) страницу")
                    })
                })
                put("required", buildJsonArray {})
            }
        )
    }

    private suspend fun executeUpdatePage(arguments: JsonObject): JsonElement {
        val pageId = arguments["page_id"]?.jsonPrimitive?.content ?: defaultPageId
        if (pageId == null) {
            return buildJsonObject {
                put("isError", true)
                put("content", buildJsonArray {
                    add(buildJsonObject {
                        put("type", "text")
                        put("text", "page_id is required. Either provide it in arguments or set NOTION_PAGE_ID environment variable.")
                    })
                })
            }
        }

        val propertiesJson = arguments["properties"] as? JsonObject
        val archived = arguments["archived"]?.jsonPrimitive?.content?.toBoolean()

        val properties = if (propertiesJson != null) {
            try {
                propertiesJson.mapValues { (_, value) ->
                    value as? JsonObject ?: buildJsonObject {}
                }
            } catch (e: Exception) {
                return buildJsonObject {
                    put("isError", true)
                    put("content", buildJsonArray {
                        add(buildJsonObject {
                            put("type", "text")
                            put("text", "Invalid properties format: ${e.message}")
                        })
                    })
                }
            }
        } else {
            null
        }

        val updateRequest = PageUpdateRequest(
            properties = properties,
            archived = archived
        )

        return try {
            val updatedPage = notionClient.updatePage(pageId, updateRequest)
            buildJsonObject {
                put("isError", false)
                put("content", buildJsonArray {
                    add(buildJsonObject {
                        put("type", "text")
                        put("text", "Page updated successfully: ${json.encodeToString(org.example.notion.NotionPage.serializer(), updatedPage)}")
                    })
                })
            }
        } catch (e: org.example.notion.NotionException) {
            buildJsonObject {
                put("isError", true)
                put("content", buildJsonArray {
                    add(buildJsonObject {
                        put("type", "text")
                        put("text", e.message ?: "Ошибка при обновлении страницы")
                    })
                })
            }
        } catch (e: Exception) {
            buildJsonObject {
                put("isError", true)
                put("content", buildJsonArray {
                    add(buildJsonObject {
                        put("type", "text")
                        put("text", "Ошибка при обновлении страницы: ${e.message ?: "Неизвестная ошибка"}")
                    })
                })
            }
        }
    }

    private fun createUpdateBlockTool(): McpTool {
        return McpTool(
            name = "update_notion_block",
            description = "Обновить блок Notion",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("block_id", buildJsonObject {
                        put("type", "string")
                        put("description", "ID блока для обновления (обязательно)")
                    })
                    put("block_type", buildJsonObject {
                        put("type", "string")
                        put("enum", buildJsonArray {
                            add("paragraph")
                            add("heading_1")
                            add("heading_2")
                            add("heading_3")
                            add("bulleted_list_item")
                            add("numbered_list_item")
                            add("to_do")
                            add("toggle")
                        })
                        put("description", "Тип блока для обновления")
                    })
                    put("text", buildJsonObject {
                        put("type", "string")
                        put("description", "Текст для блока")
                    })
                    put("checked", buildJsonObject {
                        put("type", "boolean")
                        put("description", "Для to_do блоков: отмечен ли пункт")
                    })
                    put("archived", buildJsonObject {
                        put("type", "boolean")
                        put("description", "Архивировать (true) или восстановить (false) блок")
                    })
                })
                put("required", buildJsonArray {
                    add("block_id")
                    add("block_type")
                })
            }
        )
    }

    private suspend fun executeUpdateBlock(arguments: JsonObject): JsonElement {
        val blockId = arguments["block_id"]?.jsonPrimitive?.content
            ?: return buildJsonObject {
                put("isError", true)
                put("content", buildJsonArray {
                    add(buildJsonObject {
                        put("type", "text")
                        put("text", "block_id is required")
                    })
                })
            }

        val blockType = arguments["block_type"]?.jsonPrimitive?.content
            ?: return buildJsonObject {
                put("isError", true)
                put("content", buildJsonArray {
                    add(buildJsonObject {
                        put("type", "text")
                        put("text", "block_type is required")
                    })
                })
            }

        val text = arguments["text"]?.jsonPrimitive?.content
        val checked = arguments["checked"]?.jsonPrimitive?.content?.toBoolean()
        val archived = arguments["archived"]?.jsonPrimitive?.content?.toBoolean()

        val richText = if (text != null) {
            listOf(
                NotionRichText(
                    type = "text",
                    text = NotionText(content = text),
                    plainText = text
                )
            )
        } else {
            emptyList()
        }

        val updateRequest = when (blockType) {
            "paragraph" -> BlockUpdateRequest(
                paragraph = if (richText.isNotEmpty()) ParagraphBlockUpdate(richText) else null,
                archived = archived
            )
            "heading_1" -> BlockUpdateRequest(
                heading1 = if (richText.isNotEmpty()) HeadingBlockUpdate(richText) else null,
                archived = archived
            )
            "heading_2" -> BlockUpdateRequest(
                heading2 = if (richText.isNotEmpty()) HeadingBlockUpdate(richText) else null,
                archived = archived
            )
            "heading_3" -> BlockUpdateRequest(
                heading3 = if (richText.isNotEmpty()) HeadingBlockUpdate(richText) else null,
                archived = archived
            )
            "to_do" -> BlockUpdateRequest(
                toDo = if (richText.isNotEmpty() || checked != null) {
                    ToDoBlockUpdate(
                        richText = richText,
                        checked = checked
                    )
                } else null,
                archived = archived
            )
            "bulleted_list_item" -> BlockUpdateRequest(
                bulletedListItem = if (richText.isNotEmpty()) ListItemBlockUpdate(richText) else null,
                archived = archived
            )
            "numbered_list_item" -> BlockUpdateRequest(
                numberedListItem = if (richText.isNotEmpty()) ListItemBlockUpdate(richText) else null,
                archived = archived
            )
            "toggle" -> BlockUpdateRequest(
                toggle = if (richText.isNotEmpty()) ToggleBlockUpdate(richText) else null,
                archived = archived
            )
            else -> {
                return buildJsonObject {
                    put("isError", true)
                    put("content", buildJsonArray {
                        add(buildJsonObject {
                            put("type", "text")
                            put("text", "Unsupported block type: $blockType")
                        })
                    })
                }
            }
        }

        return try {
            val updatedBlock = notionClient.updateBlock(blockId, updateRequest)
            buildJsonObject {
                put("isError", false)
                put("content", buildJsonArray {
                    add(buildJsonObject {
                        put("type", "text")
                        put("text", "Block updated successfully: ${updatedBlock.toString()}")
                    })
                })
            }
        } catch (e: org.example.notion.NotionException) {
            buildJsonObject {
                put("isError", true)
                put("content", buildJsonArray {
                    add(buildJsonObject {
                        put("type", "text")
                        put("text", e.message ?: "Ошибка при обновлении блока")
                    })
                })
            }
        } catch (e: Exception) {
            buildJsonObject {
                put("isError", true)
                put("content", buildJsonArray {
                    add(buildJsonObject {
                        put("type", "text")
                        put("text", "Ошибка при обновлении блока: ${e.message ?: "Неизвестная ошибка"}")
                    })
                })
            }
        }
    }

    private fun createAppendBlockTool(): McpTool {
        return McpTool(
            name = "append_notion_block",
            description = "Добавить блок в Notion",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("page_id", buildJsonObject {
                        put("type", "string")
                        put("description", "ID страницы Notion (опционально). Если не указан, используется страница из конфигурации NOTION_PAGE_ID.")
                    })
                    put("text", buildJsonObject {
                        put("type", "string")
                        put("description", "Текст для добавления в виде параграфа")
                    })
                })
                put("required", buildJsonArray {
                    add("text")
                })
            }
        )
    }

    private suspend fun executeAppendBlock(arguments: JsonObject): JsonElement {
        val pageId = arguments["page_id"]?.jsonPrimitive?.content ?: defaultPageId
        if (pageId == null) {
            return buildJsonObject {
                put("isError", true)
                put("content", buildJsonArray {
                    add(buildJsonObject {
                        put("type", "text")
                        put("text", "page_id is required. Either provide it in arguments or set NOTION_PAGE_ID environment variable.")
                    })
                })
            }
        }

        val text = arguments["text"]?.jsonPrimitive?.content
            ?: return buildJsonObject {
                put("isError", true)
                put("content", buildJsonArray {
                    add(buildJsonObject {
                        put("type", "text")
                        put("text", "text is required")
                    })
                })
            }

        // Split text by lines and create a paragraph block for each line
        val lines = text.split("\n").filter { it.isNotBlank() }
        val children = lines.map { line ->
            buildJsonObject {
                put("object", "block")
                put("type", "paragraph")
                put("paragraph", buildJsonObject {
                    put("rich_text", buildJsonArray {
                        add(buildJsonObject {
                            put("type", "text")
                            put("text", buildJsonObject {
                                put("content", line)
                            })
                        })
                    })
                })
            }
        }

        return try {
            val result = notionClient.appendBlockChildren(pageId, children)
            // Return a valid JSON string that can be parsed
            val successJson = buildJsonObject {
                put("success", true)
                put("message", "Successfully appended ${children.size} block(s) to page")
                put("blocksCount", children.size)
            }
            buildJsonObject {
                put("isError", false)
                put("content", buildJsonArray {
                    add(buildJsonObject {
                        put("type", "text")
                        put("text", json.encodeToString(JsonElement.serializer(), successJson))
                    })
                })
            }
        } catch (e: org.example.notion.NotionException) {
            buildJsonObject {
                put("isError", true)
                put("content", buildJsonArray {
                    add(buildJsonObject {
                        put("type", "text")
                        put("text", e.message ?: "Ошибка при добавлении блока")
                    })
                })
            }
        } catch (e: Exception) {
            buildJsonObject {
                put("isError", true)
                put("content", buildJsonArray {
                    add(buildJsonObject {
                        put("type", "text")
                        put("text", "Ошибка при добавлении блока: ${e.message ?: "Неизвестная ошибка"}")
                    })
                })
            }
        }
    }
}
