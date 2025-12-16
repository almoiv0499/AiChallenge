package org.example.notion

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NotionPage(
    @SerialName("object") val objectType: String = "page",
    val id: String,
    @SerialName("created_time") val createdTime: String? = null,
    @SerialName("last_edited_time") val lastEditedTime: String? = null,
    @SerialName("created_by") val createdBy: NotionUser? = null,
    @SerialName("last_edited_by") val lastEditedBy: NotionUser? = null,
    val archived: Boolean = false,
    @SerialName("in_trash") val inTrash: Boolean = false,
    val icon: NotionIcon? = null,
    val cover: NotionFile? = null,
    val properties: Map<String, NotionProperty> = emptyMap(),
    val parent: NotionParent? = null,
    val url: String? = null,
    @SerialName("public_url") val publicUrl: String? = null
)

@Serializable
data class NotionUser(
    @SerialName("object") val objectType: String = "user",
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

@Serializable
data class NotionProperty(
    val id: String,
    val type: String,
    val title: List<NotionRichText>? = null,
    val richText: List<NotionRichText>? = null,
    val number: Double? = null,
    val select: NotionSelect? = null,
    val status: NotionStatus? = null,
    val date: NotionDate? = null,
    val checkbox: Boolean? = null,
    val url: String? = null,
    val email: String? = null,
    val phoneNumber: String? = null
)

@Serializable
data class NotionRichText(
    val type: String,
    val text: NotionText? = null,
    @SerialName("plain_text") val plainText: String? = null
)

@Serializable
data class NotionText(
    val content: String,
    val link: String? = null
)

@Serializable
data class NotionSelect(
    val id: String? = null,
    val name: String,
    val color: String? = null
)

@Serializable
data class NotionStatus(
    val id: String? = null,
    val name: String,
    val color: String? = null
)

@Serializable
data class NotionDate(
    val start: String? = null,
    val end: String? = null,
    @SerialName("time_zone") val timeZone: String? = null
)

@Serializable
data class NotionParent(
    val type: String,
    @SerialName("page_id") val pageId: String? = null,
    @SerialName("database_id") val databaseId: String? = null,
    @SerialName("workspace") val workspace: Boolean? = null,
    @SerialName("data_source_id") val dataSourceId: String? = null
)
