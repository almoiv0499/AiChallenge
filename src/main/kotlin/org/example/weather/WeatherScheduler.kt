package org.example.weather

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.example.mcp.McpClient
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Background scheduler that fetches weather data every 30 seconds,
 * generates a summary, and writes it to Notion page via MCP.
 */
class WeatherScheduler(
    private val weatherClient: WeatherClient,
    private val lat: Double,
    private val lon: Double,
    private val notionMcpUrl: String = "http://localhost:8081/mcp"
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var mcpClient: McpClient? = null
    
    /**
     * Starts the weather monitoring loop.
     * Fetches weather every 30 seconds and writes summary to Notion.
     */
    fun start() {
        scope.launch {
            initializeMcpClient()
            runWeatherLoop()
        }
    }
    
    private suspend fun initializeMcpClient() {
        try {
            mcpClient = McpClient(baseUrl = notionMcpUrl)
            mcpClient?.initialize()
        } catch (e: Exception) {
            println("[WARNING] Failed to initialize Notion MCP client: ${e.message}")
            println("[WARNING] Weather summaries will not be written to Notion")
        }
    }
    
    /**
     * Main loop that runs continuously.
     * Every 30 seconds:
     * 1. Fetches current weather data
     * 2. Generates summary with temperature and clothing recommendations
     * 3. Writes summary to Notion page via MCP
     */
    private suspend fun runWeatherLoop() {
        while (true) {
            try {
                // 1. Fetch weather data
                val weatherData = weatherClient.getCurrentWeather(lat, lon, "metric")
                
                // 2. Generate summary
                val summary = WeatherSummaryGenerator.generateSummary(weatherData)
                
                // 3. Write to Notion via MCP
                writeSummaryToNotion(summary)
                
            } catch (e: org.example.weather.WeatherException) {
                println("[ERROR] Weather API error: ${e.message}")
            } catch (e: java.net.UnknownHostException) {
                // Network/DNS error - skip silently
            } catch (e: java.nio.channels.UnresolvedAddressException) {
                // Address resolution error - skip silently
            } catch (e: Exception) {
                println("[ERROR] Failed to process weather: ${e.message}")
            }
            
            delay(30_000) // 30 seconds
        }
    }
    
    /**
     * Writes weather summary to Notion page using MCP.
     */
    private suspend fun writeSummaryToNotion(summary: String) {
        val client = mcpClient ?: return
        
        try {
            // Call append_notion_block tool via MCP
            val arguments = buildJsonObject {
                put("text", summary)
            }
            
            val response = client.callTool("append_notion_block", arguments)
            
            // Log the summary locally as well
            println("\n" + "=".repeat(50))
            println("🌤️ Weather Summary (written to Notion):")
            println("=".repeat(50))
            println(summary)
            println("=".repeat(50) + "\n")
            
        } catch (e: Exception) {
            println("[ERROR] Failed to write summary to Notion: ${e.message}")
            // Still log the summary locally even if Notion write fails
            println("\n" + "=".repeat(50))
            println("🌤️ Weather Summary (Notion write failed):")
            println("=".repeat(50))
            println(summary)
            println("=".repeat(50) + "\n")
        }
    }
}
