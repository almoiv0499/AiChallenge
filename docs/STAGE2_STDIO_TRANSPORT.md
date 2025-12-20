# Stage 2: MCP stdio Transport Support

## Implementation Summary

### Transport Abstraction

Created a transport abstraction layer to support multiple transport mechanisms:

- **`Transport.kt`**: Base interface for all transport implementations
- **`HttpTransport.kt`**: HTTP transport implementation (refactored from original McpClient)
- **`StdioTransport.kt`**: New stdio transport implementation using non-blocking I/O

### Key Features

1. **Non-blocking I/O**: All stdio operations use Kotlin Coroutines
2. **Transport Selection**: Configuration-based transport selection via `MCP_TRANSPORT` environment variable or `local.properties`
3. **Backward Compatibility**: Existing HTTP transport continues to work

### Files Created

- `src/main/kotlin/org/example/mcp/transport/Transport.kt`
- `src/main/kotlin/org/example/mcp/transport/HttpTransport.kt`
- `src/main/kotlin/org/example/mcp/transport/StdioTransport.kt`
- `src/main/kotlin/org/example/mcp/server/StdioMcpServer.kt`

### Files Modified

- `src/main/kotlin/org/example/mcp/McpClient.kt`: Refactored to use transport abstraction
- `src/main/kotlin/org/example/config/AppConfig.kt`: Added transport configuration support
- `src/main/kotlin/org/example/Main.kt`: Updated to use new McpClient factory methods

### Usage

#### HTTP Transport (Default)
```kotlin
val client = McpClient.createHttp(baseUrl = "http://localhost:8081/mcp")
```

#### stdio Transport
```kotlin
val client = McpClient.createStdio()
```

#### Configuration-based Selection
Set `MCP_TRANSPORT=stdio` or `MCP_TRANSPORT=http` in environment or `local.properties`

### Example: Running with stdio Transport

To run the MCP server using stdio transport:

1. Set transport type:
   ```bash
   export MCP_TRANSPORT=stdio
   ```

2. Run the application:
   ```bash
   ./gradlew run
   ```

The stdio transport reads JSON-RPC requests from stdin and writes responses to stdout, following the MCP protocol specification.

