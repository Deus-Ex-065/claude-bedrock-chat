package dev.local.bedrockchat.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.OutlinedButton
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import dev.local.bedrockchat.ChatUiState
import dev.local.bedrockchat.data.ChatMessage
import dev.local.bedrockchat.data.Effort
import dev.local.bedrockchat.data.Role
import dev.local.bedrockchat.data.ToolCall
import dev.local.bedrockchat.data.ToolStatus
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.CircularProgressIndicator
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalTime
import java.util.UUID

/** Everything the chat screen can ask the view model to do. */
class ChatActions(
    val send: (String) -> Unit,
    val stop: () -> Unit,
    val retry: () -> Unit,
    val editAndResend: (String, String) -> Unit,
    val newChat: (incognito: Boolean) -> Unit,
    val addAttachments: (List<android.net.Uri>) -> Unit,
    val removeAttachment: (String) -> Unit,
    val setModel: (String) -> Unit,
    val setEffort: (Effort) -> Unit,
    val rename: (String, String) -> Unit,
    val toggleStar: (String) -> Unit,
    val delete: (String) -> Unit,
    val setChatNotifications: (Boolean) -> Unit,
    val dismissChatNotificationsOffer: () -> Unit,
)

@Composable
fun ChatScreen(
    state: ChatUiState,
    displayName: String,
    haptics: Boolean,
    offerNotifications: Boolean,
    actions: ChatActions,
    onOpenDrawer: () -> Unit,
) {
    val colors = AppTheme.colors
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val conversation = state.conversation
    val messages = conversation.messages

    var input by rememberSaveable(conversation.id) { mutableStateOf("") }
    var editingId by rememberSaveable(conversation.id) { mutableStateOf<String?>(null) }
    var quote by rememberSaveable(conversation.id) { mutableStateOf<String?>(null) }
    val readAloud = rememberReadAloud()
    LaunchedEffect(conversation.id) { readAloud.stop() }
    var showAdd by remember { mutableStateOf(false) }
    var showModel by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var selectText by remember { mutableStateOf<String?>(null) }
    var cameraFile by remember { mutableStateOf<File?>(null) }

    val notifyToggle = rememberChatNotificationToggle(actions.setChatNotifications)
    val speechAvailable = remember { SpeechRecognizer.isRecognitionAvailable(context) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        if (uris.isNotEmpty()) actions.addAttachments(uris)
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) actions.addAttachments(uris)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val file = cameraFile
        if (ok && file != null) actions.addAttachments(listOf(FileProvider.getUriForFile(context, "${context.packageName}.files", file)))
    }
    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!spoken.isNullOrBlank()) input = if (input.isBlank()) spoken else "$input $spoken"
    }

    fun copy(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Message", text))
    }

    fun submit() {
        if (haptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        val id = editingId
        val text = withQuote(quote, input)
        if (id != null) actions.editAndResend(id, text) else actions.send(text)
        input = ""
        editingId = null
        quote = null
    }

    val listState = rememberLazyListState()
    val atBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            last == null || (last.index == info.totalItemsCount - 1 && last.offset + last.size <= info.viewportEndOffset + 40)
        }
    }
    val lastText = messages.lastOrNull()?.let { it.text.length + it.thinking.length } ?: 0
    // Follow the reply while it streams. Scrolling up stops following; scrolling back to the bottom resumes it.
    // This is tracked separately from atBottom, which is already false once new text makes the reply taller.
    var follow by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress to atBottom }.collect { (scrolling, bottom) ->
            if (scrolling) follow = bottom
        }
    }
    LaunchedEffect(messages.size) {
        follow = true
        if (messages.isNotEmpty()) listState.scrollToItem(messages.size)
    }
    LaunchedEffect(lastText) {
        if (state.isStreaming && follow && messages.isNotEmpty()) listState.scrollToItem(messages.size)
    }

    Column(Modifier.fillMaxSize().background(colors.background).statusBarsPadding()) {
        // Top bar
        Box(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp)) {
            IconButton(onClick = onOpenDrawer, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(Icons.Outlined.Menu, "Open menu", tint = colors.text)
            }
            if (messages.isNotEmpty()) {
                Text(
                    if (state.incognito) "Incognito chat" else conversation.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    // Room for the two icons on the right, on both sides so the title stays centred.
                    modifier = Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 100.dp),
                )
            }
            Row(Modifier.align(Alignment.CenterEnd)) {
                if (messages.isEmpty()) {
                    IconButton(onClick = { actions.newChat(!state.incognito) }) {
                        Icon(
                            if (state.incognito) Icons.Outlined.Close else GhostIcon,
                            if (state.incognito) "Leave incognito" else "Incognito chat",
                            tint = colors.text,
                        )
                    }
                } else {
                    IconButton(onClick = { actions.newChat(false) }) {
                        Icon(Icons.Outlined.AddComment, "New chat", tint = colors.text)
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) { Icon(Icons.Outlined.MoreVert, "More options", tint = colors.text) }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }, containerColor = colors.surface) {
                            if (!state.incognito) {
                                DropdownMenuItem(
                                    text = { Text(if (conversation.starred) "Unstar" else "Star") },
                                    leadingIcon = { Icon(if (conversation.starred) Icons.Outlined.Star else Icons.Outlined.StarBorder, null) },
                                    onClick = { showMenu = false; actions.toggleStar(conversation.id) },
                                )
                                DropdownMenuItem(
                                    text = { Text("Rename") },
                                    leadingIcon = { Icon(Icons.Outlined.DriveFileRenameOutline, null) },
                                    onClick = { showMenu = false; renaming = true },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                onClick = { showMenu = false; confirmDelete = true },
                            )
                        }
                    }
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (messages.isEmpty()) {
                Greeting(state.incognito, displayName, Modifier.align(Alignment.Center))
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    if (state.incognito) {
                        item {
                            Text("Incognito chat", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary,
                                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    items(messages, key = { it.id }) { message ->
                        when (message.role) {
                            Role.USER -> UserMessage(
                                message,
                                onCopy = { copy(message.text) },
                                onSelect = { selectText = message.text },
                                onEdit = if (state.isStreaming) null else ({ editingId = message.id; input = message.text }),
                            )
                            Role.ASSISTANT -> Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                                if (offerNotifications && message == messages.last()) {
                                    NotifyCard(onTurnOn = { notifyToggle(true) }, onClose = actions.dismissChatNotificationsOffer)
                                }
                                AssistantMessage(
                                    message = message,
                                    streaming = message.id == state.streamingId,
                                    waiting = message.id == state.streamingId && state.waiting,
                                    isLast = message == messages.last(),
                                    onCopy = ::copy,
                                    onRetry = actions.retry,
                                    speaking = readAloud.speakingId == message.id,
                                    onReadAloud = { readAloud.toggle(message.id, message.text) },
                                    onShare = { shareText(context, message.text) },
                                    onQuote = { quote = it },
                                )
                            }
                        }
                    }
                    if (messages.last().role == Role.ASSISTANT && !state.isStreaming) {
                        item {
                            Text("Claude is AI and can make mistakes.", style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary, modifier = Modifier.padding(horizontal = 20.dp))
                        }
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
                androidx.compose.animation.AnimatedVisibility(
                    visible = !atBottom,
                    enter = fadeIn(), exit = fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
                ) {
                    Box(
                        Modifier
                            .size(44.dp)
                            .shadow(4.dp, CircleShape)
                            .clip(CircleShape)
                            .background(colors.surface)
                            .clickable { scope.launch { listState.animateScrollToItem(messages.size) } },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.KeyboardArrowDown, "Scroll to bottom", tint = colors.text)
                    }
                }
            }
        }

        Column(Modifier.navigationBarsPadding().imePadding().padding(horizontal = 12.dp).padding(bottom = 8.dp)) {
            quote?.let { quoted ->
                Row(
                    Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, bottom = 6.dp).height(IntrinsicSize.Min),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(3.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(colors.accent))
                    Spacer(Modifier.width(10.dp))
                    Text(quoted, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    IconButton(onClick = { quote = null }) {
                        Icon(Icons.Outlined.Close, "Remove quote", tint = colors.textSecondary, modifier = Modifier.size(18.dp))
                    }
                }
            }
            if (editingId != null) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Edit, null, tint = colors.textSecondary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Editing message", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary,
                        modifier = Modifier.weight(1f))
                    Text("Cancel", style = MaterialTheme.typography.labelMedium, color = colors.text,
                        modifier = Modifier.clickable { editingId = null; input = "" }.padding(6.dp))
                }
            }
            Composer(
                text = input,
                onTextChange = { input = it },
                placeholder = if (messages.isEmpty()) "Chat with Claude…" else "Reply to Claude…",
                pending = state.pending,
                modelId = state.modelId,
                effort = state.effort,
                streaming = state.isStreaming,
                speechAvailable = speechAvailable,
                onAdd = { showAdd = true },
                onRemoveAttachment = actions.removeAttachment,
                onModel = { showModel = true },
                onSpeech = {
                    speech.launch(
                        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    )
                },
                onSend = ::submit,
                onStop = actions.stop,
            )
        }
    }

    if (showAdd) {
        AddToChatSheet(
            onDismiss = { showAdd = false },
            onCamera = {
                val dir = File(context.cacheDir, "camera").apply { mkdirs() }
                val file = File(dir, "${UUID.randomUUID()}.jpg")
                cameraFile = file
                camera.launch(FileProvider.getUriForFile(context, "${context.packageName}.files", file))
            },
            onPhotos = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            // Any type: code files such as .kt or .py often have no text MIME type. The import checks the file.
            onFiles = { filePicker.launch(arrayOf("*/*")) },
        )
    }
    if (showModel) {
        ModelSheet(state.modelId, state.effort, actions.setModel, actions.setEffort, onDismiss = { showModel = false })
    }
    if (renaming) {
        var title by remember { mutableStateOf(conversation.title) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Rename chat") },
            text = { OutlinedTextField(title, { title = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { actions.rename(conversation.id, title); renaming = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Cancel") } },
            containerColor = colors.background,
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(if (state.incognito) "End incognito chat?" else "Delete chat?") },
            text = { Text(if (state.incognito) "This chat stays out of your history. Ending it can't be undone." else "This chat will be deleted from this device.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    if (state.incognito) actions.newChat(false) else actions.delete(conversation.id)
                }) { Text(if (state.incognito) "End chat" else "Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
            containerColor = colors.background,
        )
    }
    selectText?.let { text ->
        AlertDialog(
            onDismissRequest = { selectText = null },
            title = { Text("Select text") },
            text = { SelectionContainer { Text(text, style = MaterialTheme.typography.bodyLarge) } },
            confirmButton = { TextButton(onClick = { selectText = null }) { Text("Done") } },
            containerColor = colors.background,
        )
    }
}

@Composable
private fun Greeting(incognito: Boolean, name: String, modifier: Modifier) {
    val colors = AppTheme.colors
    Column(modifier.padding(horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (incognito) {
            Icon(GhostIcon, null, tint = colors.text, modifier = Modifier.size(44.dp))
            Spacer(Modifier.height(14.dp))
            Text("Incognito chat", style = MaterialTheme.typography.headlineSmall, color = colors.text)
            Spacer(Modifier.height(8.dp))
            Text(
                "Incognito chats stay out of your history and search, and are not saved on this device.",
                style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary, textAlign = TextAlign.Center,
            )
        } else {
            Mark(Modifier.size(52.dp))
            Spacer(Modifier.height(14.dp))
            val hour = LocalTime.now().hour
            val greeting = when (hour) { in 5..11 -> "Good morning"; in 12..17 -> "Good afternoon"; else -> "Good evening" }
            Text(
                if (name.isBlank()) greeting else "$greeting, $name",
                style = MaterialTheme.typography.displaySmall,
                color = colors.text,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UserMessage(message: ChatMessage, onCopy: () -> Unit, onSelect: () -> Unit, onEdit: (() -> Unit)?) {
    val colors = AppTheme.colors
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(start = 48.dp, end = 16.dp), horizontalAlignment = Alignment.End) {
        if (message.attachments.isNotEmpty()) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                message.attachments.forEach { AttachmentPreview(it, size = 112) }
            }
        }
        if (message.text.isNotBlank()) {
            Box {
                val (quoted, body) = remember(message.text) { splitQuote(message.text) }
                Column(
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(colors.userBubble)
                        .combinedClickable(onClick = {}, onLongClick = { menu = true })
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    if (quoted != null) {
                        Row(Modifier.padding(bottom = 8.dp).height(IntrinsicSize.Min)) {
                            Box(Modifier.width(3.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(colors.accent))
                            Spacer(Modifier.width(10.dp))
                            Text(quoted, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary,
                                maxLines = 4, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Text(body, style = MaterialTheme.typography.bodyLarge, color = colors.text)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = colors.surface) {
                    DropdownMenuItem(text = { Text("Copy message") }, leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) },
                        onClick = { menu = false; onCopy() })
                    DropdownMenuItem(text = { Text("Select text") }, leadingIcon = { Icon(Icons.Outlined.TextFields, null) },
                        onClick = { menu = false; onSelect() })
                    if (onEdit != null) {
                        DropdownMenuItem(text = { Text("Edit") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                            onClick = { menu = false; onEdit() })
                    }
                }
            }
        }
    }
}

/** One tool call in a reply, for example "Read example.com". A failed call shows why below it. */
@Composable
private fun ToolCallRow(call: ToolCall) {
    val colors = AppTheme.colors
    Column(Modifier.padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                when (call.status) {
                    ToolStatus.RUNNING -> CircularProgressIndicator(Modifier.size(14.dp), color = colors.textSecondary, strokeWidth = 1.5.dp)
                    ToolStatus.DONE -> Icon(
                        when (call.name) {
                            "update_memory" -> Icons.Outlined.Psychology
                            "search_chats", "read_chat" -> Icons.Outlined.History
                            else -> Icons.Outlined.Language
                        }, null,
                        tint = colors.textSecondary, modifier = Modifier.size(18.dp),
                    )
                    ToolStatus.FAILED -> Icon(Icons.Outlined.ErrorOutline, null, tint = colors.textSecondary, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(call.label, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary, maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        call.detail?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary, maxLines = 3,
                modifier = Modifier.padding(start = 28.dp, top = 2.dp))
        }
    }
}

@Composable
private fun AssistantMessage(
    message: ChatMessage,
    streaming: Boolean,
    waiting: Boolean,
    isLast: Boolean,
    onCopy: (String) -> Unit,
    onRetry: () -> Unit,
    speaking: Boolean,
    onReadAloud: () -> Unit,
    onShare: () -> Unit,
    onQuote: (String) -> Unit,
) {
    val colors = AppTheme.colors
    var showThinking by rememberSaveable(message.id) { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { kotlinx.coroutines.delay(1500); copied = false } }

    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        val thinkingNow = streaming && message.text.isEmpty() && message.thinking.isNotEmpty()
        if (message.thinking.isNotBlank()) {
            Row(
                Modifier.clip(RoundedCornerShape(8.dp)).clickable { showThinking = !showThinking }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (thinkingNow) "Thinking…" else "Thought for ${formatDuration(message.thinkingMillis)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
                Icon(if (showThinking) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null,
                    tint = colors.textSecondary, modifier = Modifier.size(20.dp))
            }
            if (showThinking) {
                Row(Modifier.padding(top = 4.dp, bottom = 10.dp).height(IntrinsicSize.Min)) {
                    Box(Modifier.width(2.dp).fillMaxHeight().background(colors.border))
                    Text(
                        message.thinking.trim(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
        }

        message.toolCalls.forEach { call -> ToolCallRow(call) }
        if (message.toolCalls.isNotEmpty()) Spacer(Modifier.height(6.dp))

        if (message.text.isNotEmpty()) {
            // Long-press to select. The selection menu has Copy and Quote; Quote puts the text above the message box.
            QuoteSelection(onQuote) {
                MarkdownText(message.text, onCopyCode = onCopy)
            }
        }

        if (streaming) {
            Spacer(Modifier.height(10.dp))
            WorkingMark()
        }

        message.error?.let { error ->
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(Icons.Outlined.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(error, style = MaterialTheme.typography.bodyMedium, color = colors.text)
            }
        }
        if (message.stopped && message.error == null && !streaming) {
            Text("Stopped", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary, modifier = Modifier.padding(top = 6.dp))
        }

        if (!streaming && !waiting) {
            Row(Modifier.padding(top = 4.dp)) {
                if (message.text.isNotBlank()) {
                    IconButton(onClick = { onCopy(message.text); copied = true }) {
                        Icon(if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy, "Copy message",
                            tint = colors.textSecondary, modifier = Modifier.size(20.dp))
                    }
                }
                if (message.text.isNotBlank()) {
                    IconButton(onClick = onShare) {
                        Icon(Icons.Outlined.Share, "Share message", tint = colors.textSecondary, modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onReadAloud) {
                        Icon(if (speaking) Icons.Outlined.StopCircle else Icons.AutoMirrored.Outlined.VolumeUp,
                            if (speaking) "Stop reading" else "Read aloud",
                            tint = if (speaking) colors.accent else colors.textSecondary, modifier = Modifier.size(22.dp))
                    }
                }
                if (isLast) {
                    IconButton(onClick = onRetry) {
                        Icon(Icons.Outlined.Refresh, "Retry message", tint = colors.textSecondary, modifier = Modifier.size(22.dp))
                    }
                }
            }
        }
    }
}


private fun formatDuration(ms: Long): String {
    val seconds = (ms / 1000).coerceAtLeast(1)
    return if (seconds < 60) "${seconds}s" else "${seconds / 60}m ${seconds % 60}s"
}


/** The offer to notify when a reply finishes. It shows once, above the latest reply, until you answer or close it. */
@Composable
private fun NotifyCard(onTurnOn: () -> Unit, onClose: () -> Unit) {
    val colors = AppTheme.colors
    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(24.dp))
            .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.NotificationsNone, null, tint = colors.text, modifier = Modifier.padding(top = 12.dp))
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "Close", tint = colors.textSecondary) }
        }
        Column(Modifier.padding(end = 12.dp)) {
            Text("Get notified when Claude responds", style = MaterialTheme.typography.titleSmall, color = colors.text)
            Spacer(Modifier.height(6.dp))
            Text("You can leave the app while Claude writes. The reply continues, and the phone notifies you when it is ready.",
                style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onTurnOn, modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, colors.border)) {
                Text("Turn on", color = colors.accent)
            }
        }
    }
}
