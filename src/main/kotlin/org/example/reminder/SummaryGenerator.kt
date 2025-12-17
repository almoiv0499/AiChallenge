package org.example.reminder

/**
 * Generates human-readable summaries from lists of tasks.
 * Shows current state and recent changes.
 */
object SummaryGenerator {
    /**
     * Generates a summary showing active tasks and recently completed tasks.
     * 
     * @param activeTasks List of currently active tasks (status != "Done")
     * @param completedTasks List of recently completed tasks (tasks that became Done)
     * @return Formatted summary string
     * 
     * Example output:
     * ```
     * Сейчас в работе:
     * Task1
     * Task2
     * Task3
     * 
     * Завершены:
     * Task4
     * ```
     */
    fun generateSummary(activeTasks: List<Task>, completedTasks: List<Task>): String {
        val parts = mutableListOf<String>()
        
        // Add active tasks section
        if (activeTasks.isNotEmpty()) {
            parts.add("Сейчас в работе:")
            activeTasks.forEach { task ->
                if (task.name.isNotBlank()) {
                    parts.add(task.name)
                }
            }
        }
        
        // Add completed tasks section
        if (completedTasks.isNotEmpty()) {
            if (parts.isNotEmpty()) {
                parts.add("") // Empty line separator
            }
            parts.add("Завершены:")
            completedTasks.forEach { task ->
                if (task.name.isNotBlank()) {
                    parts.add(task.name)
                }
            }
        }
        
        return if (parts.isEmpty()) {
            ""
        } else {
            parts.joinToString("\n")
        }
    }
}
