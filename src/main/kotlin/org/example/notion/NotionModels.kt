package org.example.notion

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Database Object
 * 
 * Response from GET /v1/databases/{database_id}
 * Contains database metadata including properties schema.
 */
@Serializable
data class NotionDatabase(
    @SerialName("object") val objectType: String, // Always "database"
    val id: String,
    val title: List<NotionRichText>,
    val properties: Map<String, NotionPropertySchema>
)

/**
 * Property Schema Object
 * 
 * Defines the schema of a property in a database.
 * This is different from NotionProperty (which contains values).
 * Type-specific configuration fields are stored as empty objects for most types.
 */
@Serializable
data class NotionPropertySchema(
    val id: String,
    val type: String
    // Type-specific configuration (e.g., number format, select options) 
    // is typically an empty object {} and can be ignored for basic usage
)

/**
 * Database Query Request
 * 
 * Request body for POST /v1/databases/{database_id}/query
 * All fields are optional. An empty request body {} will return all pages.
 * 
 * Example empty request (returns all pages):
 * DatabaseQueryRequest()
 * 
 * Example with filter (build filter as JsonObject when implementing business logic):
 * {
 *   "filter": {
 *     "property": "Status",
 *     "select": {
 *       "equals": "Done"
 *     }
 *   }
 * }
 * 
 * Note: filter and sorts are stored as JsonObject for flexibility.
 * They should be built using JsonObject builder when needed.
 */
@Serializable
data class DatabaseQueryRequest(
    val filter: JsonObject? = null,
    val sorts: List<JsonObject>? = null,
    @SerialName("start_cursor") val startCursor: String? = null,
    @SerialName("page_size") val pageSize: Int? = null
) {
    /**
     * Creates an empty query request (returns all pages)
     */
    companion object {
        fun empty() = DatabaseQueryRequest()
    }
}

/**
 * Database Query Response
 * 
 * Response from POST /v1/databases/{database_id}/query
 * Contains a list of pages from the database.
 */
@Serializable
data class DatabaseQueryResponse(
    @SerialName("object") val objectType: String, // Always "list"
    val results: List<NotionPage>,
    @SerialName("has_more") val hasMore: Boolean,
    @SerialName("next_cursor") val nextCursor: String? = null
)

/**
 * Page Object
 * 
 * Represents a single page in Notion. Required fields only.
 * Pages in a database are stored as page objects with properties.
 */
@Serializable
data class NotionPage(
    @SerialName("object") val objectType: String, // Always "page"
    val id: String,
    @SerialName("created_time") val createdTime: String, // ISO 8601 format
    @SerialName("last_edited_time") val lastEditedTime: String, // ISO 8601 format
    @SerialName("created_by") val createdBy: NotionUser,
    @SerialName("last_edited_by") val lastEditedBy: NotionUser,
    val parent: NotionParent,
    val archived: Boolean,
    val properties: Map<String, NotionProperty>
)

/**
 * User Object
 * 
 * Required fields only. Represents the user who created or edited a page.
 */
@Serializable
data class NotionUser(
    @SerialName("object") val objectType: String, // Always "user"
    val id: String
)

@Serializable
data class NotionIcon(
    val type: String,
    val emoji: String? = null
)

@Serializable
data class NotionFile(
    val type: String,
    val external: NotionFileExternal? = null,
    @SerialName("file") val file: NotionFileData? = null
)

@Serializable
data class NotionFileExternal(
    val url: String
)

@Serializable
data class NotionFileData(
    val url: String,
    @SerialName("expiry_time") val expiryTime: String? = null
)

/**
 * Property Object
 * 
 * Represents a property value in a page. The structure varies by property type.
 * Only fields relevant to title, select, and date properties are shown.
 */
@Serializable
data class NotionProperty(
    val id: String,
    val type: String,
    // For title property (type = "title")
    val title: List<NotionRichText>? = null,
    // For select property (type = "select")
    val select: NotionSelect? = null,
    // For date property (type = "date")
    val date: NotionDate? = null
)

/**
 * Rich Text Object
 * 
 * Used in title properties. Required fields only.
 * Each rich text object represents a segment of formatted text.
 */
@Serializable
data class NotionRichText(
    val type: String, // Usually "text"
    val text: NotionText,
    @SerialName("plain_text") val plainText: String,
    val href: String? = null
)

/**
 * Text Content Object
 * 
 * Contains the actual text content within a rich text object.
 */
@Serializable
data class NotionText(
    val content: String,
    val link: String? = null
)

/**
 * Select Value Object
 * 
 * Represents the selected option in a select property.
 * Required fields only.
 */
@Serializable
data class NotionSelect(
    val name: String, // Required: the name of the selected option
    val color: String? = null, // Optional: color of the option
    val id: String? = null // Optional: unique identifier for the option
)

/**
 * Date Value Object
 * 
 * Represents a date or date range in a date property.
 * Required fields only.
 */
@Serializable
data class NotionDate(
    val start: String, // Required: ISO 8601 format date string
    val end: String? = null, // Optional: end date for date ranges
    @SerialName("time_zone") val timeZone: String? = null // Optional: IANA timezone
)

/**
 * Parent Object
 * 
 * Specifies the parent of a page. The type field determines which other field is present.
 * Required fields only.
 */
@Serializable
data class NotionParent(
    val type: String, // Required: "database_id", "page_id", "workspace", or "block_id"
    @SerialName("database_id") val databaseId: String? = null, // When type = "database_id"
    @SerialName("page_id") val pageId: String? = null, // When type = "page_id"
    @SerialName("workspace") val workspace: Boolean? = null, // When type = "workspace" (always true)
    @SerialName("block_id") val blockId: String? = null // When type = "block_id"
)

/**
 * VALUE EXTRACTION GUIDE
 * 
 * How to extract values from Notion properties:
 * 
 * 1. NAME (Title Property):
 *    - Access: page.properties["Name"]
 *    - Check: property.type == "title"
 *    - Extract: property.title?.joinToString("") { it.plainText }
 *      OR: property.title?.joinToString("") { it.text.content }
 *    - The title is an array of rich text objects. Concatenate all plain_text values.
 * 
 * 2. STATUS (Select Property):
 *    - Access: page.properties["Status"]
 *    - Check: property.type == "select"
 *    - Extract: property.select?.name
 *    - Note: property.select may be null if no option is selected.
 * 
 * 3. DUE (Date Property):
 *    - Access: page.properties["Due"]
 *    - Check: property.type == "date"
 *    - Extract: property.date?.start
 *    - Note: property.date may be null if no date is set.
 *      The start field is required when date is present.
 *      Use property.date?.end for date ranges.
 */

/**
 * Page Update Request
 * 
 * Request body for PATCH /v1/pages/{page_id}
 * All fields are optional. Only provided fields will be updated.
 * 
 * @see https://developers.notion.com/reference/patch-page
 */
@Serializable
data class PageUpdateRequest(
    /**
     * Properties to update. Each key is the property name or ID.
     * Values are property-specific update objects.
     */
    val properties: Map<String, JsonObject>? = null,
    /**
     * Icon to set for the page. Set to null to remove icon.
     */
    val icon: NotionIcon? = null,
    /**
     * Cover to set for the page. Set to null to remove cover.
     */
    val cover: NotionFile? = null,
    /**
     * Archive or restore the page. true = archived, false = restored.
     */
    val archived: Boolean? = null
)

/**
 * Property Update Objects
 * 
 * These are used within PageUpdateRequest.properties map.
 * Each property type has its own update structure.
 */

/**
 * Title Property Update
 * 
 * Used to update a title property.
 * Example: properties["Name"] = JsonObject { put("title", titleUpdateJson) }
 */
@Serializable
data class TitlePropertyUpdate(
    val title: List<NotionRichText>
)

/**
 * Select Property Update
 * 
 * Used to update a select property.
 * Set name to null to clear the selection.
 */
@Serializable
data class SelectPropertyUpdate(
    val select: NotionSelect?
)

/**
 * Date Property Update
 * 
 * Used to update a date property.
 * Set date to null to clear the date.
 */
@Serializable
data class DatePropertyUpdate(
    val date: NotionDate?
)

/**
 * Rich Text Property Update
 * 
 * Used to update a rich text property.
 */
@Serializable
data class RichTextPropertyUpdate(
    @SerialName("rich_text") val richText: List<NotionRichText>
)

/**
 * Number Property Update
 * 
 * Used to update a number property.
 * Set number to null to clear the number.
 */
@Serializable
data class NumberPropertyUpdate(
    val number: Double?
)

/**
 * Checkbox Property Update
 * 
 * Used to update a checkbox property.
 */
@Serializable
data class CheckboxPropertyUpdate(
    val checkbox: Boolean
)

/**
 * Block Update Request
 * 
 * Request body for PATCH /v1/blocks/{block_id}
 * Only one block type field should be provided.
 * 
 * @see https://developers.notion.com/reference/update-a-block
 */
@Serializable
data class BlockUpdateRequest(
    /**
     * Update a paragraph block
     */
    val paragraph: ParagraphBlockUpdate? = null,
    /**
     * Update a heading_1 block
     */
    @SerialName("heading_1") val heading1: HeadingBlockUpdate? = null,
    /**
     * Update a heading_2 block
     */
    @SerialName("heading_2") val heading2: HeadingBlockUpdate? = null,
    /**
     * Update a heading_3 block
     */
    @SerialName("heading_3") val heading3: HeadingBlockUpdate? = null,
    /**
     * Update a bulleted_list_item block
     */
    @SerialName("bulleted_list_item") val bulletedListItem: ListItemBlockUpdate? = null,
    /**
     * Update a numbered_list_item block
     */
    @SerialName("numbered_list_item") val numberedListItem: ListItemBlockUpdate? = null,
    /**
     * Update a to_do block
     */
    @SerialName("to_do") val toDo: ToDoBlockUpdate? = null,
    /**
     * Update a toggle block
     */
    val toggle: ToggleBlockUpdate? = null,
    /**
     * Archive or restore the block. true = archived, false = restored.
     */
    val archived: Boolean? = null
)

/**
 * Paragraph Block Update
 * 
 * Content for updating a paragraph block.
 */
@Serializable
data class ParagraphBlockUpdate(
    @SerialName("rich_text") val richText: List<NotionRichText>
)

/**
 * Heading Block Update
 * 
 * Content for updating heading_1, heading_2, or heading_3 blocks.
 */
@Serializable
data class HeadingBlockUpdate(
    @SerialName("rich_text") val richText: List<NotionRichText>
)

/**
 * List Item Block Update
 * 
 * Content for updating bulleted_list_item or numbered_list_item blocks.
 */
@Serializable
data class ListItemBlockUpdate(
    @SerialName("rich_text") val richText: List<NotionRichText>
)

/**
 * To-Do Block Update
 * 
 * Content for updating a to_do block.
 */
@Serializable
data class ToDoBlockUpdate(
    @SerialName("rich_text") val richText: List<NotionRichText>,
    val checked: Boolean? = null
)

/**
 * Toggle Block Update
 * 
 * Content for updating a toggle block.
 */
@Serializable
data class ToggleBlockUpdate(
    @SerialName("rich_text") val richText: List<NotionRichText>
)
