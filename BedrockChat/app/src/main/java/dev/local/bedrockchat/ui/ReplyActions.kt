package dev.local.bedrockchat.ui

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.foundation.text.contextmenu.builder.item
import androidx.compose.foundation.text.contextmenu.data.ProcessTextKey
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuItem
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuKeys
import androidx.compose.foundation.text.contextmenu.modifier.appendTextContextMenuComponents
import androidx.compose.foundation.text.contextmenu.modifier.filterTextContextMenuComponents
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

/**
 * Opens Android's share sheet with [text]. Bold marks and heading marks are removed, because most apps
 * show them as plain characters. Lists and code stay as they are.
 */
fun shareText(context: Context, text: String) {
    val plain = text
        .replace(Regex("""(\*\*|__)(.+?)\1"""), "$2")
        .replace(Regex("""(?m)^#{1,6}\s+"""), "")
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, plain)
    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/**
 * Reads replies aloud with Android's text-to-speech. One reply at a time: starting another one stops the first.
 * [speakingId] is the message that is read now, or null.
 */
class ReadAloud(context: Context) {
    var speakingId by mutableStateOf<String?>(null)
        private set
    private var ready = false
    private var pending: Pair<String, String>? = null
    private val main = Handler(Looper.getMainLooper())
    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        main.post {
            ready = status == TextToSpeech.SUCCESS
            pending?.let { (id, text) -> pending = null; if (ready) start(id, text) else speakingId = null }
        }
    }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                if (utteranceId?.endsWith(LAST) == true) main.post { if (utteranceId.startsWith("${speakingId}:")) speakingId = null }
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { main.post { speakingId = null } }
            override fun onError(utteranceId: String?, errorCode: Int) { main.post { speakingId = null } }
        })
    }

    /** Starts reading [markdown], or stops if this message is already being read. */
    fun toggle(id: String, markdown: String) {
        if (speakingId == id) { stop(); return }
        val text = speakableText(markdown)
        if (text.isBlank()) return
        speakingId = id
        if (ready) start(id, text) else pending = id to text // The engine is still starting.
    }

    private fun start(id: String, text: String) {
        tts.stop()
        val parts = chunks(text, TextToSpeech.getMaxSpeechInputLength() - 100)
        parts.forEachIndexed { i, part ->
            val uid = "$id:$i" + if (i == parts.lastIndex) LAST else ""
            tts.speak(part, TextToSpeech.QUEUE_ADD, null, uid)
        }
    }

    fun stop() {
        pending = null
        speakingId = null
        tts.stop()
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }

    private companion object {
        const val LAST = ":last"

        /** Splits at paragraph or sentence ends, so each part fits the engine's limit. */
        fun chunks(text: String, max: Int): List<String> {
            val out = mutableListOf<String>()
            val current = StringBuilder()
            for (sentence in text.split(Regex("(?<=[.!?\\n])\\s+"))) {
                if (current.length + sentence.length + 1 > max && current.isNotEmpty()) {
                    out += current.toString(); current.clear()
                }
                // A sentence longer than the limit is cut at the limit.
                sentence.chunked(max).forEach { piece ->
                    if (current.length + piece.length + 1 > max && current.isNotEmpty()) { out += current.toString(); current.clear() }
                    if (current.isNotEmpty()) current.append(' ')
                    current.append(piece)
                }
            }
            if (current.isNotEmpty()) out += current.toString()
            return out
        }
    }
}

@Composable
fun rememberReadAloud(): ReadAloud {
    val context = LocalContext.current
    val readAloud = remember { ReadAloud(context) }
    androidx.compose.runtime.DisposableEffect(readAloud) { onDispose { readAloud.shutdown() } }
    return readAloud
}

/** The reply as plain sentences for speech: no Markdown marks, and code blocks are left out. */
fun speakableText(markdown: String): String = markdown
    .replace(Regex("```[\\s\\S]*?(```|$)"), "\n")
    .replace(Regex("!\\[[^\\]]*]\\([^)]*\\)"), "")
    .replace(Regex("\\[([^\\]]+)]\\([^)]*\\)"), "$1")
    .replace(Regex("(?m)^\\s{0,3}(#{1,6}|>|[-*+]|\\d+[.)])\\s+"), "")
    .replace(Regex("(?m)^\\s*\\|?\\s*:?-{3,}.*$"), "")
    .replace(Regex("[*_~`|]"), " ")
    .replace(Regex("[ \\t]+"), " ")
    .replace(Regex("\\n{2,}"), "\n")
    .trim()

/**
 * Makes [content] selectable, with Copy, Select all, and Quote in the selection menu. Compose gives menu items no access to the
 * selection, so Quote runs the built-in Copy action into a stand-in clipboard: the text goes to [onQuote], and the
 * system clipboard is not touched (no "pasted from your clipboard" notice). Copy itself still uses the real clipboard.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QuoteSelection(onQuote: (String) -> Unit, content: @Composable () -> Unit) {
    val real = LocalClipboard.current
    val currentOnQuote by rememberUpdatedState(onQuote)
    val clipboard = remember(real) { QuoteClipboard(real) }
    val copyItem = remember { arrayOfNulls<TextContextMenuItem>(1) }
    CompositionLocalProvider(LocalClipboard provides clipboard) {
        SelectionContainer(
            Modifier
                .filterTextContextMenuComponents { component ->
                    if (component.key == TextContextMenuKeys.CopyKey && component is TextContextMenuItem) copyItem[0] = component
                    // Leave out the system text actions (Ask Gemini, Read aloud, and others). Compose adds them before
                    // app items, so with them Quote is hidden under the overflow. The reply has its own Read aloud.
                    component.key !is ProcessTextKey
                }
                .appendTextContextMenuComponents {
                    item(key = QuoteKey, label = "Quote") {
                        copyItem[0]?.let { copy ->
                            clipboard.capture = { text -> text.trim().takeIf { it.isNotEmpty() }?.let(currentOnQuote) }
                            copy.onClick(this)
                        }
                        close()
                    }
                },
        ) { content() }
    }
}

/** Passes everything to the real clipboard, except one copy that Quote asked to capture. */
private class QuoteClipboard(private val real: Clipboard) : Clipboard {
    var capture: ((String) -> Unit)? = null

    override suspend fun getClipEntry(): ClipEntry? = real.getClipEntry()

    override suspend fun setClipEntry(clipEntry: ClipEntry?) {
        val take = capture
        if (take == null) { real.setClipEntry(clipEntry); return }
        capture = null
        val data = clipEntry?.clipData
        val text = if (data != null && data.itemCount > 0) data.getItemAt(0).text?.toString() else null
        text?.let(take)
    }

    override val nativeClipboard get() = real.nativeClipboard
}

private object QuoteKey

/** The text to send: the quote as a Markdown block quote, then the message. */
fun withQuote(quote: String?, message: String): String =
    if (quote.isNullOrBlank()) message
    else quote.trim().lines().joinToString("\n") { "> $it" } + "\n\n" + message

/** Splits a sent message into its leading quote (if any) and the rest, for the message bubble. */
fun splitQuote(text: String): Pair<String?, String> {
    val lines = text.lines()
    val quoted = lines.takeWhile { it.startsWith(">") }
    if (quoted.isEmpty() || quoted.size == lines.size) return null to text
    val rest = lines.drop(quoted.size).joinToString("\n").trim()
    if (rest.isEmpty()) return null to text
    return quoted.joinToString("\n") { it.removePrefix(">").removePrefix(" ") } to rest
}
