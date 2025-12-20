package org.example.mcp.transport

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonElement

/**
 * Base interface for MCP transport mechanisms.
 * Supports both HTTP and stdio transports.
 */
interface Transport {
    /**
     * Sends a JSON-RPC request and returns the response.
     */
    suspend fun sendRequest(request: JsonElement): JsonElement
    
    /**
     * Closes the transport connection.
     */
    fun close()
    
    /**
     * Checks if the transport is connected.
     */
    suspend fun isConnected(): Boolean
}

/**
 * Transport type enumeration.
 */
enum class TransportType {
    HTTP,
    STDIO
}


