package org.example.reminder

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.example.config.OpenRouterConfig
import org.example.storage.TaskStorage

/**
 * Background scheduler that continuously monitors and displays task reminders.
 * 
 * Polls Notion API every 10 seconds, persists task state to local database,
 * and generates summaries based on changes between previous and current state.
 */
class TaskReminderScheduler(
    private val reminderService: ReminderService
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val taskStorage = TaskStorage()
    
    /**
     * Starts the background task reminder loop.
     * The loop runs continuously and never terminates on its own.
     */
    fun start() {
        scope.launch {
            runReminderLoop()
        }
    }
    
    /**
     * Main loop that runs continuously.
     * 
     * Every 10 seconds:
     * 1. Queries Notion database using Database Query API
     * 2. Extracts list of tasks (Name + Status)
     * 3. Saves or updates tasks in local database
     * 
     * Every 30 seconds (every 3th iteration):
     * 4. Compares current task state with previous state stored locally
     * 5. Generates summary based on changes
     * 6. Prints summary to stdout
     * 
     * Exceptions are caught and logged so the loop never stops.
     */
    private suspend fun runReminderLoop() {
        var iterationCount = 0
        
        while (true) {
            // Check if task reminder is enabled
            if (!OpenRouterConfig.ENABLE_TASK_REMINDER) {
                delay(10_000)
                continue
            }
            
            try {
                // 1. Query Notion database
                val currentTasks = reminderService.getAllTasks()
                
                // 2. Save or update current tasks in database
                taskStorage.saveOrUpdateTasks(currentTasks)
                
                iterationCount++
                
                // Every minute (6 iterations of 10 seconds = 30 seconds)
                if (iterationCount >= 3) {
                    iterationCount = 0
                    
                    // 3. Get previous state from local database
                    val previousTasks = taskStorage.getAllTasks()
                    val isFirstRun = previousTasks.isEmpty()
                    
                    // 4. Compare states and detect changes
                    val currentTasksMap = currentTasks.associateBy { it.id }
                    
                    // Find tasks that are now completed (were active before, now Done or missing)
                    val completedTasks = mutableListOf<Task>()
                    
                    // Only detect completed tasks if this is not the first run
                    if (!isFirstRun) {
                        // Check previous tasks that are now Done or missing
                        for (previousTask in previousTasks) {
                            if (previousTask.status != "Done") {
                                // Was active before
                                val currentTask = currentTasksMap[previousTask.id]
                                if (currentTask == null) {
                                    // Task was removed from Notion (archived) - consider it completed
                                    completedTasks.add(previousTask.copy(status = "Done"))
                                } else if (currentTask.status == "Done") {
                                    // Task was marked as Done
                                    completedTasks.add(currentTask)
                                }
                            }
                        }
                    }
                    
                    // Find active tasks (not Done)
                    val activeTasks = currentTasks.filter { it.status != "Done" && it.name.isNotBlank() }
                    
                    // 5. Generate summary
                    val summary = SummaryGenerator.generateSummary(activeTasks, completedTasks)
                    
                    // 6. Print summary to stdout
                    if (summary.isNotBlank()) {
                        println("\n" + "=".repeat(30))
                        println(summary)
                        println("=".repeat(30) + "\n")
                    }
                }
                
            } catch (e: java.net.UnknownHostException) {
                // Network/DNS error - skip silently
            } catch (e: java.nio.channels.UnresolvedAddressException) {
                // Address resolution error - skip silently
            } catch (e: org.example.notion.NotionException) {
                // Notion API error - log but continue
                println("\n[ERROR] Notion API error: ${e.message}")
            } catch (e: Exception) {
                // Other errors - log but continue
                println("\n[ERROR] Failed to process tasks: ${e.message}")
            }
            
            delay(10_000)
        }
    }
}
