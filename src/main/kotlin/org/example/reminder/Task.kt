package org.example.reminder

/**
 * Domain model representing a task from Notion.
 */
data class Task(
    val id: String,
    val name: String,
    val status: String?,
    val dueDate: String? // ISO 8601 format date string, nullable if no due date set
)
