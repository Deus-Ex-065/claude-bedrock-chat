package dev.local.bedrockchat

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.local.bedrockchat.data.Attachment
import dev.local.bedrockchat.data.AttachmentKind
import dev.local.bedrockchat.data.ChatMessage
import dev.local.bedrockchat.data.ClaudeService
import dev.local.bedrockchat.data.ColorMode
import dev.local.bedrockchat.data.Conversation
import dev.local.bedrockchat.data.ConversationSummary
import dev.local.bedrockchat.data.Credentials
import dev.local.bedrockchat.data.Effort
import dev.local.bedrockchat.data.Routing
import dev.local.bedrockchat.data.Role
import dev.local.bedrockchat.data.StreamEvent
import dev.local.bedrockchat.data.ChatTools
import dev.local.bedrockchat.data.ChatSearch
import dev.local.bedrockchat.data.ToolCall
import dev.local.bedrockchat.data.ToolStatus
import dev.local.bedrockchat.data.TITLE_MODEL_ID
import dev.local.bedrockchat.data.modelById
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class ChatUiState(
    val conversation: Conversation = Conversation(),
    val incognito: Boolean = false,
    val streamingId: String? = null,
    /** True while the request is sent but no output has arrived yet. */
    val waiting: Boolean = false,
    val pending: List<Attachment> = emptyList(),
    val modelId: String,
    val effort: Effort,
) {
    val isStreaming get() = streamingId != null
}

class ChatViewModel(app: Application) : AndroidViewModel(app) {
    private val settings = (app as App).settings
    private val store = (app as App).store
    private val memory = (app as App).memory

    private val _credentials = MutableStateFlow(settings.credentials())
    val credentials: StateFlow<Credentials?> = _credentials.asStateFlow()

    private val _colorMode = MutableStateFlow(settings.colorMode)
    val colorMode: StateFlow<ColorMode> = _colorMode.asStateFlow()

    private val _haptics = MutableStateFlow(settings.hapticFeedback)
    val haptics: StateFlow<Boolean> = _haptics.asStateFlow()

    private val _displayName = MutableStateFlow(settings.displayName)
    val displayName: StateFlow<String> = _displayName.asStateFlow()

    private val _state = MutableStateFlow(ChatUiState(modelId = settings.modelId, effort = settings.effort))
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    val summaries: StateFlow<List<ConversationSummary>> = store.summaries

    private val _messages = MutableStateFlow<String?>(null)
    /** One-shot notices for a snackbar. */
    val notices: StateFlow<String?> = _messages.asStateFlow()

    private var service: ClaudeService? = settings.credentials()?.let(::ClaudeService)
    private var streamJob: Job? = null

    init {
        viewModelScope.launch { store.refresh() }
    }

    fun consumeNotice() { _messages.value = null }

    // ---- Sign in ----

    /**
     * Finds a routing that accepts this key in this region, trying [routings] in order.
     * Returns the credentials, the service, and the model that worked.
     */
    private suspend fun connect(
        apiKey: String,
        region: String,
        modelId: String,
        routings: List<Routing>,
    ): Triple<Credentials, ClaudeService, String> {
        var firstError: Exception? = null
        for (routing in routings.distinct()) {
            val credentials = Credentials(apiKey, region, routing)
            val candidate = ClaudeService(credentials)
            try {
                return Triple(credentials, candidate, candidate.validate(modelId))
            } catch (e: Exception) {
                Log.w("BedrockChat", "${routing.name} in $region failed: ${e.message}")
                if (firstError == null) firstError = e
            }
        }
        throw firstError!!
    }

    /** Switches routing. Only the chosen routing is tried; returns an error or null. */
    suspend fun setRouting(routing: Routing): String? {
        val current = _credentials.value ?: return "Not signed in."
        return try {
            val wanted = _state.value.modelId
            val (updated, candidate, working) = connect(current.apiKey, current.region, wanted, listOf(routing))
            settings.saveCredentials(updated)
            service = candidate
            _credentials.value = updated
            setModel(working)
            noteModelSwitch(wanted, working)
            null
        } catch (e: Exception) {
            ClaudeService.rawError(e)
        }
    }

    suspend fun signIn(apiKey: String, region: String, modelId: String): String? {
        return try {
            val (credentials, candidate, working) = connect(apiKey.trim(), region, modelId, listOf(Routing.GLOBAL, Routing.IN_REGION))
            settings.saveCredentials(credentials)
            service = candidate
            setModel(working)
            noteModelSwitch(modelId, working)
            _credentials.value = credentials
            null
        } catch (e: Exception) {
            ClaudeService.rawError(e)
        }
    }

    /** Changes the key and/or region after sign-in. Checks the new values first; returns an error or null. */
    suspend fun updateCredentials(apiKey: String? = null, region: String? = null): String? {
        val current = _credentials.value ?: return "Not signed in."
        return try {
            val wanted = _state.value.modelId
            // Only the current routing is tried; if it fails, nothing changes and the error is shown.
            val (updated, candidate, working) = connect(
                apiKey?.trim()?.takeIf { it.isNotEmpty() } ?: current.apiKey, region ?: current.region, wanted,
                listOf(current.routing),
            )
            settings.saveCredentials(updated)
            service = candidate
            _credentials.value = updated
            setModel(working)
            noteModelSwitch(wanted, working)
            null
        } catch (e: Exception) {
            ClaudeService.rawError(e)
        }
    }

    private fun noteModelSwitch(wanted: String, working: String) {
        if (wanted != working) {
            _messages.value = "${modelById(wanted).name} isn't available for this key here. Switched to ${modelById(working).name}."
        }
    }

    fun signOut() {
        stop()
        settings.signOut()
        service = null
        _credentials.value = null
        newChat()
    }

    // ---- Preferences ----

    fun setModel(id: String) { settings.modelId = id; _state.update { it.copy(modelId = id) } }
    fun setEffort(effort: Effort) { settings.effort = effort; _state.update { it.copy(effort = effort) } }
    fun setColorMode(mode: ColorMode) { settings.colorMode = mode; _colorMode.value = mode }
    fun setHaptics(on: Boolean) { settings.hapticFeedback = on; _haptics.value = on }
    fun setDisplayName(name: String) { settings.displayName = name.trim(); _displayName.value = name.trim() }

    private val _askName = MutableStateFlow(!settings.askedName && settings.displayName.isBlank())
    /** True until the user answers or skips the name question that shows once after sign-in. */
    val askName: StateFlow<Boolean> = _askName.asStateFlow()
    fun answerName(name: String) {
        if (name.isNotBlank()) setDisplayName(name)
        settings.askedName = true
        _askName.value = false
    }

    private val _chatNotifications = MutableStateFlow(settings.chatNotifications)
    /** "Notify when Claude responds": keeps a reply running in the background and notifies when it finishes. */
    val chatNotifications: StateFlow<Boolean> = _chatNotifications.asStateFlow()

    private val _readLinks = MutableStateFlow(settings.readLinks)
    /** "Read links": Claude can open web pages and PDFs, downloaded by the phone. */
    val readLinks: StateFlow<Boolean> = _readLinks.asStateFlow()
    fun setReadLinks(on: Boolean) { settings.readLinks = on; _readLinks.value = on }

    private val _memoryOn = MutableStateFlow(settings.memory)
    /** "Memory": Claude remembers things across chats. Incognito chats do not read or change it. */
    val memoryOn: StateFlow<Boolean> = _memoryOn.asStateFlow()
    val memoryItems: StateFlow<List<String>> = memory.items
    fun setMemory(on: Boolean) { settings.memory = on; _memoryOn.value = on }

    private val _searchChats = MutableStateFlow(settings.searchChats)
    /** "Search chats": Claude can look through earlier chats. Not in incognito chats. */
    val searchChats: StateFlow<Boolean> = _searchChats.asStateFlow()
    fun setSearchChats(on: Boolean) { settings.searchChats = on; _searchChats.value = on }
    fun deleteMemoryItem(index: Int) = memory.removeAt(index)
    fun clearMemory() = memory.clear()

    private val _offerChatNotifications = MutableStateFlow(!settings.askedChatNotifications && !settings.chatNotifications)
    /** True until the "Get notified when Claude responds" card is answered or closed. */
    val offerChatNotifications: StateFlow<Boolean> = _offerChatNotifications.asStateFlow()

    fun setChatNotifications(on: Boolean) {
        settings.chatNotifications = on
        settings.askedChatNotifications = true
        _chatNotifications.value = on
        _offerChatNotifications.value = false
    }

    fun dismissChatNotificationsOffer() {
        settings.askedChatNotifications = true
        _offerChatNotifications.value = false
    }

    // ---- Conversations ----

    fun newChat(incognito: Boolean = false) {
        stop()
        discardIncognito()
        _state.update { it.copy(conversation = Conversation(), incognito = incognito, pending = emptyList()) }
    }

    fun openChat(id: String) {
        viewModelScope.launch {
            val conversation = store.load(id) ?: return@launch
            stop()
            discardIncognito()
            _state.update { it.copy(conversation = conversation, incognito = false, pending = emptyList()) }
        }
    }

    fun deleteChat(id: String) {
        viewModelScope.launch {
            store.delete(id)
            if (_state.value.conversation.id == id) newChat()
        }
    }

    fun renameChat(id: String, title: String) {
        viewModelScope.launch {
            val conversation = store.load(id) ?: return@launch
            val renamed = conversation.copy(title = title.trim())
            store.save(renamed)
            if (_state.value.conversation.id == id) _state.update { it.copy(conversation = renamed) }
        }
    }

    fun toggleStar(id: String) {
        viewModelScope.launch {
            val conversation = store.load(id) ?: return@launch
            val updated = conversation.copy(starred = !conversation.starred)
            store.save(updated)
            if (_state.value.conversation.id == id) _state.update { it.copy(conversation = updated) }
        }
    }

    fun deleteAllChats() {
        viewModelScope.launch {
            store.deleteAll()
            newChat()
        }
    }

    private fun discardIncognito() {
        val current = _state.value
        if (current.incognito) {
            current.conversation.messages.flatMap { it.attachments }.forEach { File(it.path).delete() }
        }
    }

    // ---- Attachments ----

    fun addAttachments(uris: List<Uri>) {
        viewModelScope.launch {
            for (uri in uris) {
                val result = runCatching { importAttachment(uri) }
                result.onSuccess { attachment -> _state.update { it.copy(pending = it.pending + attachment) } }
                result.onFailure { _messages.value = it.message ?: "Could not add the file." }
            }
        }
    }

    fun removeAttachment(id: String) {
        _state.update { s ->
            s.pending.firstOrNull { it.id == id }?.let { File(it.path).delete() }
            s.copy(pending = s.pending.filterNot { it.id == id })
        }
    }

    private suspend fun importAttachment(uri: Uri): Attachment = withContext(Dispatchers.IO) {
        val resolver = getApplication<Application>().contentResolver
        var name = "file"
        var size = -1L
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getString(0)?.let { name = it }
                if (!c.isNull(1)) size = c.getLong(1)
            }
        }
        val mime = resolver.getType(uri) ?: guessMime(name)
        val id = UUID.randomUUID().toString()
        when {
            mime.startsWith("image/") -> {
                val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                    ?: error("Could not read the image.")
                val scaled = downscale(bitmap, 1568)
                val out = File(store.attachmentsDir, "$id.jpg")
                out.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                Attachment(id = id, kind = AttachmentKind.IMAGE, name = name, mediaType = "image/jpeg",
                    path = out.path, sizeBytes = out.length())
            }
            mime == "application/pdf" -> {
                if (size > MAX_PDF_BYTES) error("PDFs must be smaller than 15 MB.")
                val out = File(store.attachmentsDir, "$id.pdf")
                resolver.openInputStream(uri)?.use { input -> out.outputStream().use { input.copyTo(it) } }
                if (out.length() > MAX_PDF_BYTES) { out.delete(); error("PDFs must be smaller than 15 MB.") }
                Attachment(id = id, kind = AttachmentKind.PDF, name = name, mediaType = mime, path = out.path, sizeBytes = out.length())
            }
            isTextType(mime, name) -> {
                val out = File(store.attachmentsDir, "$id.txt")
                resolver.openInputStream(uri)?.use { input -> out.outputStream().use { input.copyTo(it) } }
                if (out.length() > MAX_TEXT_BYTES) { out.delete(); error("Text files must be smaller than 2 MB.") }
                Attachment(id = id, kind = AttachmentKind.TEXT, name = name, mediaType = "text/plain", path = out.path, sizeBytes = out.length())
            }
            else -> error("This file type isn't supported. Use images, PDFs, or text files.")
        }
    }

    // ---- Sending ----

    fun send(text: String) {
        val s = _state.value
        if (s.isStreaming || (text.isBlank() && s.pending.isEmpty())) return
        val user = ChatMessage(role = Role.USER, text = text.trim(), attachments = s.pending)
        val conversation = s.conversation.copy(messages = s.conversation.messages + user, updatedAt = System.currentTimeMillis())
        _state.update { it.copy(conversation = conversation, pending = emptyList()) }
        persist()
        generate()
    }

    fun retry() {
        val s = _state.value
        if (s.isStreaming) return
        val messages = s.conversation.messages
        if (messages.lastOrNull()?.role != Role.ASSISTANT) return
        _state.update { it.copy(conversation = it.conversation.copy(messages = messages.dropLast(1))) }
        generate()
    }

    /** Replaces a user message, drops everything after it, and asks again. */
    fun editAndResend(messageId: String, newText: String) {
        val s = _state.value
        if (s.isStreaming || newText.isBlank()) return
        val index = s.conversation.messages.indexOfFirst { it.id == messageId }
        if (index < 0) return
        val edited = s.conversation.messages[index].copy(id = UUID.randomUUID().toString(), text = newText.trim())
        val messages = s.conversation.messages.take(index) + edited
        _state.update { it.copy(conversation = it.conversation.copy(messages = messages, updatedAt = System.currentTimeMillis())) }
        persist()
        generate()
    }

    fun stop() {
        streamJob?.cancel()
        streamJob = null
    }

    private fun generate() {
        val service = service ?: return
        val s = _state.value
        val history = s.conversation.messages
        val reply = ChatMessage(role = Role.ASSISTANT, modelId = s.modelId)
        _state.update {
            it.copy(
                conversation = it.conversation.copy(messages = history + reply),
                streamingId = reply.id,
                waiting = true,
            )
        }

        ReplyService.start(getApplication())
        streamJob = viewModelScope.launch {
            var outcome: String? = null // The reply text for the notification; null when you stopped it.
            var failed = false
            val text = StringBuilder()
            val thinking = StringBuilder()
            var thinkingStart = 0L
            var thinkingMillis = 0L
            var lastPush = 0L
            var final: ChatMessage? = null

            fun push(force: Boolean = false) {
                val now = System.currentTimeMillis()
                if (!force && now - lastPush < 50) return
                lastPush = now
                updateMessage(reply.id) {
                    it.copy(text = text.toString(), thinking = thinking.toString(), thinkingMillis = thinkingMillis)
                }
            }

            try {
                service.stream(s.modelId, s.effort, history, settings.displayName, ChatTools(
                    readLinks = settings.readLinks,
                    memory = memory.takeIf { settings.memory && !s.incognito },
                    chats = ChatSearch(store, s.conversation.id).takeIf { settings.searchChats && !s.incognito },
                )).collect { event ->
                    when (event) {
                        is StreamEvent.ThinkingDelta -> {
                            if (thinkingStart == 0L) thinkingStart = System.currentTimeMillis()
                            thinking.append(event.text)
                            thinkingMillis = System.currentTimeMillis() - thinkingStart
                            _state.update { it.copy(waiting = false) }
                            push()
                        }
                        is StreamEvent.TextDelta -> {
                            if (thinkingStart != 0L && text.isEmpty()) thinkingMillis = System.currentTimeMillis() - thinkingStart
                            text.append(event.text)
                            _state.update { it.copy(waiting = false) }
                            push()
                        }
                        is StreamEvent.ToolStarted -> {
                            _state.update { it.copy(waiting = false) }
                            push(force = true)
                            updateMessage(reply.id) {
                                it.copy(toolCalls = it.toolCalls + ToolCall(event.id, event.name, event.label, ToolStatus.RUNNING))
                            }
                        }
                        is StreamEvent.ToolFinished -> updateMessage(reply.id) {
                            it.copy(toolCalls = it.toolCalls.map { call ->
                                if (call.id != event.id) call
                                else call.copy(label = event.label, status = if (event.failed) ToolStatus.FAILED else ToolStatus.DONE, detail = event.detail)
                            })
                        }
                        is StreamEvent.Done -> {
                            val refusal = ClaudeService.isRefusal(event.message)
                            final = currentMessage(reply.id)?.copy(
                                text = text.toString(),
                                thinking = thinking.toString(),
                                thinkingMillis = thinkingMillis,
                                apiContent = ClaudeService.encodeContent(event.message),
                                apiTurns = event.turns,
                                error = if (refusal) "Claude declined to respond to this request. Try rephrasing it, or pick another model." else null,
                            )
                        }
                    }
                }
                final?.let { done -> updateMessage(reply.id) { done } }
                outcome = text.toString()
                failed = final?.error != null
            } catch (e: kotlinx.coroutines.CancellationException) {
                updateMessage(reply.id) {
                    it.copy(text = text.toString(), thinking = thinking.toString(), thinkingMillis = thinkingMillis, stopped = true).endTools()
                }
                throw e
            } catch (e: Exception) {
                updateMessage(reply.id) {
                    it.copy(text = text.toString(), thinking = thinking.toString(), stopped = true, error = ClaudeService.rawError(e)).endTools()
                }
                outcome = text.toString()
                failed = true
            } finally {
                _state.update { it.copy(streamingId = null, waiting = false) }
                val done = _state.value
                ReplyService.finish(getApplication(), done.conversation.id, done.conversation.title, outcome, failed, done.incognito)
                persist()
                maybeTitle()
            }
        }
    }

    /** Marks tool calls that were still running when the reply stopped. */
    private fun ChatMessage.endTools() = copy(toolCalls = toolCalls.map {
        if (it.status == ToolStatus.RUNNING) it.copy(label = it.label.removeSuffix("…"), status = ToolStatus.FAILED, detail = "Stopped") else it
    })

    private fun currentMessage(id: String) = _state.value.conversation.messages.firstOrNull { it.id == id }

    private fun updateMessage(id: String, transform: (ChatMessage) -> ChatMessage) {
        _state.update { s ->
            s.copy(conversation = s.conversation.copy(messages = s.conversation.messages.map { if (it.id == id) transform(it) else it }))
        }
    }

    private fun persist() {
        val s = _state.value
        if (s.incognito) return
        val conversation = s.conversation
        viewModelScope.launch(Dispatchers.IO + kotlinx.coroutines.NonCancellable) { store.save(conversation) }
    }

    private fun maybeTitle() {
        val s = _state.value
        val conversation = s.conversation
        if (s.incognito || conversation.title.isNotBlank()) return
        val firstUser = conversation.messages.firstOrNull { it.role == Role.USER } ?: return
        val firstReply = conversation.messages.firstOrNull { it.role == Role.ASSISTANT && it.text.isNotBlank() } ?: return
        val service = service ?: return
        viewModelScope.launch {
            val userText = firstUser.text.ifBlank { "(attachment)" }
            // Prefer the fast model; fall back to the chat's model if the key has no access to it.
            val title = (listOf(TITLE_MODEL_ID, s.modelId).distinct()).firstNotNullOfOrNull { model ->
                runCatching { service.title(model, userText, firstReply.text) }
                    .onFailure { Log.w("BedrockChat", "Title with $model failed: ${ClaudeService.friendlyError(it)}") }
                    .getOrNull()?.takeIf { it.isNotBlank() }
            }
                ?: firstUser.text.take(40).ifBlank { "New chat" }
            val current = _state.value
            if (current.conversation.id == conversation.id) {
                _state.update { it.copy(conversation = it.conversation.copy(title = title)) }
                persist()
            } else {
                store.load(conversation.id)?.let { store.save(it.copy(title = title)) }
            }
        }
    }

    private companion object {
        const val MAX_PDF_BYTES = 15L * 1024 * 1024
        const val MAX_TEXT_BYTES = 2L * 1024 * 1024

        fun downscale(bitmap: Bitmap, maxEdge: Int): Bitmap {
            val edge = maxOf(bitmap.width, bitmap.height)
            if (edge <= maxEdge) return bitmap
            val scale = maxEdge.toFloat() / edge
            return Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
        }

        fun guessMime(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
            "pdf" -> "application/pdf"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            else -> "application/octet-stream"
        }

        val TEXT_EXTENSIONS = setOf(
            "txt", "md", "markdown", "csv", "tsv", "json", "xml", "yaml", "yml", "html", "htm", "css", "js", "ts",
            "kt", "kts", "java", "py", "rb", "go", "rs", "c", "h", "cpp", "hpp", "cs", "swift", "sh", "sql", "log",
            "ini", "toml", "gradle", "properties",
        )

        fun isTextType(mime: String, name: String) = mime.startsWith("text/") ||
            mime in setOf("application/json", "application/xml", "application/x-yaml", "application/javascript") ||
            name.substringAfterLast('.', "").lowercase() in TEXT_EXTENSIONS
    }
}
