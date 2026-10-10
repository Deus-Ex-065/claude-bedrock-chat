package dev.local.bedrockchat.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

@Serializable
enum class AttachmentKind { IMAGE, PDF, TEXT }

@Serializable
data class Attachment(
    val id: String = UUID.randomUUID().toString(),
    val kind: AttachmentKind,
    val name: String,
    val mediaType: String,
    /** Absolute path of the copy kept in app storage. */
    val path: String,
    val sizeBytes: Long,
)

@Serializable
enum class Role { USER, ASSISTANT }

@Serializable
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: Role,
    val text: String = "",
    val attachments: List<Attachment> = emptyList(),
    /** Summarized thinking shown in the expandable "Thought process" section. */
    val thinking: String = "",
    val thinkingMillis: Long = 0,
    /**
     * The assistant content blocks exactly as the API returned them (JSON of
     * List<ContentBlockParam>). Sent back unchanged so thinking blocks stay valid.
     */
    val apiContent: String? = null,
    val modelId: String? = null,
    val error: String? = null,
    val stopped: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
data class Conversation(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val messages: List<ChatMessage> = emptyList(),
    val starred: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

/** Summary row for the chats list. */
data class ConversationSummary(val id: String, val title: String, val updatedAt: Long, val starred: Boolean)

/** Stores each conversation as one JSON file in app-private storage. */
class ChatStore(context: Context) {
    private val dir = File(context.filesDir, "chats").apply { mkdirs() }
    val attachmentsDir = File(context.filesDir, "attachments").apply { mkdirs() }
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _summaries = MutableStateFlow<List<ConversationSummary>>(emptyList())
    val summaries: StateFlow<List<ConversationSummary>> = _summaries

    suspend fun refresh() = withContext(Dispatchers.IO) {
        _summaries.value = dir.listFiles { f -> f.extension == "json" }.orEmpty()
            .mapNotNull { file -> runCatching { json.decodeFromString<Conversation>(file.readText()) }.getOrNull() }
            .filter { it.messages.isNotEmpty() }
            .map { ConversationSummary(it.id, it.title.ifBlank { "New chat" }, it.updatedAt, it.starred) }
            .sortedWith(compareByDescending<ConversationSummary> { it.starred }.thenByDescending { it.updatedAt })
    }

    suspend fun load(id: String): Conversation? = withContext(Dispatchers.IO) {
        val file = File(dir, "$id.json")
        if (!file.exists()) null else runCatching { json.decodeFromString<Conversation>(file.readText()) }.getOrNull()
    }

    suspend fun save(conversation: Conversation) {
        withContext(Dispatchers.IO) {
            val tmp = File(dir, "${conversation.id}.json.tmp")
            tmp.writeText(json.encodeToString(conversation))
            tmp.renameTo(File(dir, "${conversation.id}.json"))
        }
        refresh()
    }

    suspend fun delete(id: String) {
        withContext(Dispatchers.IO) {
            load(id)?.messages?.flatMap { it.attachments }?.forEach { File(it.path).delete() }
            File(dir, "$id.json").delete()
        }
        refresh()
    }

    suspend fun deleteAll() {
        withContext(Dispatchers.IO) {
            dir.listFiles()?.forEach { it.delete() }
            attachmentsDir.listFiles()?.forEach { it.delete() }
        }
        refresh()
    }
}
