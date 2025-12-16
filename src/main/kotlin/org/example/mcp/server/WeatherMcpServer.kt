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
import org.example.weather.WeatherClient
import org.example.weather.WeatherException

class WeatherMcpServer(private val weatherClient: WeatherClient) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    fun configureMcpServer(application: Application) {
        application.install(ContentNegotiation) {
            json(this@WeatherMcpServer.json)
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
                put("name", "WeatherMcpServer")
                put("version", "1.0.0")
            })
        }
        return JsonRpcResponse(id = request.id, result = result)
    }

    private fun handleToolsList(request: JsonRpcRequest): JsonRpcResponse {
        val tools = listOf(
            createGetWeatherTool()
        )
        val toolsListResult = ToolsListResult(tools = tools)
        val result = json.encodeToJsonElement(ToolsListResult.serializer(), toolsListResult)
        return JsonRpcResponse(id = request.id, result = result)
    }

    private suspend fun handleToolCall(request: JsonRpcRequest): JsonRpcResponse {
        val params = request.params?.jsonObject ?: return JsonRpcResponse(
            id = request.id,
            error = org.example.mcp.JsonRpcError(code = -32602, message = "Invalid params")
        )
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
            "get_weather" -> executeGetWeather(arguments)
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

    private fun createGetWeatherTool(): McpTool {
        return McpTool(
            name = "get_weather",
            description = "Получить текущую погоду и прогноз для указанных координат (широта и долгота). Используй этот инструмент ВСЕГДА, когда пользователь спрашивает о погоде. Возвращает текущую погоду, почасовой прогноз на 48 часов и дневной прогноз на 8 дней.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("lat", buildJsonObject {
                        put("type", "number")
                        put("description", "Широта в градусах (от -90 до 90). Например: 55.7558 для Москвы")
                    })
                    put("lon", buildJsonObject {
                        put("type", "number")
                        put("description", "Долгота в градусах (от -180 до 180). Например: 37.6173 для Москвы")
                    })
                    put("units", buildJsonObject {
                        put("type", "string")
                        put("description", "Единицы измерения: 'metric' (Цельсий, м/с), 'imperial' (Фаренгейт, мили/ч), 'standard' (Кельвин, м/с). По умолчанию: 'metric'")
                        put("enum", buildJsonArray {
                            add("metric")
                            add("imperial")
                            add("standard")
                        })
                    })
                })
                put("required", buildJsonArray {
                    add("lat")
                    add("lon")
                })
            }
        )
    }

    private suspend fun executeGetWeather(arguments: JsonObject): JsonElement {
        val lat = arguments["lat"]?.jsonPrimitive?.content?.toDoubleOrNull()
            ?: return buildJsonObject {
                put("isError", true)
                put("content", buildJsonArray {
                    add(buildJsonObject {
                        put("type", "text")
                        put("text", "lat is required and must be a number")
                    })
                })
            }
        val lon = arguments["lon"]?.jsonPrimitive?.content?.toDoubleOrNull()
            ?: return buildJsonObject {
                put("isError", true)
                put("content", buildJsonArray {
                    add(buildJsonObject {
                        put("type", "text")
                        put("text", "lon is required and must be a number")
                    })
                })
            }
        val units = arguments["units"]?.jsonPrimitive?.content ?: "metric"
        if (lat < -90 || lat > 90) {
            return buildJsonObject {
                put("isError", true)
                put("content", buildJsonArray {
                    add(buildJsonObject {
                        put("type", "text")
                        put("text", "lat must be between -90 and 90")
                    })
                })
            }
        }
        if (lon < -180 || lon > 180) {
            return buildJsonObject {
                put("isError", true)
                put("content", buildJsonArray {
                    add(buildJsonObject {
                        put("type", "text")
                        put("text", "lon must be between -180 and 180")
                    })
                })
            }
        }
        return try {
            val weatherData = weatherClient.getCurrentWeather(lat, lon, units)
            buildJsonObject {
                put("isError", false)
                put("content", buildJsonArray {
                    add(buildJsonObject {
                        put("type", "text")
                        put("text", json.encodeToString(org.example.weather.WeatherResponse.serializer(), weatherData))
                    })
                })
            }
        } catch (e: WeatherException) {
            buildJsonObject {
                put("isError", true)
                put("content", buildJsonArray {
                    add(buildJsonObject {
                        put("type", "text")
                        put("text", e.message ?: "Ошибка при получении данных о погоде")
                    })
                })
            }
        } catch (e: Exception) {
            buildJsonObject {
                put("isError", true)
                put("content", buildJsonArray {
                    add(buildJsonObject {
                        put("type", "text")
                        put("text", "Ошибка при получении погоды: ${e.message ?: "Неизвестная ошибка"}")
                    })
                })
            }
        }
    }
}
