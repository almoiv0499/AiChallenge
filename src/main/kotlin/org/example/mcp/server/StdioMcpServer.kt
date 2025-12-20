package org.example.mcp.server

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.example.mcp.JsonRpcRequest
import org.example.mcp.JsonRpcResponse
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

/**
 * MCP server implementation using stdio transport.
 * Reads JSON-RPC requests from stdin and writes responses to stdout.
 */
class StdioMcpServer(
    private val requestHandler: suspend (JsonRpcRequest) -> JsonRpcResponse,
    private val input: InputStream = System.`in`,
    private val output: OutputStream = System.out,
    private val error: OutputStream = System.err
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isRunning = false
    
    /**
     * Starts the stdio server.
     * Reads from stdin and processes JSON-RPC requests.
     */
    suspend fun start() {
        if (isRunning) return
        isRunning = true
        
        scope.launch {
            try {
                val reader = BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8))
                val writer = BufferedWriter(OutputStreamWriter(output, StandardCharsets.UTF_8))
                
                while (isRunning && scope.isActive) {
                    val line = withContext(Dispatchers.IO) {
                        reader.readLine()
                    } ?: break
                    
                    if (line.isBlank()) continue
                    
                    try {
                        val requestElement = json.parseToJsonElement(line)
                        val request = json.decodeFromJsonElement(JsonRpcRequest.serializer(), requestElement)
                        
                        // Process request
                        val response = requestHandler(request)
                        
                        // Send response
                        val responseJson = json.encodeToString(JsonRpcResponse.serializer(), response)
                        withContext(Dispatchers.IO) {
                            writer.write(responseJson)
                            writer.newLine()
                            writer.flush()
                        }
                    } catch (e: Exception) {
                        // Send error response
                        val errorResponse = JsonRpcResponse(
                            id = null,
                            error = org.example.mcp.JsonRpcError(
                                code = -32700,
                                message = "Parse error: ${e.message}"
                            )
                        )
                        val errorJson = json.encodeToString(JsonRpcResponse.serializer(), errorResponse)
                        withContext(Dispatchers.IO) {
                            writer.write(errorJson)
                            writer.newLine()
                            writer.flush()
                        }
                    }
                }
            } catch (e: Exception) {
                if (isRunning) {
                    writeError("Error in stdio server: ${e.message}\n")
                }
            }
        }
    }
    
    /**
     * Stops the stdio server.
     */
    fun stop() {
        isRunning = false
        scope.cancel()
    }
    
    private suspend fun writeError(message: String) {
        withContext(Dispatchers.IO) {
            try {
                error.write(message.toByteArray(StandardCharsets.UTF_8))
                error.flush()
            } catch (e: Exception) {
                // Ignore write errors
            }
        }
    }
}

