package dev.local.bedrockchat.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Lets Claude look through the user's earlier chats on this phone (the search_chats and read_chat tools).
 * The chat that is open now is left out: Claude has it already. It blocks, so call it from a background thread.
 */
class ChatSearch(private val store: ChatStore, private val currentId: String) {

    /** The best matching chats for [query], with a short excerpt from each, as text for Claude. */
    fun search(query: String): String {
        val words = query.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 2 }.distinct()
        if (words.isEmpty()) error("Give one or more keywords.")
        val hits = chats().mapNotNull { chat ->
            val titleScore = words.count { chat.title.lowercase().contains(it) }
            val messages = chat.messages.mapNotNull { m ->
                val text = m.text.lowercase()
                words.count { text.contains(it) }.takeIf { it > 0 }?.let { m to it }
            }
            if (messages.isEmpty() && titleScore == 0) return@mapNotNull null
            // All words in one message count most. Then the title, then how often the chat mentions them.
            val score = (messages.maxOfOrNull { it.second } ?: 0) * 10 + titleScore * 5 + minOf(messages.size, 5)
            Triple(chat, score, messages.sortedByDescending { it.second }.take(2).map { it.first })
        }.sortedWith(compareByDescending<Triple<Conversation, Int, List<ChatMessage>>> { it.second }.thenByDescending { it.first.updatedAt })
            .take(MAX_RESULTS)
        if (hits.isEmpty()) return "No earlier chats match \"$query\"."
        return hits.joinToString("\n\n") { (chat, _, messages) ->
            "Chat \"${chat.title.ifBlank { "Untitled" }}\" (id ${chat.id}, ${day(chat.updatedAt)}):\n" +
                messages.joinToString("\n") { m -> "  ${who(m)}: " + excerpt(m.text, words) }
        } + "\n\nUse read_chat with an id to read one of these chats in full."
    }

    /** One earlier chat as plain text for Claude, cut at a limit. */
    fun read(id: String): String {
        val chat = chats().firstOrNull { it.id == id } ?: error("No chat has the id $id. Use an id from search_chats.")
        val body = chat.messages.filter { it.text.isNotBlank() }.joinToString("\n\n") { "${who(it)}: ${it.text.trim()}" }
        val cut = body.length > MAX_CHAT_CHARS
        return "Chat \"${chat.title.ifBlank { "Untitled" }}\" (${day(chat.createdAt)} to ${day(chat.updatedAt)}):\n\n" +
            body.take(MAX_CHAT_CHARS) + if (cut) "\n\n[The chat is longer. Only the first part is shown.]" else ""
    }

    fun titleOf(id: String): String? = chats().firstOrNull { it.id == id }?.title?.ifBlank { null }

    private fun chats() = store.allBlocking().filter { it.id != currentId && it.messages.isNotEmpty() }

    private fun who(m: ChatMessage) = if (m.role == Role.USER) "User" else "Claude"

    private fun day(millis: Long) = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(millis))

    /** About 300 characters around the first keyword in [text]. */
    private fun excerpt(text: String, words: List<String>): String {
        val flat = text.replace(Regex("\\s+"), " ").trim()
        val at = words.mapNotNull { w -> flat.indexOf(w, ignoreCase = true).takeIf { it >= 0 } }.minOrNull() ?: 0
        val start = (at - 100).coerceAtLeast(0)
        val end = (at + 200).coerceAtMost(flat.length)
        return (if (start > 0) "…" else "") + flat.substring(start, end) + (if (end < flat.length) "…" else "")
    }

    private companion object {
        const val MAX_RESULTS = 5
        /** About 7 500 tokens. A result stays in the chat, and every later message sends it again. */
        const val MAX_CHAT_CHARS = 30_000
    }
}
