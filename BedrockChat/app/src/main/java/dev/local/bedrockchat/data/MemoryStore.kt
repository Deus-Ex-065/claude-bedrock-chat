package dev.local.bedrockchat.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * What Claude remembers across chats: short items, one per line, in a file in app storage.
 * It stays on the phone. Claude reads it in the system prompt and changes it with the update_memory tool.
 */
class MemoryStore(dir: File) {
    private val file = File(dir, "memory.txt")
    private val _items = MutableStateFlow(read())
    val items: StateFlow<List<String>> = _items

    private fun read(): List<String> =
        runCatching { file.readLines().map { it.trim() }.filter { it.isNotEmpty() } }.getOrDefault(emptyList())

    @Synchronized
    private fun write(items: List<String>) {
        file.writeText(items.joinToString("\n"))
        _items.value = items
    }

    /** Adds one item. Returns an error text, or null when it was saved. */
    @Synchronized
    fun add(text: String): String? {
        val item = oneLine(text) ?: return "The item is empty."
        val items = _items.value
        if (items.any { it.equals(item, ignoreCase = true) }) return null
        if (item.length > MAX_ITEM_CHARS) return "The item is too long. Keep it under $MAX_ITEM_CHARS characters."
        if (items.sumOf { it.length + 1 } + item.length > MAX_TOTAL_CHARS) {
            return "Memory is full. Remove or merge items that are no longer needed first."
        }
        write(items + item)
        return null
    }

    /** Replaces the item that contains [old]. Returns an error text, or null when it was changed. */
    @Synchronized
    fun replace(old: String, text: String): String? {
        val item = oneLine(text) ?: return "The new text is empty. Use remove to delete an item."
        val at = find(old) ?: return notFound(old)
        if (at < 0) return ambiguous(old)
        write(_items.value.toMutableList().also { it[at] = item })
        return null
    }

    /** Removes the item that contains [old]. Returns an error text, or null when it was removed. */
    @Synchronized
    fun remove(old: String): String? {
        val at = find(old) ?: return notFound(old)
        if (at < 0) return ambiguous(old)
        removeAt(at)
        return null
    }

    @Synchronized
    fun removeAt(index: Int) {
        val items = _items.value
        if (index in items.indices) write(items.filterIndexed { i, _ -> i != index })
    }

    @Synchronized
    fun clear() = write(emptyList())

    /** The index of the one item that is or contains [text]; -1 when several match; null when none does. */
    private fun find(text: String): Int? {
        val wanted = oneLine(text) ?: return null
        val items = _items.value
        items.indexOfFirst { it.equals(wanted, ignoreCase = true) }.takeIf { it >= 0 }?.let { return it }
        val hits = items.indices.filter { items[it].contains(wanted, ignoreCase = true) }
        return when (hits.size) {
            0 -> null
            1 -> hits[0]
            else -> -1
        }
    }

    private fun notFound(text: String) = "No memory item contains \"$text\". Copy the text of the item exactly."
    private fun ambiguous(text: String) = "Several memory items contain \"$text\". Use more of the item's text."

    private fun oneLine(text: String) = text.replace(Regex("\\s+"), " ").trim().removePrefix("- ").takeIf { it.isNotEmpty() }

    companion object {
        const val MAX_ITEM_CHARS = 300
        /** About 1 500 tokens. The whole memory goes into every request, so it stays small. */
        const val MAX_TOTAL_CHARS = 6_000
    }
}
