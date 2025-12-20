package org.example.agent.android

/**
 * Interface for executing device search operations.
 * Provides abstraction for on-device search functionality.
 */
interface DeviceSearchExecutor {
    /**
     * Executes a search on the device.
     * 
     * @param query The search query to execute
     * @return Result message describing the operation outcome
     */
    suspend fun executeSearch(query: String): String
}


