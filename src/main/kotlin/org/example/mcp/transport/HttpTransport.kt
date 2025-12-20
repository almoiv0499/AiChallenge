package org.example.mcp.transport

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.client.call.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

/**
 * HTTP transport implementation for MCP protocol.
 * Refactored from the original McpClient implementation.
 */
class HttpTransport(
    private val baseUrl: String
) : Transport {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }
    
    private val httpClient: HttpClient = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 30000
            connectTimeoutMillis = 15000
            socketTimeoutMillis = 30000
        }
    }
    
    override suspend fun sendRequest(request: JsonElement): JsonElement {
        return withContext(Dispatchers.IO) {
            val requestJson = json.encodeToString(JsonElement.serializer(), request)
            val httpResponse = httpClient.post(baseUrl) {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Accept, "application/json, text/event-stream")
                setBody(requestJson)
            }
            
            if (!httpResponse.status.isSuccess()) {
                val errorBody = httpResponse.bodyAsText()
                throw Exception("HTTP ${httpResponse.status.value}: $errorBody")
            }
            
            val responseText = httpResponse.bodyAsText()
            json.parseToJsonElement(responseText)
        }
    }
    
    override suspend fun isConnected(): Boolean {
        return try {
            // Try a simple POST request to check connection with short timeout
            withContext(Dispatchers.IO) {
                val testClient = HttpClient(CIO) {
                    install(HttpTimeout) {
                        requestTimeoutMillis = 1000
                        connectTimeoutMillis = 1000
                    }
                }
                try {
                    testClient.post(baseUrl) {
                        contentType(ContentType.Application.Json)
                        setBody("{}")
                    }
                    true
                } finally {
                    testClient.close()
                }
            }
        } catch (e: Exception) {
            false
        }
    }
    
    override fun close() {
        httpClient.close()
    }
}

