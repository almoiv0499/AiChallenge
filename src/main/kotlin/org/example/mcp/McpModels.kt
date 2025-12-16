package org.example.mcp

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class JsonRpcRequest(
    val jsonrpc: String = "2.0",
    val id: Int? = null,
    val method: String,
    val params: JsonElement? = null
)

@Serializable
data class JsonRpcResponse(
    val jsonrpc: String = "2.0",
    val id: Int? = null,
    val result: JsonElement? = null,
    val error: JsonRpcError? = null
)

@Serializable
data class JsonRpcError(
    val code: Int,
    val message: String,
    val data: JsonElement? = null
)

@Serializable
data class InitializeParams(
    @SerialName("protocolVersion") val protocolVersion: String,
    val capabilities: ClientCapabilities,
    @SerialName("clientInfo") val clientInfo: ClientInfo
)

@Serializable
data class ClientCapabilities(
    val tools: JsonElement? = JsonObject(emptyMap())
)

@Serializable
data class ClientInfo(
    val name: String,
    val version: String
)

@Serializable
data class InitializeResult(
    @SerialName("protocolVersion") val protocolVersion: String,
    val capabilities: ServerCapabilities,
    @SerialName("serverInfo") val serverInfo: ServerInfo
)

@Serializable
data class ServerCapabilities(
    val tools: ToolsCapability? = null
)

@Serializable
data class ToolsCapability(
    @SerialName("listChanged") val listChanged: Boolean? = null
)

@Serializable
data class ServerInfo(
    val name: String,
    val version: String
)

@Serializable
data class ToolsListParams(
    val cursor: String? = null
)

@Serializable
data class ToolsListResult(
    val tools: List<McpTool>,
    @SerialName("nextCursor") val nextCursor: String? = null
)

@Serializable
data class McpTool(
    val name: String,
    val description: String? = null,
    @SerialName("inputSchema") val inputSchema: JsonElement? = null
)

