package dev.local.bedrockchat.data

import android.util.Base64
import com.anthropic.bedrock.backends.BedrockBackend
import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.jsonMapper
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.NotFoundException
import com.anthropic.errors.PermissionDeniedException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.helpers.MessageAccumulator
import com.anthropic.models.messages.Base64ImageSource
import com.anthropic.models.messages.Base64PdfSource
import com.anthropic.models.messages.CacheControlEphemeral
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.DocumentBlockParam
import com.anthropic.models.messages.ImageBlockParam
import com.anthropic.models.messages.Message
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.MessageParam
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.PlainTextSource
import com.anthropic.models.messages.StopReason
import com.anthropic.models.messages.TextBlockParam
import com.anthropic.models.messages.ThinkingConfigAdaptive
import com.anthropic.models.messages.ToolChoiceNone
import com.anthropic.models.messages.ToolResultBlockParam
import com.fasterxml.jackson.core.type.TypeReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.concurrent.thread
import software.amazon.awssdk.regions.Region

class NoGeoProfileException(val region: String) : IllegalArgumentException("No geographic profile for $region")

sealed interface StreamEvent {
    data class ThinkingDelta(val text: String) : StreamEvent
    data class TextDelta(val text: String) : StreamEvent
    data class ToolStarted(val id: String, val name: String, val label: String) : StreamEvent
    data class ToolFinished(val id: String, val label: String, val failed: Boolean, val detail: String?) : StreamEvent
    data class Done(val message: Message, val turns: List<ApiTurn>, val usage: TokenUsage) : StreamEvent
}

/**
 * Talks to Claude in Amazon Bedrock with a Bedrock API key. Global and geographic routing use
 * bedrock-runtime with an inference profile; in-region routing uses the bedrock-mantle Messages API endpoint.
 */
class ClaudeService(private val credentials: Credentials) {

    private val client: AnthropicClient = when (credentials.routing) {
        Routing.GLOBAL, Routing.GEOGRAPHIC -> AnthropicOkHttpClient.builder()
            .backend(BedrockBackend.builder().apiKey(credentials.apiKey).region(Region.of(credentials.region)).build())
        Routing.IN_REGION -> AnthropicOkHttpClient.builder()
            .baseUrl("https://bedrock-mantle.${credentials.region}.api.aws/anthropic")
            .apiKey(credentials.apiKey)
    }.timeout(Duration.ofMinutes(15)).maxRetries(2).build()

    /** Model IDs in the app are base IDs; bedrock-runtime needs an inference profile prefix. */
    private fun wire(modelId: String) = when (credentials.routing) {
        Routing.GLOBAL -> "global.$modelId"
        Routing.GEOGRAPHIC -> "${geoPrefix(credentials.region) ?: throw NoGeoProfileException(credentials.region)}.$modelId"
        Routing.IN_REGION -> modelId
    }

    /**
     * Streams one reply. When Claude asks for a tool, the tool runs on the phone and the result goes back in a
     * new request, until Claude answers without a tool. [StreamEvent.Done] carries the turns in between.
     */
    fun stream(
        modelId: String,
        effort: Effort,
        history: List<ChatMessage>,
        userName: String = "",
        tools: ChatTools = ChatTools(readLinks = false),
    ): Flow<StreamEvent> = callbackFlow {
        val messages = history.flatMap(::toParams).toMutableList()
        // Old tool turns need every tool definition, even for a tool that is off now. A call to it returns an error.
        val definitions = if (history.any { it.apiTurns.isNotEmpty() && !it.stopped }) ChatTools.ALL else tools.definitions
        val turns = mutableListOf<ApiTurn>()
        val cancelled = AtomicBoolean(false)
        val current = AtomicReference<AutoCloseable?>(null)

        val worker = thread(name = "claude-stream") {
            try {
                var wroteText = false
                var wroteThinking = false
                for (round in 0..MAX_TOOL_ROUNDS) {
                    val params = MessageCreateParams.builder()
                        .model(wire(modelId))
                        .maxTokens(64_000L)
                        .system(systemPrompt(modelId, userName, tools))
                        .messages(messages)
                        .thinking(ThinkingConfigAdaptive.builder().display(ThinkingConfigAdaptive.Display.SUMMARIZED).build())
                        .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.of(effort.apiValue)).build())
                        .cacheControl(CacheControlEphemeral.builder().build())
                        .apply {
                            definitions.forEach { addTool(it) }
                            // On the last round, or when the tools are off, Claude must answer with what it has.
                            if (definitions.isNotEmpty() && (round == MAX_TOOL_ROUNDS || tools.isEmpty)) {
                                toolChoice(ToolChoiceNone.builder().build())
                            }
                        }
                        .build()

                    val response = client.messages().createStreaming(params)
                    current.set(response)
                    if (cancelled.get()) { response.close(); return@thread }
                    val accumulator = MessageAccumulator.create()
                    var roundText = false
                    var roundThinking = false
                    response.use { r ->
                        r.stream().forEach { event ->
                            accumulator.accumulate(event)
                            event.contentBlockDelta().ifPresent { deltaEvent ->
                                val delta = deltaEvent.delta()
                                delta.text().ifPresent {
                                    // Text from a new round starts a new paragraph.
                                    if (!roundText) { roundText = true; if (wroteText) trySend(StreamEvent.TextDelta("\n\n")) }
                                    wroteText = true
                                    trySend(StreamEvent.TextDelta(it.text()))
                                }
                                delta.thinking().ifPresent {
                                    if (!roundThinking) { roundThinking = true; if (wroteThinking) trySend(StreamEvent.ThinkingDelta("\n\n")) }
                                    wroteThinking = true
                                    trySend(StreamEvent.ThinkingDelta(it.thinking()))
                                }
                            }
                        }
                    }
                    val message = accumulator.message()
                    val uses = message.content().mapNotNull { it.toolUse().orElse(null) }
                    // A reply cut off by max_tokens can hold a half-written tool call. Do not run it.
                    if (message.stopReason().orElse(null) != StopReason.TOOL_USE || uses.isEmpty()) {
                        val u = message.usage()
                        val usage = TokenUsage(
                            input = u.inputTokens(),
                            cacheRead = u.cacheReadInputTokens().orElse(0L),
                            cacheWrite = u.cacheCreationInputTokens().orElse(0L),
                            output = u.outputTokens(),
                        )
                        trySend(StreamEvent.Done(message, turns.toList(), usage))
                        close()
                        return@thread
                    }

                    val results = uses.map { use ->
                        val input = jsonMapper().writeValueAsString(use._input())
                        val label = tools.label(use.name(), input)
                        trySend(StreamEvent.ToolStarted(use.id(), use.name(), label.running))
                        val out = tools.run(use.name(), input)
                        if (cancelled.get()) return@thread
                        val detail = if (out.isError) out.blocks.firstOrNull()?.text()?.orElse(null)?.text() else null
                        trySend(StreamEvent.ToolFinished(use.id(), if (out.isError) label.failed else label.done, out.isError, detail))
                        ContentBlockParam.ofToolResult(
                            ToolResultBlockParam.builder()
                                .toolUseId(use.id())
                                .contentOfBlocks(out.blocks)
                                .apply { if (out.isError) isError(true) }
                                .build()
                        )
                    }
                    val assistant = message.content().map { it.toParam() }
                    turns += ApiTurn(Role.ASSISTANT, jsonMapper().writeValueAsString(assistant))
                    turns += ApiTurn(Role.USER, jsonMapper().writeValueAsString(results))
                    messages += MessageParam.builder().role(MessageParam.Role.ASSISTANT).contentOfBlockParams(assistant).build()
                    messages += MessageParam.builder().role(MessageParam.Role.USER).contentOfBlockParams(results).build()
                }
            } catch (e: Throwable) {
                if (!cancelled.get()) close(e)
            }
        }
        awaitClose {
            cancelled.set(true)
            runCatching { current.get()?.close() }
            worker.interrupt()
        }
    }.flowOn(Dispatchers.IO)

    /** Short title for the chats list, made by a fast model. */
    suspend fun title(modelId: String, firstUserText: String, firstReply: String): String = withContext(Dispatchers.IO) {
        val params = MessageCreateParams.builder()
            .model(wire(modelId))
            .maxTokens(2_000L)
            .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build())
            .addUserMessage(
                "Write a short title (2 to 6 words) for this conversation. Reply with the title only, " +
                    "no quotes or punctuation at the end.\n\nUser: ${firstUserText.take(2_000)}\n\n" +
                    "Assistant: ${firstReply.take(2_000)}"
            )
            .build()
        client.messages().create(params).content()
            .mapNotNull { block -> block.text().map { it.text() }.orElse(null) }
            .joinToString("").trim().trim('"').take(80)
    }

    /** Sends a tiny request to check that the key, region, and model work. */
    /**
     * Checks the key and region with one tiny request. Tries [preferredModelId] first; if that model
     * doesn't exist for this key and region, tries the other models. Returns the model that worked.
     */
    suspend fun validate(preferredModelId: String): String = withContext(Dispatchers.IO) {
        val candidates = (listOf(preferredModelId) + MODELS.map { it.id }).distinct()
        var lastNotFound: NotFoundException? = null
        for (modelId in candidates) {
            try {
                client.messages().create(
                    MessageCreateParams.builder()
                        .model(wire(modelId))
                        .maxTokens(64L)
                        .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build())
                        .addUserMessage("Reply with OK.")
                        .build()
                )
                return@withContext modelId
            } catch (e: NotFoundException) {
                lastNotFound = e
            }
        }
        throw lastNotFound ?: IllegalStateException("No model to check.")
    }

    /**
     * Bedrock sends no system prompt of its own, so the model does not know its name unless the app tells it.
     * The chat can switch models, so earlier replies may come from another model.
     */
    private fun systemPrompt(modelId: String, userName: String, tools: ChatTools): String {
        val today = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy"))
        val model = modelById(modelId).name
        return "You are Claude $model, an AI assistant made by Anthropic, chatting with the user in a mobile app " +
            "through Amazon Bedrock. If asked which model you are, say Claude $model. The user can switch models " +
            "during a chat, so earlier replies in this conversation may come from another Claude model. " +
            (if (userName.isNotBlank()) "The user's name is $userName. " else "") +
            tools.promptNote +
            "The current date is $today. Format replies with Markdown when it helps readability. " +
            "Keep the layout mobile-friendly: short paragraphs, and tables only with few columns."
    }

    companion object {
        /** Tool rounds in one reply. After this, Claude must answer without tools. */
        private const val MAX_TOOL_ROUNDS = 8
        private val blockListType = object : TypeReference<List<ContentBlockParam>>() {}
        private val MODEL_MISSING = Regex("""model '(?:(?:global|us|eu|jp|au|apac)\.)?([^']+)' does not exist""")

        /** Serializes the final assistant content so it can be sent back unchanged later. */
        fun encodeContent(message: Message): String =
            jsonMapper().writeValueAsString(message.content().map { it.toParam() })

        fun isRefusal(message: Message): Boolean =
            message.stopReason().map { it == StopReason.REFUSAL }.orElse(false)

        /** The message as API turns. A reply that used tools has its tool turns before the final content. */
        fun toParams(message: ChatMessage): List<MessageParam> {
            if (message.role == Role.ASSISTANT && !message.stopped && message.apiContent != null && message.apiTurns.isNotEmpty()) {
                val turns = message.apiTurns.map { turn ->
                    val blocks = runCatching { jsonMapper().readValue(turn.content, blockListType) }.getOrNull()
                        ?: return listOf(toParam(message))
                    MessageParam.builder()
                        .role(if (turn.role == Role.USER) MessageParam.Role.USER else MessageParam.Role.ASSISTANT)
                        .contentOfBlockParams(blocks)
                        .build()
                }
                return turns + toParam(message)
            }
            return listOf(toParam(message))
        }

        fun toParam(message: ChatMessage): MessageParam = when (message.role) {
            Role.USER -> MessageParam.builder()
                .role(MessageParam.Role.USER)
                .contentOfBlockParams(userBlocks(message))
                .build()
            Role.ASSISTANT -> {
                val blocks = message.apiContent
                    ?.takeIf { !message.stopped }
                    ?.let { runCatching { jsonMapper().readValue(it, blockListType) }.getOrNull() }
                    ?: listOf(textBlock(message.text.ifBlank { "(no response)" }))
                MessageParam.builder()
                    .role(MessageParam.Role.ASSISTANT)
                    .contentOfBlockParams(blocks)
                    .build()
            }
        }

        private fun userBlocks(message: ChatMessage): List<ContentBlockParam> {
            val blocks = mutableListOf<ContentBlockParam>()
            for (attachment in message.attachments) {
                val file = File(attachment.path)
                if (!file.exists()) continue
                when (attachment.kind) {
                    AttachmentKind.IMAGE -> blocks += ContentBlockParam.ofImage(
                        ImageBlockParam.builder()
                            .source(
                                Base64ImageSource.builder()
                                    .data(base64(file))
                                    .mediaType(Base64ImageSource.MediaType.of(attachment.mediaType))
                                    .build()
                            )
                            .build()
                    )
                    AttachmentKind.PDF -> blocks += ContentBlockParam.ofDocument(
                        DocumentBlockParam.builder()
                            .source(Base64PdfSource.builder().data(base64(file)).build())
                            .title(attachment.name)
                            .build()
                    )
                    AttachmentKind.TEXT -> blocks += ContentBlockParam.ofDocument(
                        DocumentBlockParam.builder()
                            .source(PlainTextSource.builder().data(file.readText()).build())
                            .title(attachment.name)
                            .build()
                    )
                }
            }
            if (message.text.isNotBlank() || blocks.isEmpty()) blocks += textBlock(message.text.ifBlank { "(attachment)" })
            return blocks
        }

        private fun textBlock(text: String) = ContentBlockParam.ofText(TextBlockParam.builder().text(text).build())

        private fun base64(file: File) = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)

        fun friendlyError(e: Throwable): String {
            android.util.Log.w("BedrockChat", "Request failed", e)
            return describe(e)
        }

        /** The error exactly as Bedrock returned it, for chat replies, sign-in, and settings checks. */
        fun rawError(e: Throwable): String {
            android.util.Log.w("BedrockChat", "Check failed", e)
            return when (e) {
                is NoGeoProfileException -> describe(e)
                else -> listOfNotNull(e.javaClass.simpleName, e.message).joinToString(": ")
            }
        }

        private fun describe(e: Throwable): String = when (e) {
            is NoGeoProfileException -> "${e.region} has no geographic routing. Use Global or In-region."

            is UnauthorizedException -> "The Bedrock API key was rejected. Check that it is correct and not expired."
            is PermissionDeniedException -> "This key does not have access to the selected model in this region. " +
                "Enable model access in the Bedrock console, or pick another model."
            is NotFoundException -> MODEL_MISSING.find(e.message.orEmpty())?.let { m ->
                "${modelById(m.groupValues[1]).name} isn't available for this key in this region. Pick another model."
            } ?: "Bedrock returned 404 (not found): ${e.message}"
            is RateLimitException -> "Rate limited by Bedrock. Wait a moment and try again."
            is AnthropicServiceException -> "Bedrock returned an error (${e.statusCode()}): ${e.message}"
            is AnthropicIoException -> "Network error. Check your connection and try again."
            else -> e.message ?: e.javaClass.simpleName
        }
    }
}
