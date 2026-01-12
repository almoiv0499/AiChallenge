package org.example.mcp

import kotlinx.serialization.json.*
import org.example.mcp.transport.Transport
import org.example.mcp.transport.HttpTransport
import org.example.mcp.transport.StdioTransport
import org.example.mcp.transport.TransportType

class McpClient(
    private val transport: Transport,
    private val clientName: String = "KotlinMcpClient",
    private val clientVersion: String = "1.0.0",
    private val protocolVersion: String = "2025-06-18"
) {
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        prettyPrint = false
        isLenient = true
    }
    
    companion object {
        /**
         * Creates an HTTP-based MCP client.
         */
        fun createHttp(
            baseUrl: String,
            clientName: String = "KotlinMcpClient",
            clientVersion: String = "1.0.0",
            protocolVersion: String = "2025-06-18"
        ): McpClient {
            return McpClient(
                transport = HttpTransport(baseUrl),
                clientName = clientName,
                clientVersion = clientVersion,
                protocolVersion = protocolVersion
            )
        }
        
        /**
         * Creates a stdio-based MCP client.
         */
        fun createStdio(
            clientName: String = "KotlinMcpClient",
            clientVersion: String = "1.0.0",
            protocolVersion: String = "2025-06-18"
        ): McpClient {
            return McpClient(
                transport = StdioTransport(),
                clientName = clientName,
                clientVersion = clientVersion,
                protocolVersion = protocolVersion
            )
        }
    }

    suspend fun connect() {
        // Для HTTP MCP серверов отдельное подключение не требуется
    }

    suspend fun initialize(): InitializeResult {
        val params = InitializeParams(
            protocolVersion = protocolVersion,
            capabilities = ClientCapabilities(),
            clientInfo = ClientInfo(name = clientName, version = clientVersion)
        )
        val request = JsonRpcRequest(
            id = 1,
            method = "initialize",
            params = json.encodeToJsonElement(params)
        )
        val response = sendRequest(request)
        if (response.error != null) {
            throw McpException("Initialize failed: ${response.error.code} - ${response.error.message}")
        }
        val resultElement = response.result ?: throw McpException("Initialize failed: empty result")
        val result = json.decodeFromJsonElement(InitializeResult.serializer(), resultElement)
        sendInitializedNotification()
        return result
    }

    suspend fun listTools(cursor: String? = null): List<McpTool> {
        val params = ToolsListParams(cursor = cursor)
        val request = JsonRpcRequest(
            id = 2,
            method = "tools/list",
            params = json.encodeToJsonElement(params)
        )
        val response = sendRequest(request)
        if (response.error != null) {
            throw McpException("Tools list failed: ${response.error.code} - ${response.error.message}")
        }
        val resultElement = response.result ?: throw McpException("Tools list failed: empty result")
        val result = json.decodeFromJsonElement(ToolsListResult.serializer(), resultElement)
        return result.tools
    }

    suspend fun callTool(name: String, arguments: JsonElement): JsonElement {
        val params = buildJsonObject {
            put("name", name)
            put("arguments", arguments)
        }
        val request = JsonRpcRequest(
            id = 3,
            method = "tools/call",
            params = json.encodeToJsonElement(params)
        )
        val response = sendRequest(request)
        if (response.error != null) {
            throw McpException("Tool call failed: ${response.error.code} - ${response.error.message}")
        }
        val resultElement = response.result ?: throw McpException("Tool call failed: empty result")
        val resultObject = when {
            resultElement is JsonNull -> throw McpException("Tool call failed: result is JsonNull")
            resultElement is JsonObject -> resultElement
            else -> throw McpException("Tool call failed: result is not a JSON object, type: ${resultElement.javaClass.simpleName}")
        }
        val isError = resultObject["isError"]?.jsonPrimitive?.content?.toBoolean() ?: false
        if (isError) {
            val contentElement = resultObject["content"]
            val contentArray = when (contentElement) {
                null, is JsonNull -> null
                is JsonArray -> contentElement
                else -> null
            }
            val errorItem = contentArray?.firstOrNull()
            val errorText = when {
                errorItem == null || errorItem is JsonNull -> "Unknown error"
                errorItem is JsonObject -> errorItem.get("text")?.jsonPrimitive?.content ?: "Unknown error"
                else -> "Unknown error"
            }
            throw McpException("Tool execution error: $errorText")
        }
        
        // Возвращаем весь объект результата, чтобы McpToolAdapter мог его обработать
        // Формат: { "isError": false, "content": [{ "type": "text", "text": "..." }] }
        return resultObject
    }

    fun close() {
        transport.close()
    }

    private suspend fun sendRequest(request: JsonRpcRequest): JsonRpcResponse {
        val requestElement = json.encodeToJsonElement(JsonRpcRequest.serializer(), request)
        val responseElement = transport.sendRequest(requestElement)
        
        // For stdio transport, the response is already a JsonElement
        // For HTTP transport, we need to decode it
        val response = when (transport) {
            is HttpTransport -> {
                // HTTP transport returns JsonElement that needs to be decoded
                json.decodeFromJsonElement(JsonRpcResponse.serializer(), responseElement)
            }
            is StdioTransport -> {
                // Stdio transport returns JsonElement directly
                json.decodeFromJsonElement(JsonRpcResponse.serializer(), responseElement)
            }
            else -> {
                json.decodeFromJsonElement(JsonRpcResponse.serializer(), responseElement)
            }
        }
        return response
    }

    private suspend fun sendInitializedNotification() {
        val notification = JsonRpcRequest(
            id = null,
            method = "notifications/initialized",
            params = null
        )
        // For stdio, notifications are sent but we don't wait for response
        // For HTTP, we still send but ignore response
        try {
            val notificationElement = json.encodeToJsonElement(JsonRpcRequest.serializer(), notification)
            if (transport is HttpTransport) {
                // HTTP transport: send notification
                transport.sendRequest(notificationElement)
            } else {
                // Stdio transport: send notification (fire and forget)
                transport.sendRequest(notificationElement)
            }
        } catch (e: Exception) {
            // Ignore notification errors
        }
    }
}

class McpException(message: String) : Exception(message)
