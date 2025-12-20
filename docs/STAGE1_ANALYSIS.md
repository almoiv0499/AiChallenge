# Stage 1: Repository Analysis

## Current Project Architecture

### Module Structure
The current project (AiChallenge) follows a clean architecture with the following structure:

```
src/main/kotlin/org/example/
├── Main.kt                    # Application entry point
├── agent/                     # Agent logic (OpenRouterAgent)
├── client/                    # HTTP clients (OpenRouterClient)
├── config/                    # Configuration management
├── mcp/                       # MCP implementation
│   ├── McpClient.kt          # MCP client (HTTP transport)
│   ├── McpModels.kt          # MCP protocol models
│   └── server/               # MCP servers
│       ├── NotionMcpServer.kt
│       └── WeatherMcpServer.kt
├── models/                    # Data models
├── tools/                     # Tool registry and adapters
├── ui/                        # Console UI
└── [other modules]
```

### Application Entry Points

**Main Entry Point**: `org.example.Main.kt`
- Initializes configuration
- Starts local MCP servers (Notion on port 8081, Weather on port 8082)
- Connects to MCP servers via HTTP
- Runs chat loop with OpenRouterAgent

### MCP Interaction Implementation

**Current Transport**: HTTP (via Ktor)
- **Client**: `McpClient.kt` - Uses Ktor HTTP client for JSON-RPC communication
- **Servers**: `NotionMcpServer.kt`, `WeatherMcpServer.kt` - Ktor-based HTTP servers
- **Protocol**: JSON-RPC 2.0 over HTTP POST requests
- **Endpoints**: `/mcp` on each server

**MCP Protocol Flow**:
1. Client sends `initialize` request
2. Server responds with capabilities and server info
3. Client sends `notifications/initialized`
4. Client can call `tools/list` and `tools/call`

### Existing Transports

Currently, only **HTTP transport** is implemented:
- Uses Ktor's `HttpClient` for client-side
- Uses Ktor's `embeddedServer(Netty)` for server-side
- Communication via JSON-RPC over HTTP POST

### Where to Add New Capabilities

**For stdio transport (Stage 2)**:
1. Create new transport abstraction in `mcp/transport/`:
   - `Transport.kt` - Base interface
   - `HttpTransport.kt` - Existing HTTP implementation (refactor)
   - `StdioTransport.kt` - New stdio implementation
2. Refactor `McpClient.kt` to use transport abstraction
3. Create `McpStdioServer.kt` for server-side stdio handling
4. Add configuration in `config/` to select transport type

**For agent behavior (Stage 3)**:
1. Create `agent/DeviceSearchExecutor.kt` - Interface for device search
2. Create `agent/android/` package:
   - `AndroidEmulatorController.kt` - Emulator management
   - `ChromeLauncher.kt` - Browser launching
   - `AdbCommandExecutor.kt` - ADB command execution
3. Integrate into `OpenRouterAgent.kt` to detect "on-device search" intent
4. Add configuration for Android SDK paths in `AppConfig.kt`

### Architectural Constraints

1. **Non-blocking I/O**: All I/O operations must use Kotlin Coroutines
2. **Clean separation**: MCP handlers should not directly control emulator
3. **Configuration-driven**: No hardcoded paths, use configuration
4. **Ktor-based**: Server components use Ktor framework
5. **Coroutines**: All async operations use Kotlin Coroutines

### Established Patterns

1. **Tool Registry Pattern**: `ToolRegistry` manages available tools
2. **Adapter Pattern**: `McpToolAdapter` adapts MCP tools to internal tool interface
3. **Configuration Pattern**: `AppConfig` loads from environment or properties file
4. **Server Pattern**: MCP servers implement `configureMcpServer(Application)` method

## Integration Points for Next Stages

### Stage 2 (stdio transport):
- **Location**: `src/main/kotlin/org/example/mcp/transport/`
- **Files to create**:
  - `Transport.kt` - Transport interface
  - `StdioTransport.kt` - stdio implementation
  - `StdioTransportClient.kt` - Client-side stdio
  - `StdioTransportServer.kt` - Server-side stdio
- **Files to modify**:
  - `McpClient.kt` - Add transport abstraction
  - `Main.kt` - Add transport selection logic
  - `AppConfig.kt` - Add transport configuration

### Stage 3 (Android emulator agent):
- **Location**: `src/main/kotlin/org/example/agent/android/`
- **Files to create**:
  - `DeviceSearchExecutor.kt` - Interface
  - `AndroidEmulatorController.kt` - Emulator management
  - `ChromeLauncher.kt` - Browser launching
  - `AdbCommandExecutor.kt` - ADB commands
  - `DeviceSearchService.kt` - Orchestration service
- **Files to modify**:
  - `OpenRouterAgent.kt` - Add intent detection and device search integration
  - `AppConfig.kt` - Add Android SDK path configuration
  - `Main.kt` - Initialize device search service

