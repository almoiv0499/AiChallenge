package org.example.mcp

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement

class McpClient(
    private val baseUrl: String,
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
    private val httpClient: HttpClient = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 30000
            connectTimeoutMillis = 15000
            socketTimeoutMillis = 30000
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

    fun close() {
        httpClient.close()
    }

    private suspend fun sendRequest(request: JsonRpcRequest): JsonRpcResponse {
        return withContext(Dispatchers.IO) {
            val requestJson = json.encodeToString(JsonRpcRequest.serializer(), request)
            val httpResponse = httpClient.post(baseUrl) {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Accept, "application/json, text/event-stream")
                setBody(requestJson)
            }
            if (!httpResponse.status.isSuccess()) {
                val errorBody = httpResponse.bodyAsText()
                throw McpException("HTTP ${httpResponse.status.value}: $errorBody")
            }
            val responseText = httpResponse.bodyAsText()
            json.decodeFromString(JsonRpcResponse.serializer(), responseText)
        }
    }

    private suspend fun sendInitializedNotification() {
        withContext(Dispatchers.IO) {
            val notification = JsonRpcRequest(
                id = null,
                method = "notifications/initialized",
                params = null
            )
            val notificationJson = json.encodeToString(JsonRpcRequest.serializer(), notification)
            httpClient.post(baseUrl) {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Accept, "application/json, text/event-stream")
                setBody(notificationJson)
            }
        }
    }
}

class McpException(message: String) : Exception(message)
