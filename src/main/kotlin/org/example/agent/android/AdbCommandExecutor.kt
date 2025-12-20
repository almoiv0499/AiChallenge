package org.example.agent.android

import kotlinx.coroutines.*
import java.io.File

/**
 * Executes ADB (Android Debug Bridge) commands.
 * Uses non-blocking I/O with Kotlin Coroutines.
 */
class AdbCommandExecutor(
    private val adbPath: String
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * Checks if ADB is available at the configured path.
     */
    suspend fun isAdbAvailable(): Boolean = withContext(Dispatchers.IO) {
        val adbFile = File(adbPath)
        adbFile.exists() && adbFile.canExecute()
    }
    
    /**
     * Executes an ADB command and returns the output.
     */
    suspend fun executeCommand(vararg args: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val processBuilder = ProcessBuilder(adbPath, *args)
                processBuilder.redirectErrorStream(true)
                val process = processBuilder.start()
                
                val output = process.inputStream.bufferedReader().readText()
                val exitCode = process.waitFor()
                
                if (exitCode != 0) {
                    throw Exception("ADB command failed with exit code $exitCode: $output")
                }
                
                output.trim()
            } catch (e: Exception) {
                throw Exception("Failed to execute ADB command: ${e.message}", e)
            }
        }
    }
    
    /**
     * Checks if any emulator is currently running.
     */
    suspend fun isEmulatorRunning(): Boolean {
        return try {
            val devices = executeCommand("devices")
            devices.lines().any { line ->
                line.contains("emulator") && line.contains("device")
            }
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Gets the list of available emulators.
     */
    suspend fun listEmulators(): List<String> {
        return try {
            val output = executeCommand("devices", "-l")
            output.lines()
                .filter { it.contains("emulator") && it.contains("device") }
                .map { line ->
                    line.split("\\s+".toRegex()).firstOrNull() ?: ""
                }
                .filter { it.isNotBlank() }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Waits for the device to be fully booted.
     */
    suspend fun waitForBootComplete(deviceId: String, timeoutSeconds: Int = 120): Boolean {
        val startTime = System.currentTimeMillis()
        val timeoutMillis = timeoutSeconds * 1000L
        
        while (System.currentTimeMillis() - startTime < timeoutMillis) {
            try {
                val bootComplete = executeCommand("-s", deviceId, "shell", "getprop", "sys.boot_completed")
                if (bootComplete.trim() == "1") {
                    // Additional check: wait for package manager
                    delay(2000)
                    val pmReady = executeCommand("-s", deviceId, "shell", "pm", "list", "packages")
                    if (pmReady.isNotBlank()) {
                        return true
                    }
                }
            } catch (e: Exception) {
                // Continue waiting
            }
            delay(2000)
        }
        return false
    }
    
    /**
     * Launches Chrome browser with a search query.
     */
    suspend fun launchChromeWithSearch(deviceId: String, query: String): Boolean {
        return try {
            // URL encode the query
            val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
            val searchUrl = "https://www.google.com/search?q=$encodedQuery"
            
            // Launch Chrome with the search URL
            executeCommand(
                "-s", deviceId,
                "shell", "am", "start",
                "-a", "android.intent.action.VIEW",
                "-d", searchUrl,
                "com.android.chrome"
            )
            true
        } catch (e: Exception) {
            false
        }
    }
    
    fun close() {
        scope.cancel()
    }
}


