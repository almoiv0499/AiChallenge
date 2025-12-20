package org.example.agent.android

import kotlinx.coroutines.*
import org.example.config.AppConfig

/**
 * Service that orchestrates device search operations.
 * Implements the DeviceSearchExecutor interface and coordinates
 * emulator control and Chrome launching.
 */
class DeviceSearchService(
    private val emulatorController: AndroidEmulatorController,
    private val adbExecutor: AdbCommandExecutor
) : DeviceSearchExecutor {
    
    override suspend fun executeSearch(query: String): String {
        return try {
            // Check if emulator is running
            val isRunning = emulatorController.isEmulatorRunning()
            val deviceId: String
            
            if (isRunning) {
                // Get the running emulator device ID
                val devices = adbExecutor.listEmulators()
                deviceId = devices.firstOrNull()
                    ?: return "Error: Emulator appears to be running but device ID not found"
            } else {
                // Start emulator
                val startedDeviceId = emulatorController.startEmulator()
                if (startedDeviceId == null) {
                    return "Error: Failed to start Android emulator. Please check your Android SDK setup."
                }
                deviceId = startedDeviceId
            }
            
            // Launch Chrome with search query
            val success = adbExecutor.launchChromeWithSearch(deviceId, query)
            
            if (success) {
                "Successfully launched Chrome browser on Android emulator with search query: '$query'"
            } else {
                "Error: Failed to launch Chrome browser. Please ensure Chrome is installed on the emulator."
            }
        } catch (e: Exception) {
            "Error executing device search: ${e.message}"
        }
    }
    
    companion object {
        /**
         * Creates a DeviceSearchService with configuration from AppConfig.
         */
        suspend fun create(): DeviceSearchService? {
            val androidSdkPath = AppConfig.loadAndroidSdkPath() ?: return null
            val adbPath = AppConfig.loadAdbPath(androidSdkPath)
            val emulatorPath = AppConfig.loadEmulatorPath(androidSdkPath)
            
            val adbExecutor = AdbCommandExecutor(adbPath)
            val emulatorController = AndroidEmulatorController(emulatorPath, adbExecutor)
            
            return DeviceSearchService(emulatorController, adbExecutor)
        }
    }
}


