package org.example.mcp.transport

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.serialization.json.*
import java.io.*
import java.nio.charset.StandardCharsets

/**
 * stdio transport implementation for MCP protocol.
 * Uses non-blocking I/O with Kotlin Coroutines.
 */
class StdioTransport(
    private val input: InputStream = System.`in`,
    private val output: OutputStream = System.out,
    private val error: OutputStream = System.err
) : Transport {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }
    
    private val requestChannel = Channel<String>(Channel.UNLIMITED)
    private val responseChannel = Channel<String>(Channel.UNLIMITED)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var requestIdCounter = 0
    private val pendingRequests = mutableMapOf<Int, CompletableDeferred<JsonElement>>()
    private var isClosed = false
    
    private val readerJob: Job
    private val writerJob: Job
    
    init {
        // Start reader coroutine for stdin
        readerJob = scope.launch {
            readFromStdin()
        }
        
        // Start writer coroutine for stdout
        writerJob = scope.launch {
            writeToStdout()
        }
    }
    
    /**
     * Reads JSON-RPC messages from stdin line by line.
     */
    private suspend fun readFromStdin() {
        try {
            val reader = BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8))
            while (!isClosed && scope.isActive) {
                val line = withContext(Dispatchers.IO) {
                    reader.readLine()
                } ?: break
                
                if (line.isBlank()) continue
                
                try {
                    val jsonElement = json.parseToJsonElement(line)
                    handleIncomingMessage(jsonElement)
                } catch (e: Exception) {
                    // Log error but continue processing
                    writeError("Error parsing JSON-RPC message: ${e.message}\n")
                }
            }
        } catch (e: Exception) {
            if (!isClosed) {
                writeError("Error reading from stdin: ${e.message}\n")
            }
        }
    }
    
    /**
     * Writes JSON-RPC messages to stdout.
     */
    private suspend fun writeToStdout() {
        try {
            val writer = BufferedWriter(OutputStreamWriter(output, StandardCharsets.UTF_8))
            while (!isClosed && scope.isActive) {
                val message = responseChannel.receive()
                withContext(Dispatchers.IO) {
                    writer.write(message)
                    writer.newLine()
                    writer.flush()
                }
            }
        } catch (e: Exception) {
            if (!isClosed) {
                writeError("Error writing to stdout: ${e.message}\n")
            }
        }
    }
    
    /**
     * Handles incoming JSON-RPC messages (requests, responses, notifications).
     */
    private suspend fun handleIncomingMessage(jsonElement: JsonElement) {
        if (jsonElement !is JsonObject) return
        
        val jsonrpc = jsonElement["jsonrpc"]?.jsonPrimitive?.content
        if (jsonrpc != "2.0") return
        
        val id = jsonElement["id"]
        val method = jsonElement["method"]?.jsonPrimitive?.content
        val result = jsonElement["result"]
        val error = jsonElement["error"]
        
        // Handle response
        if (id != null && (result != null || error != null)) {
            val idValue = when (id) {
                is JsonNull -> null
                is JsonPrimitive -> id.content.toIntOrNull()
                else -> null
            }
            
            if (idValue != null) {
                val deferred = pendingRequests.remove(idValue)
                if (deferred != null) {
                    if (error != null) {
                        deferred.completeExceptionally(
                            Exception("JSON-RPC error: ${error.jsonObject["message"]?.jsonPrimitive?.content}")
                        )
                    } else {
                        deferred.complete(result ?: JsonNull)
                    }
                }
            }
        }
        // Note: In a full implementation, we would also handle incoming requests here
        // For now, we focus on client-side functionality
    }
    
    /**
     * Sends a JSON-RPC request and waits for the response.
     */
    override suspend fun sendRequest(request: JsonElement): JsonElement {
        if (isClosed) {
            throw IllegalStateException("Transport is closed")
        }
        
        val requestObj = request as? JsonObject
            ?: throw IllegalArgumentException("Request must be a JSON object")
        
        // Extract or assign request ID
        val id = requestObj["id"]?.let {
            when (it) {
                is JsonNull -> null
                is JsonPrimitive -> it.content.toIntOrNull()
                is JsonObject -> null // ID should be primitive
                else -> null
            }
        } ?: run {
            requestIdCounter++
            requestIdCounter
        }
        
        // Create request with ID
        val requestWithId = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id)
            requestObj.forEach { (key, value) ->
                if (key != "id" || requestObj["id"] == null) {
                    put(key, value)
                }
            }
        }
        
        // For notifications (id is null), send and return immediately
        if (id == null) {
            val requestJson = json.encodeToString(JsonElement.serializer(), requestWithId)
            responseChannel.send(requestJson)
            return JsonNull // Notifications don't have responses
        }
        
        // Create deferred for response
        val deferred = CompletableDeferred<JsonElement>()
        pendingRequests[id] = deferred
        
        // Send request
        val requestJson = json.encodeToString(JsonElement.serializer(), requestWithId)
        responseChannel.send(requestJson)
        
        // Wait for response with timeout
        return try {
            withTimeout(30000) {
                deferred.await()
            }
        } catch (e: TimeoutCancellationException) {
            pendingRequests.remove(id)
            throw Exception("Request timeout after 30 seconds", e)
        }
    }
    
    /**
     * Writes error message to stderr.
     */
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
    
    override suspend fun isConnected(): Boolean {
        return !isClosed && scope.isActive
    }
    
    override fun close() {
        isClosed = true
        readerJob.cancel()
        writerJob.cancel()
        scope.cancel()
        requestChannel.close()
        responseChannel.close()
        pendingRequests.clear()
    }
}

