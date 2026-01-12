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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.example.mcp.JsonRpcRequest
import org.example.mcp.JsonRpcResponse
import org.example.mcp.McpTool
import org.example.mcp.ToolsListResult
import java.io.File
import java.nio.file.Paths

class GitMcpServer(
    private val repositoryPath: String = "."
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }
    
    private val repoDir = File(repositoryPath).absoluteFile

    fun configureMcpServer(application: Application) {
        application.install(ContentNegotiation) {
            json(this@GitMcpServer.json)
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
                put("name", "GitMcpServer")
                put("version", "1.0.0")
            })
        }
        return JsonRpcResponse(id = request.id, result = result)
    }

    private fun handleToolsList(request: JsonRpcRequest): JsonRpcResponse {
        val tools = mutableListOf(
            createGetCurrentBranchTool(),
            createGetGitStatusTool(),
            createGetOpenFilesTool(),
            createGetIdeOpenFilesTool(),
            createGetRecentCommitsTool()
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
        val toolName = params["name"]?.jsonPrimitive?.content
            ?: return JsonRpcResponse(
                id = request.id,
                error = org.example.mcp.JsonRpcError(code = -32602, message = "Tool name is required")
            )
        val argumentsElement = params["arguments"]
        val arguments = when {
            argumentsElement == null -> buildJsonObject {}
            argumentsElement is JsonObject -> argumentsElement
            else -> buildJsonObject {}
        }
        val result = when (toolName) {
            "get_current_branch" -> executeGetCurrentBranch(arguments)
            "get_git_status" -> executeGetGitStatus(arguments)
            "get_open_files" -> executeGetOpenFiles(arguments)
            "get_ide_open_files" -> executeGetIdeOpenFiles(arguments)
            "get_recent_commits" -> executeGetRecentCommits(arguments)
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

    private fun createGetCurrentBranchTool(): McpTool {
        return McpTool(
            name = "get_current_branch",
            description = "Получить текущую активную ветку Git репозитория. Используйте этот инструмент, когда пользователь спрашивает о текущей ветке, активной ветке, на какой ветке мы находимся, или когда нужно узнать имя ветки Git. Use this tool when user asks about current branch, active branch, what branch we are on, or needs to know Git branch name.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {})
                put("required", buildJsonArray {})
            }
        )
    }

    private fun executeGetCurrentBranch(arguments: JsonObject): JsonElement {
        return try {
            val process = ProcessBuilder("git", "rev-parse", "--abbrev-ref", "HEAD")
                .directory(repoDir)
                .start()
            val output = process.inputStream.bufferedReader().readText().trim()
            val exitCode = process.waitFor()
            
            if (exitCode == 0 && output.isNotBlank()) {
                buildJsonObject {
                    put("isError", false)
                    put("content", buildJsonArray {
                        add(buildJsonObject {
                            put("type", "text")
                            put("text", output)
                        })
                    })
                }
            } else {
                buildJsonObject {
                    put("isError", true)
                    put("content", buildJsonArray {
                        add(buildJsonObject {
                            put("type", "text")
                            put("text", "Не удалось получить текущую ветку. Убедитесь, что это Git репозиторий.")
                        })
                    })
                }
            }
        } catch (e: Exception) {
            buildJsonObject {
                put("isError", true)
                put("content", buildJsonArray {
                    add(buildJsonObject {
                        put("type", "text")
                        put("text", "Ошибка при получении текущей ветки: ${e.message ?: "Неизвестная ошибка"}")
                    })
                })
            }
        }
    }

    private fun createGetGitStatusTool(): McpTool {
        return McpTool(
            name = "get_git_status",
            description = "Получить статус Git репозитория: список измененных, добавленных, удаленных файлов и их статус. Используйте этот инструмент, когда пользователь спрашивает о статусе Git, измененных файлах, что изменено, какие файлы изменены, статус репозитория, или нужно проверить состояние рабочей директории. Use this tool when user asks about Git status, changed files, what files are modified, repository status, or needs to check working directory state.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {})
                put("required", buildJsonArray {})
            }
        )
    }

    private fun executeGetGitStatus(arguments: JsonObject): JsonElement {
        return try {
            val process = ProcessBuilder("git", "status", "--short")
                .directory(repoDir)
                .start()
            val output = process.inputStream.bufferedReader().readText().trim()
            val exitCode = process.waitFor()
            
            if (exitCode == 0) {
                val statusLines = if (output.isBlank()) {
                    "Рабочая директория чиста, нет изменений"
                } else {
                    output.lines().joinToString("\n")
                }
                buildJsonObject {
                    put("isError", false)
                    put("content", buildJsonArray {
                        add(buildJsonObject {
                            put("type", "text")
                            put("text", statusLines)
                        })
                    })
                }
            } else {
                buildJsonObject {
                    put("isError", true)
                    put("content", buildJsonArray {
                        add(buildJsonObject {
                            put("type", "text")
                            put("text", "Не удалось получить статус Git. Убедитесь, что это Git репозиторий.")
                        })
                    })
                }
            }
        } catch (e: Exception) {
            buildJsonObject {
                put("isError", true)
                put("content", buildJsonArray {
                    add(buildJsonObject {
                        put("type", "text")
                        put("text", "Ошибка при получении статуса Git: ${e.message ?: "Неизвестная ошибка"}")
                    })
                })
            }
        }
    }

    private fun createGetOpenFilesTool(): McpTool {
        return McpTool(
            name = "get_open_files",
            description = "Получить список ИЗМЕНЁННЫХ файлов в Git репозитории (из git status). Используйте этот инструмент, когда пользователь спрашивает: 'какие файлы изменены', 'modified files', 'changed files', 'какие файлы были изменены', 'git status', 'uncommitted changes', 'незакоммиченные изменения'. Возвращает список файлов с незафиксированными изменениями. Use this tool for git-modified files, uncommitted changes.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {})
                put("required", buildJsonArray {})
            }
        )
    }
    
    private fun createGetIdeOpenFilesTool(): McpTool {
        return McpTool(
            name = "get_ide_open_files",
            description = "Получить список файлов, ОТКРЫТЫХ в Android Studio / IntelliJ IDEA. КРИТИЧЕСКИ ВАЖНО: Используйте этот инструмент, когда пользователь спрашивает: 'какие файлы сейчас открыты', 'какие файлы открыты в IDE', 'какие вкладки открыты', 'what files are open', 'which files are open in IDE', 'open tabs', 'открытые вкладки', 'current editor files'. Читает информацию из конфигурации IDE (.idea/workspace.xml). Use this tool when user asks about files currently open in the IDE editor.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {})
                put("required", buildJsonArray {})
            }
        )
    }

    private fun executeGetOpenFiles(arguments: JsonObject): JsonElement {
        return try {
            val process = ProcessBuilder("git", "status", "--short")
                .directory(repoDir)
                .start()
            val output = process.inputStream.bufferedReader().readText().trim()
            val exitCode = process.waitFor()
            
            if (exitCode == 0) {
                val files = if (output.isBlank()) {
                    emptyList<String>()
                } else {
                    output.lines()
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .map { line ->
                            // Git status format: " M file.txt" or "?? newfile.txt"
                            val status = line.take(2).trim()
                            val filename = line.substring(2).trim()
                            val statusEmoji = when {
                                status.contains("M") -> "📝" // Modified
                                status.contains("A") -> "➕" // Added
                                status.contains("D") -> "❌" // Deleted
                                status.contains("?") -> "❓" // Untracked
                                status.contains("R") -> "🔄" // Renamed
                                else -> "📄"
                            }
                            "$statusEmoji $filename"
                        }
                        .filter { it.isNotBlank() }
                }
                
                val result = if (files.isEmpty()) {
                    "✅ Нет изменённых файлов (рабочая директория чиста)"
                } else {
                    "📋 Изменённые файлы (git status):\n${files.joinToString("\n")}"
                }
                
                buildJsonObject {
                    put("isError", false)
                    put("content", buildJsonArray {
                        add(buildJsonObject {
                            put("type", "text")
                            put("text", result)
                        })
                    })
                }
            } else {
                buildJsonObject {
                    put("isError", true)
                    put("content", buildJsonArray {
                        add(buildJsonObject {
                            put("type", "text")
                            put("text", "Не удалось получить список файлов. Убедитесь, что это Git репозиторий.")
                        })
                    })
                }
            }
        } catch (e: Exception) {
            buildJsonObject {
                put("isError", true)
                put("content", buildJsonArray {
                    add(buildJsonObject {
                        put("type", "text")
                        put("text", "Ошибка при получении списка файлов: ${e.message ?: "Неизвестная ошибка"}")
                    })
                })
            }
        }
    }
    
    /**
     * Получает список файлов, открытых в Android Studio / IntelliJ IDEA
     * Читает информацию из .idea/workspace.xml
     */
    private fun executeGetIdeOpenFiles(arguments: JsonObject): JsonElement {
        return try {
            val workspaceFile = File(repoDir, ".idea/workspace.xml")
            
            if (!workspaceFile.exists()) {
                return buildJsonObject {
                    put("isError", false)
                    put("content", buildJsonArray {
                        add(buildJsonObject {
                            put("type", "text")
                            put("text", "⚠️ Файл .idea/workspace.xml не найден.\nВозможно, проект не открыт в Android Studio/IntelliJ IDEA или IDE ещё не сохранила состояние.")
                        })
                    })
                }
            }
            
            val workspaceContent = workspaceFile.readText()
            val openFiles = mutableListOf<String>()
            
            // Парсим XML для поиска открытых файлов
            // Ищем паттерны вида: file="file://$PROJECT_DIR$/path/to/file.kt"
            val projectDirMarker = "\$PROJECT_DIR\$/"
            var searchStart = 0
            while (true) {
                val markerIndex = workspaceContent.indexOf(projectDirMarker, searchStart)
                if (markerIndex == -1) break
                
                val pathStart = markerIndex + projectDirMarker.length
                val pathEnd = workspaceContent.indexOf('"', pathStart)
                if (pathEnd > pathStart) {
                    val filePath = workspaceContent.substring(pathStart, pathEnd)
                    if (filePath.isNotBlank() && !openFiles.contains(filePath)) {
                        openFiles.add(filePath)
                    }
                }
                searchStart = pathStart
            }
            
            // Также ищем в секции FileEditorManager для текущих вкладок
            val leafFilePattern = Regex("""leaf-file-name="([^"]+)"""")
            val leafMatches = leafFilePattern.findAll(workspaceContent)
            val currentTabs = leafMatches.map { it.groupValues[1] }.distinct().toList()
            
            val result = buildString {
                if (currentTabs.isNotEmpty()) {
                    appendLine("📑 Текущие вкладки в IDE (${currentTabs.size}):")
                    currentTabs.forEach { tab ->
                        appendLine("  📄 $tab")
                    }
                }
                
                if (openFiles.isNotEmpty()) {
                    if (currentTabs.isNotEmpty()) appendLine()
                    appendLine("📂 Недавно открытые файлы (${openFiles.size}):")
                    // Показываем только уникальные файлы, которых нет в текущих вкладках
                    val recentFiles = openFiles
                        .filter { path -> currentTabs.none { tab -> path.endsWith(tab) } }
                        .take(15) // Ограничиваем количество
                    
                    if (recentFiles.isNotEmpty()) {
                        recentFiles.forEach { file ->
                            appendLine("  📄 $file")
                        }
                        if (openFiles.size > 15) {
                            appendLine("  ... и ещё ${openFiles.size - 15} файлов")
                        }
                    } else {
                        appendLine("  (все недавние файлы уже показаны в текущих вкладках)")
                    }
                }
                
                if (currentTabs.isEmpty() && openFiles.isEmpty()) {
                    append("📭 Нет открытых файлов в IDE")
                }
            }
            
            buildJsonObject {
                put("isError", false)
                put("content", buildJsonArray {
                    add(buildJsonObject {
                        put("type", "text")
                        put("text", result)
                    })
                })
            }
        } catch (e: Exception) {
            buildJsonObject {
                put("isError", true)
                put("content", buildJsonArray {
                    add(buildJsonObject {
                        put("type", "text")
                        put("text", "Ошибка при чтении открытых файлов IDE: ${e.message ?: "Неизвестная ошибка"}")
                    })
                })
            }
        }
    }

    private fun createGetRecentCommitsTool(): McpTool {
        return McpTool(
            name = "get_recent_commits",
            description = "Получить последние коммиты Git репозитория с их сообщениями. Используйте этот инструмент, когда пользователь спрашивает о последних коммитах, истории коммитов, что было закоммичено, история изменений, или нужно посмотреть недавние коммиты. Use this tool when user asks about recent commits, commit history, what was committed, change history, or needs to see recent commits.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("limit", buildJsonObject {
                        put("type", "integer")
                        put("description", "Количество коммитов для получения (по умолчанию 5). Number of commits to retrieve (default 5)")
                        put("default", 5)
                    })
                })
                put("required", buildJsonArray {})
            }
        )
    }

    private fun executeGetRecentCommits(arguments: JsonObject): JsonElement {
        return try {
            val limit = arguments["limit"]?.jsonPrimitive?.content?.toIntOrNull() ?: 5
            val process = ProcessBuilder("git", "log", "--oneline", "-n", limit.toString())
                .directory(repoDir)
                .start()
            val output = process.inputStream.bufferedReader().readText().trim()
            val exitCode = process.waitFor()
            
            if (exitCode == 0) {
                val commits = if (output.isBlank()) {
                    "Нет коммитов в репозитории"
                } else {
                    output
                }
                buildJsonObject {
                    put("isError", false)
                    put("content", buildJsonArray {
                        add(buildJsonObject {
                            put("type", "text")
                            put("text", commits)
                        })
                    })
                }
            } else {
                buildJsonObject {
                    put("isError", true)
                    put("content", buildJsonArray {
                        add(buildJsonObject {
                            put("type", "text")
                            put("text", "Не удалось получить коммиты. Убедитесь, что это Git репозиторий.")
                        })
                    })
                }
            }
        } catch (e: Exception) {
            buildJsonObject {
                put("isError", true)
                put("content", buildJsonArray {
                    add(buildJsonObject {
                        put("type", "text")
                        put("text", "Ошибка при получении коммитов: ${e.message ?: "Неизвестная ошибка"}")
                    })
                })
            }
        }
    }
}

