package org.example.agent.android

import kotlinx.coroutines.*
import java.io.File

/**
 * Controls Android emulator lifecycle.
 * Handles starting, stopping, and checking emulator status.
 */
class AndroidEmulatorController(
    private val emulatorPath: String,
    private val adbExecutor: AdbCommandExecutor,
    private val avdName: String? = null
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * Checks if emulator executable is available.
     */
    suspend fun isEmulatorAvailable(): Boolean = withContext(Dispatchers.IO) {
        val emulatorFile = File(emulatorPath)
        emulatorFile.exists() && emulatorFile.canExecute()
    }
    
    /**
     * Checks if an emulator is already running.
     */
    suspend fun isEmulatorRunning(): Boolean {
        return adbExecutor.isEmulatorRunning()
    }
    
    /**
     * Starts the Android emulator.
     * 
     * @param avdName Optional AVD name. If not provided, uses the first available AVD.
     * @return The device ID of the started emulator, or null if failed
     */
    suspend fun startEmulator(avdName: String? = null): String? {
        return withContext(Dispatchers.IO) {
            try {
                // Check if already running
                if (isEmulatorRunning()) {
                    val devices = adbExecutor.listEmulators()
                    return@withContext devices.firstOrNull()
                }
                
                // Determine which AVD to use
                val targetAvd = avdName ?: this@AndroidEmulatorController.avdName ?: getFirstAvailableAvd()
                if (targetAvd == null) {
                    throw Exception("No AVD available. Please create an Android Virtual Device first.")
                }
                
                // Start emulator in background
                val processBuilder = ProcessBuilder(
                    emulatorPath,
                    "-avd", targetAvd,
                    "-no-snapshot-save" // Don't save snapshot for faster startup
                )
                processBuilder.redirectErrorStream(true)
                processBuilder.start()
                
                // Wait for emulator to appear in device list
                var deviceId: String? = null
                var attempts = 0
                val maxAttempts = 60 // Wait up to 2 minutes
                
                while (deviceId == null && attempts < maxAttempts) {
                    delay(2000)
                    val devices = adbExecutor.listEmulators()
                    deviceId = devices.firstOrNull()
                    attempts++
                }
                
                if (deviceId == null) {
                    throw Exception("Emulator started but device not found in ADB devices list")
                }
                
                // Wait for full boot
                val bootComplete = adbExecutor.waitForBootComplete(deviceId, timeoutSeconds = 120)
                if (!bootComplete) {
                    throw Exception("Emulator did not finish booting within timeout")
                }
                
                deviceId
            } catch (e: Exception) {
                throw Exception("Failed to start emulator: ${e.message}", e)
            }
        }
    }
    
    /**
     * Gets the first available AVD name.
     */
    private suspend fun getFirstAvailableAvd(): String? {
        return withContext(Dispatchers.IO) {
            try {
                val processBuilder = ProcessBuilder(emulatorPath, "-list-avds")
                processBuilder.redirectErrorStream(true)
                val process = processBuilder.start()
                val output = process.inputStream.bufferedReader().readText()
                process.waitFor()
                
                output.lines()
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .firstOrNull()
            } catch (e: Exception) {
                null
            }
        }
    }
    
    fun close() {
        scope.cancel()
    }
}


