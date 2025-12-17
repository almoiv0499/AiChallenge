package org.example.reminder

import org.example.notion.DatabaseQueryRequest
import org.example.notion.NotionClient
import org.example.notion.NotionPage

/**
 * Service responsible for retrieving tasks from Notion.
 * 
 * Responsibilities:
 * 1. Query all tasks from Notion database
 * 2. Map Notion pages into domain Task objects
 */
class ReminderService(
    private val notionClient: NotionClient,
    private val databaseId: String
) {
    /**
     * Retrieves all tasks from the Notion database.
     * 
     * @return List of all Task objects (both active and completed)
     */
    suspend fun getAllTasks(): List<Task> {
        // Query all pages from the database
        val queryResponse = notionClient.queryDatabase(
            databaseId = databaseId,
            request = DatabaseQueryRequest() // Empty request returns all pages
        )
        
        // Map Notion pages to domain Task objects
        return queryResponse.results.map { page ->
            mapPageToTask(page)
        }
    }
    
    /**
     * Maps a Notion page to a domain Task object.
     * 
     * Extracts:
     * - id: Page ID
     * - name: From "Name" property (title type)
     * - status: From "Status" property (select type)
     * - dueDate: From "Due" property (date type)
     */
    private fun mapPageToTask(page: NotionPage): Task {
        val name = extractName(page)
        val status = extractStatus(page)
        val dueDate = extractDueDate(page)
        
        return Task(
            id = page.id,
            name = name,
            status = status,
            dueDate = dueDate
        )
    }
    
    /**
     * Extracts the name from a page's title property.
     * First tries to find a property named "Name", then searches for any property of type "title".
     */
    private fun extractName(page: NotionPage): String {
        // First, try to find property named "Name"
        val nameProperty = page.properties["Name"]
        if (nameProperty != null && nameProperty.type == "title") {
            val title = nameProperty.title
            if (title != null && title.isNotEmpty()) {
                return title.joinToString("") { 
                    it.plainText.ifEmpty { it.text.content }
                }
            }
        }
        
        // If "Name" not found or empty, search for any property of type "title"
        for ((key, property) in page.properties) {
            if (property.type == "title") {
                val title = property.title
                if (title != null && title.isNotEmpty()) {
                    return title.joinToString("") { 
                        it.plainText.ifEmpty { it.text.content }
                    }
                }
            }
        }
        
        // If no title property found, return empty string
        return ""
    }
    
    /**
     * Extracts the status from a page's "Status" property (select type).
     * Returns null if no status is set.
     */
    private fun extractStatus(page: NotionPage): String? {
        val statusProperty = page.properties["Status"]
        return if (statusProperty != null && statusProperty.type == "select") {
            statusProperty.select?.name
        } else {
            null
        }
    }
    
    /**
     * Extracts the due date from a page's "Due" property (date type).
     * Returns null if no due date is set.
     */
    private fun extractDueDate(page: NotionPage): String? {
        val dueProperty = page.properties["Due"]
        return if (dueProperty != null && dueProperty.type == "date") {
            dueProperty.date?.start
        } else {
            null
        }
    }
}
