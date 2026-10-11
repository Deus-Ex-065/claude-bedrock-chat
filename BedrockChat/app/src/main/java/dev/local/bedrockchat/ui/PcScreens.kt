package dev.local.bedrockchat.ui

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.filled.Stop
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.local.bedrockchat.PcUiState
import dev.local.bedrockchat.PcViewModel
import dev.local.bedrockchat.data.PcPermission
import dev.local.bedrockchat.data.PcSession
import dev.local.bedrockchat.data.modelById
import kotlinx.coroutines.delay
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import dev.local.bedrockchat.PcScreenState
import dev.local.bedrockchat.PcWatchService
import kotlinx.coroutines.launch
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.draw.shadow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Keeps the live connection open while this screen is visible. */
@Composable
private fun LiveWhileVisible(vm: PcViewModel) {
    DisposableEffect(vm) {
        vm.startLive()
        onDispose { vm.stopLive() }
    }
}

@Composable
fun PcSessionsScreen(vm: PcViewModel, state: PcUiState, onOpenDrawer: () -> Unit, onOpenSession: (String) -> Unit) {
    val colors = AppTheme.colors
    LiveWhileVisible(vm)
    LaunchedEffect(state.link) { if (state.link != null) vm.refreshSessions() }
    var menu by remember { mutableStateOf(false) }
    var collapsed by remember { mutableStateOf(setOf<String>()) }

    Column(Modifier.fillMaxSize().background(colors.background).statusBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp)) {
            IconButton(onClick = onOpenDrawer, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(Icons.Outlined.Menu, "Open menu", tint = colors.text)
            }
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("PC sessions", style = MaterialTheme.typography.titleLarge, color = colors.text)
                state.link?.let {
                    Text(
                        if (state.connected) "${it.name} · live" else "${it.name} · connecting…",
                        style = MaterialTheme.typography.bodySmall, color = colors.textSecondary,
                    )
                }
            }
            if (state.link != null) {
                Row(Modifier.align(Alignment.CenterEnd)) {
                    IconButton(onClick = vm::refreshSessions) { Icon(Icons.Outlined.Refresh, "Refresh", tint = colors.text) }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "More", tint = colors.text) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = colors.surface) {
                            DropdownMenuItem(text = { Text("Unpair this PC") }, onClick = { menu = false; vm.unpair() })
                        }
                    }
                }
            }
        }

        if (state.link == null) {
            PairForm(vm)
            return@Column
        }
        state.error?.let { ErrorBanner(it) }
        NotificationsRow(vm)
        when {
            state.loadingSessions -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.accent)
            }
            state.sessions.isEmpty() -> Text(
                "No Claude Code sessions found on ${state.link.name}.",
                color = colors.textSecondary, modifier = Modifier.padding(24.dp),
            )
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                // The same groups as the PC's sidebar, in its order. Sessions in no group come last.
                val sections = state.sessions.groupBy { it.group }.entries
                    .sortedBy { (name, list) -> if (name == null) Int.MAX_VALUE else list.first().groupOrder ?: Int.MAX_VALUE - 1 }
                val grouped = sections.any { it.key != null }
                sections.forEach { (name, list) ->
                    val key = name ?: ""
                    val open = key !in collapsed
                    if (grouped) {
                        item(key = "group:$key") {
                            GroupHeader(name ?: "Sessions", list.size, open) {
                                collapsed = if (open) collapsed + key else collapsed - key
                            }
                        }
                    }
                    if (open) {
                        items(list, key = { it.id }) { session ->
                            SessionRow(session, state.activity[session.id]) { onOpenSession(session.id) }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Turns background notifications on or off. Turning them on asks for the two permissions they need:
 * notifications (Android 13 and later), and background use without battery limits.
 * Android cannot ask at install time, so the app asks when you turn the feature on.
 */
@Composable
private fun rememberNotificationToggle(vm: PcViewModel): (Boolean) -> Unit {
    val context = LocalContext.current
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.setNotifications(granted)
        if (granted) askBackgroundUse(context)
    }
    return { want ->
        val needsPermission = want && android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        when {
            needsPermission -> ask.launch(Manifest.permission.POST_NOTIFICATIONS)
            want -> { vm.setNotifications(true); askBackgroundUse(context) }
            else -> vm.setNotifications(false)
        }
    }
}

/** Shows Android's "Let the app always run in the background?" dialog, unless the app already has that. */
@android.annotation.SuppressLint("BatteryLife")
private fun askBackgroundUse(context: android.content.Context) {
    val power = context.getSystemService(android.os.PowerManager::class.java)
    if (power.isIgnoringBatteryOptimizations(context.packageName)) return
    runCatching {
        context.startActivity(
            android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .setData(android.net.Uri.parse("package:${context.packageName}"))
        )
    }
}

@Composable
private fun NotificationsRow(vm: PcViewModel) {
    val colors = AppTheme.colors
    val context = LocalContext.current
    val setting by vm.notifications.collectAsStateWithLifecycle()
    val toggle = rememberNotificationToggle(vm)
    // Notifications are on by default. Right after pairing, ask for the permissions they need.
    val ask by vm.askPermissions.collectAsStateWithLifecycle()
    LaunchedEffect(ask) {
        if (ask) { vm.permissionsAsked(); toggle(true) }
    }
    // Show the real state: without the notification permission, nothing can show.
    val on = setting && rememberNotificationPermission()
    Row(
        Modifier.fillMaxWidth().clickable { toggle(!on) }.padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Notifications", style = MaterialTheme.typography.bodyMedium, color = colors.text)
            Text(
                "Approvals and finished replies, for sessions you approve on this phone or message from it.",
                style = MaterialTheme.typography.bodySmall, color = colors.textSecondary,
            )
        }
        Switch(checked = on, onCheckedChange = toggle, colors = SwitchDefaults.colors(checkedTrackColor = colors.accent))
    }
}

/** Tells the notification service which session is on screen, and clears that session's notifications. */
@Composable
private fun TrackVisibleSession(sessionId: String?) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    DisposableEffect(sessionId, owner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    PcScreenState.visibleSession = sessionId
                    sessionId?.let { PcWatchService.clear(context, it) }
                }
                Lifecycle.Event.ON_PAUSE -> PcScreenState.visibleSession = null
                else -> {}
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            PcScreenState.visibleSession = null
        }
    }
}

@Composable
private fun GroupHeader(name: String, count: Int, open: Boolean, onToggle: () -> Unit) {
    val colors = AppTheme.colors
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(
                name, style = MaterialTheme.typography.labelLarge, color = colors.textSecondary,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(6.dp))
            Text("$count", style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
        }
        Icon(
            if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
            if (open) "Collapse $name" else "Expand $name",
            tint = colors.textSecondary, modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun SessionRow(session: PcSession, activity: String?, onClick: () -> Unit) {
    val colors = AppTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(session)
        Column(Modifier.weight(1f)) {
            Text(session.title.ifBlank { "Untitled session" }, style = MaterialTheme.typography.bodyLarge, color = colors.text,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            val detail = (if (session.pending > 0) "Needs your approval" else null)
                ?: activity?.let { "$it…" }
                ?: listOfNotNull(session.cwd?.substringAfterLast('\\')?.substringAfterLast('/'), relativeTime(session.lastModified))
                    .joinToString(" · ")
            Text(detail, style = MaterialTheme.typography.bodySmall,
                color = if (activity != null || session.pending > 0) colors.accent else colors.textSecondary,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun StatusDot(session: PcSession) {
    val colors = AppTheme.colors
    val color = when {
        !session.live -> null
        session.status == "busy" -> colors.accent
        else -> Color(0xFF7FB069)
    }
    Box(Modifier.padding(end = 14.dp).size(10.dp).clip(CircleShape).background(color ?: colors.border.copy(alpha = 0f)))
}

private fun relativeTime(millis: Long): String? =
    if (millis <= 0) null
    else if (System.currentTimeMillis() - millis < DateUtils.MINUTE_IN_MILLIS) "Just now"
    else DateUtils.getRelativeTimeSpanString(millis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()

@Composable
private fun ErrorBanner(message: String) {
    val colors = AppTheme.colors
    Row(
        Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = colors.text)
    }
}

@Composable
private fun PairForm(vm: PcViewModel) {
    val colors = AppTheme.colors
    val scope = rememberCoroutineScope()
    var address by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = colors.text, unfocusedBorderColor = colors.border,
        focusedTextColor = colors.text, unfocusedTextColor = colors.text, cursorColor = colors.text,
        focusedLabelColor = colors.textSecondary, unfocusedLabelColor = colors.textSecondary,
    )

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(Icons.Outlined.Computer, null, tint = colors.accent, modifier = Modifier.size(40.dp))
        Text("Watch Claude Code on your PC", style = ReplyTextStyle.copy(fontSize = 24.sp), color = colors.text)
        Text(
            "Start the bridge on your PC with --lan. It shows an address and a 6-digit code. " +
                "Enter both here. The phone and the PC must be on the same network.",
            style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary,
        )
        OutlinedTextField(
            value = address, onValueChange = { address = it; error = null },
            label = { Text("PC address") }, placeholder = { Text("192.168.0.6:8765") },
            singleLine = true, colors = fieldColors, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
        OutlinedTextField(
            value = code, onValueChange = { code = it.filter(Char::isDigit).take(6); error = null },
            label = { Text("Pairing code") }, singleLine = true, colors = fieldColors, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        Button(
            onClick = {
                busy = true
                scope.launch {
                    error = vm.pair(address, code)
                    busy = false
                }
            },
            enabled = !busy && address.isNotBlank() && code.length == 6,
            colors = ButtonDefaults.buttonColors(containerColor = colors.primaryButton, contentColor = colors.onPrimaryButton),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text(if (busy) "Pairing…" else "Pair") }
        Text(
            "You can follow sessions, send messages, and approve requests from here. " +
                "Claude Code runs on the PC with its own settings. The phone does not use your Bedrock key for this.",
            style = MaterialTheme.typography.bodySmall, color = colors.textSecondary,
        )
    }
}

// ---- One session ----

private sealed interface FeedItem {
    val key: String
    data class User(override val key: String, val text: String) : FeedItem
    /** The summary that Claude Code writes when it compacts a conversation. The messages before it are not loaded. */
    data class Compacted(override val key: String, val text: String) : FeedItem
    data class Attachment(override val key: String, val label: String, val image: Boolean, val fromUser: Boolean) : FeedItem
    data class Reply(override val key: String, val text: String) : FeedItem
    data class Thinking(override val key: String, val text: String) : FeedItem
    data class ToolCall(override val key: String, val name: String, val summary: String, val input: String) : FeedItem
    data class ToolResult(override val key: String, val text: String, val isError: Boolean, val truncated: Boolean) : FeedItem
}

/** How Claude Code starts the summary message after compaction. The SDK does not mark that message. */
private const val COMPACT_SUMMARY_START = "This session is being continued from a previous conversation"

private fun JsonObject.str(name: String) = (this[name] as? JsonPrimitive)?.contentOrNull

private fun toolSummary(input: JsonObject?): String {
    if (input == null) return ""
    for (key in listOf("command", "file_path", "pattern", "description", "url", "query", "prompt")) {
        input.str(key)?.takeIf { it.isNotBlank() }?.let { return it }
    }
    return input.toString()
}

private fun feedItems(state: PcUiState): List<FeedItem> = buildList {
    state.messages.forEachIndexed { i, m ->
        m.message.content.forEachIndexed { j, block ->
            val key = "$i-$j-${m.uuid.orEmpty()}" // The position makes it unique even if a uuid repeats.
            when (block.str("type")) {
                "text" -> block.str("text")?.let {
                    add(when {
                        m.type != "user" -> FeedItem.Reply(key, it)
                        it.startsWith(COMPACT_SUMMARY_START) -> FeedItem.Compacted(key, it)
                        else -> FeedItem.User(key, it)
                    })
                }
                "attachment" -> {
                    val image = block.str("kind") == "image"
                    val format = block.str("format").orEmpty()
                    val name = block.str("name").orEmpty().ifBlank { if (image) "Image" else "File" }
                    add(FeedItem.Attachment(key, if (format.isNotBlank()) "$name ($format)" else name, image, m.type == "user"))
                }
                "thinking" -> block.str("thinking")?.let { add(FeedItem.Thinking(key, it)) }
                "tool_use" -> {
                    val input = block["input"] as? JsonObject
                    add(FeedItem.ToolCall(key, block.str("name") ?: "Tool", toolSummary(input), input?.toString() ?: ""))
                }
                "tool_result" -> add(
                    FeedItem.ToolResult(
                        key, block.str("content").orEmpty(),
                        isError = block["is_error"]?.jsonPrimitive?.booleanOrNull == true,
                        truncated = block["truncated"]?.jsonPrimitive?.booleanOrNull == true,
                    )
                )
            }
        }
    }
}

@Composable
fun PcSessionScreen(vm: PcViewModel, state: PcUiState, onBack: () -> Unit) {
    val colors = AppTheme.colors
    LiveWhileVisible(vm)
    TrackVisibleSession(state.openId)
    BackHandler(onBack = onBack)
    val session = state.sessions.firstOrNull { it.id == state.openId }
    val items = remember(state.messages) { feedItems(state) }
    val listState = rememberLazyListState()
    val clipboard = LocalClipboardManager.current
    val activity = state.openId?.let { state.activity[it] }
    // The newest main-session reply tells how full the context is. Subagents have their own context.
    val lastUsage = remember(state.messages) {
        state.messages.lastOrNull { it.type == "assistant" && it.parentToolUseId == null && it.message.usage != null }
    }
    var showContext by remember { mutableStateOf(false) }

    // Start at the newest message, and follow new ones while the reader is at the bottom.
    var followed by remember { mutableStateOf(0) }
    val total = items.size + state.control.queued.size
    LaunchedEffect(total) {
        val atBottom = followed == 0 || listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it >= followed - 2 } == true
        if (total > 0 && atBottom) listState.scrollToItem(total - 1)
        followed = total
    }

    Column(Modifier.fillMaxSize().background(colors.background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = colors.text) }
            Column(Modifier.weight(1f)) {
                Text(session?.title ?: "Session", style = MaterialTheme.typography.titleMedium, color = colors.text,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    when {
                        !state.connected -> "Connecting to ${state.link?.name ?: "PC"}…"
                        activity != null -> "$activity…"
                        session?.live == true -> if (session.status == "busy") "Working" else "Idle on ${state.link?.name ?: "PC"}"
                        else -> "Not running"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (activity != null) colors.accent else colors.textSecondary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            lastUsage?.let { m ->
                val share = m.message.usage!!.toTokenUsage().context.toFloat() / contextWindowOf(m.message.model)
                TextButton(onClick = { showContext = true }) {
                    Text(
                        "Context ${percent(share)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (share > 0.8f) MaterialTheme.colorScheme.error else colors.textSecondary,
                        maxLines = 1, softWrap = false,
                    )
                }
            }
        }
        if (showContext) {
            val m = lastUsage
            ContextUsageDialog(
                m?.message?.usage?.toTokenUsage(), contextWindowOf(m?.message?.model), m?.message?.model?.let { modelById("anthropic.$it").name } ?: "Claude",
                note = "Claude Code compacts the session by itself when it gets near the limit. " +
                    "Use /compact or /clear on the PC to make room sooner.",
                onDismiss = { showContext = false },
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { vm.setRemote(!state.control.remote) }
                .padding(horizontal = 20.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Approve on this phone", style = MaterialTheme.typography.bodyMedium, color = colors.text)
                Text(
                    if (state.control.remote) "Permission requests come here instead of the PC."
                    else "Permission requests show on the PC.",
                    style = MaterialTheme.typography.bodySmall, color = colors.textSecondary,
                )
            }
            Switch(
                checked = state.control.remote, onCheckedChange = vm::setRemote,
                colors = SwitchDefaults.colors(checkedTrackColor = colors.accent),
            )
        }
        state.error?.let { ErrorBanner(it) }
        if (state.loadingMessages && items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = colors.accent) }
            return@Column
        }
        Box(Modifier.weight(1f)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items, key = { it.key }) { item ->
                // Long-press any text to select and copy it.
                SelectionContainer {
                when (item) {
                    is FeedItem.User -> UserBubble(item.text)
                    is FeedItem.Compacted -> Expandable("Earlier messages compacted: summary", item.text, mono = false)
                    is FeedItem.Attachment -> AttachmentChip(item)
                    is FeedItem.Reply -> Column {
                        MarkdownText(item.text, onCopyCode = { clipboard.setText(AnnotatedString(it)) })
                        IconButton(onClick = { clipboard.setText(AnnotatedString(item.text)) }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Outlined.ContentCopy, "Copy reply", tint = colors.textSecondary, modifier = Modifier.size(18.dp))
                        }
                    }
                    is FeedItem.Thinking -> Expandable("Thinking", item.text, mono = false)
                    is FeedItem.ToolCall -> ToolRow(item)
                    is FeedItem.ToolResult -> Expandable(
                        if (item.isError) "Error" else "Result",
                        item.text + if (item.truncated) "\n…(cut by the bridge)" else "",
                        mono = true, isError = item.isError,
                    )
                }
                }
            }
            itemsIndexed(state.control.queued) { i, text ->
                val qid = state.control.queuedIds.getOrNull(i)
                // A session that was just opened or restarted on the PC listens only after its first turn.
                val deaf = state.control.listening == false && session?.live == true && session.status != "busy" && activity == null
                UserBubble(
                    text,
                    note = if (deaf) "Waits: the PC session is not listening yet. Type anything on the PC once."
                    else "Sends when Claude finishes this turn",
                    onDelete = qid?.let { { vm.unqueue(it) } },
                )
            }
        }
        JumpButtons(listState, total)
        }
        // Also near the message box, so it is easy to see that Claude is still working.
        if (state.connected && (activity != null || session?.status == "busy")) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(color = colors.accent, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    activity?.let { "$it…" } ?: "Working…",
                    style = MaterialTheme.typography.bodySmall, color = colors.accent,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        state.control.pending.forEach { request ->
            ApprovalCard(request, onDecide = { allow, always -> vm.decide(request.id, allow, always) })
        }
        Composer(
            hint = if (session?.live == true) "Message Claude on ${state.link?.name ?: "PC"}" else "Continue this session",
            onSend = vm::send,
            confirmResume = session?.live != true,
            working = state.connected && (activity != null || session?.status == "busy" || state.control.runningHere),
            stopping = state.control.stopping,
            onStop = vm::stop,
        )
    }
}

@Composable
private fun ToolRow(item: FeedItem.ToolCall) {
    val colors = AppTheme.colors
    var open by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.codeBackground)
            .clickable { open = !open }
            .padding(horizontal = 12.dp, vertical = 9.dp)
            .animateContentSize(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Terminal, null, tint = colors.textSecondary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(item.name, fontFamily = MonoFamily, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.text)
            Spacer(Modifier.width(8.dp))
            Text(item.summary, fontFamily = MonoFamily, fontSize = 13.sp, color = colors.textSecondary,
                maxLines = if (open) Int.MAX_VALUE else 1, overflow = TextOverflow.Ellipsis)
        }
        if (open && item.input.isNotBlank() && item.input != item.summary) {
            Text(item.input, fontFamily = MonoFamily, fontSize = 12.sp, color = colors.textSecondary,
                modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun Expandable(label: String, text: String, mono: Boolean, isError: Boolean = false) {
    val colors = AppTheme.colors
    var open by remember { mutableStateOf(false) }
    val tint = if (isError) MaterialTheme.colorScheme.error else colors.textSecondary
    Column(Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 4.dp).animateContentSize()) {
        Text(
            if (open) "$label ▾" else "$label ▸  " + text.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty().take(80),
            style = MaterialTheme.typography.bodySmall, color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        if (open) {
            Text(
                text,
                fontFamily = if (mono) MonoFamily else null,
                fontSize = if (mono) 12.sp else 14.sp,
                color = tint,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/**
 * While you scroll down, a button jumps to the newest message. While you scroll up, a button jumps to the first one.
 * The buttons go away a short time after the scrolling stops.
 */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.JumpButtons(listState: LazyListState, total: Int) {
    val scope = rememberCoroutineScope()
    var direction by remember { mutableStateOf(0) } // 1 = down, -1 = up, 0 = none
    LaunchedEffect(listState) {
        var last = 0 to 0
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }.collect { now ->
            if (listState.isScrollInProgress && now != last) {
                direction = if (compareValuesBy(now, last, { it.first }, { it.second }) > 0) 1 else -1
            }
            last = now
        }
    }
    LaunchedEffect(listState.isScrollInProgress, direction) {
        if (!listState.isScrollInProgress && direction != 0) { delay(2500); direction = 0 }
    }
    JumpButton(direction == -1 && listState.canScrollBackward, up = true, Modifier.align(Alignment.TopCenter)) {
        direction = 0; scope.launch { listState.scrollToItem(0) }
    }
    JumpButton(direction == 1 && listState.canScrollForward, up = false, Modifier.align(Alignment.BottomCenter)) {
        direction = 0; scope.launch { if (total > 0) listState.scrollToItem(total - 1, Int.MAX_VALUE) }
    }
}

@Composable
private fun JumpButton(visible: Boolean, up: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = AppTheme.colors
    AnimatedVisibility(visible, modifier.padding(12.dp), enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier.size(44.dp).shadow(4.dp, CircleShape).clip(CircleShape).background(colors.surface).clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (up) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                if (up) "Jump to the first message" else "Jump to the newest message", tint = colors.text)
        }
    }
}

@Composable
private fun UserBubble(text: String, note: String? = null, onDelete: (() -> Unit)? = null) {
    val colors = AppTheme.colors
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        Text(
            text, style = MaterialTheme.typography.bodyLarge,
            color = if (note != null) colors.textSecondary else colors.text,
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(colors.userBubble)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )
        note?.let {
            Row(Modifier.padding(top = 4.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                // The note wraps first, so "Delete" keeps its full width at large text sizes.
                Text(it, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary,
                    modifier = Modifier.weight(1f, fill = false))
                onDelete?.let { delete ->
                    Text(
                        "Delete", style = MaterialTheme.typography.bodySmall, color = colors.accent, maxLines = 1, softWrap = false,
                        modifier = Modifier.padding(start = 6.dp).clip(RoundedCornerShape(6.dp)).clickable(onClick = delete)
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ApprovalCard(request: PcPermission, onDecide: (allow: Boolean, always: Boolean) -> Unit) {
    val colors = AppTheme.colors
    var busy by remember(request.id) { mutableStateOf(false) }
    fun decide(allow: Boolean, always: Boolean) { busy = true; onDecide(allow, always) }
    Column(
        Modifier
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .padding(14.dp),
    ) {
        Text("Allow Claude to use ${request.tool}?", style = MaterialTheme.typography.titleSmall, color = colors.text)
        if (request.summary.isNotBlank()) {
            Text(
                request.summary, fontFamily = MonoFamily, fontSize = 13.sp, color = colors.textSecondary,
                maxLines = 6, overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.codeBackground)
                    .padding(10.dp),
            )
        }
        // Allow gets its own row, so the buttons fit on narrow or zoomed screens.
        Button(
            onClick = { decide(true, false) }, enabled = !busy,
            colors = ButtonDefaults.buttonColors(containerColor = colors.primaryButton, contentColor = colors.onPrimaryButton),
            modifier = Modifier.padding(top = 10.dp).fillMaxWidth(),
        ) { Text("Allow", maxLines = 1) }
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { decide(false, false) }, enabled = !busy, modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)) {
                Text("Deny", color = colors.text, maxLines = 1)
            }
            if (request.canAlways) {
                OutlinedButton(onClick = { decide(true, true) }, enabled = !busy, modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)) {
                    Text("Always allow", color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun Composer(
    hint: String,
    onSend: suspend (String) -> String?,
    confirmResume: Boolean,
    working: Boolean,
    stopping: Boolean,
    onStop: () -> Unit,
) {
    val colors = AppTheme.colors
    val scope = rememberCoroutineScope()
    var text by rememberSaveable { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirming by remember { mutableStateOf(false) }
    fun send() {
        val message = text.trim()
        sending = true
        scope.launch {
            error = onSend(message)
            if (error == null) text = ""
            sending = false
        }
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            containerColor = colors.surface,
            title = { Text("Continue a closed session?", color = colors.text) },
            text = {
                Text(
                    "This session is not open on the PC. To continue it, Claude Code reads the whole " +
                        "conversation again. For a long session, that uses a lot of your usage limit. " +
                        "A message to an open session costs much less.",
                    color = colors.textSecondary,
                )
            },
            confirmButton = { TextButton(onClick = { confirming = false; send() }) { Text("Continue", color = colors.accent) } },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text("Cancel", color = colors.text) } },
        )
    }
    Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(12.dp)) {
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 8.dp, bottom = 6.dp)) }
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(colors.surface)
                .padding(start = 16.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            // The hint is inside the field, so a tap anywhere on a wrapped hint (large text sizes) opens the keyboard.
            BasicTextField(
                value = text, onValueChange = { text = it; error = null },
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.text),
                cursorBrush = SolidColor(colors.text), maxLines = 6,
                modifier = Modifier.weight(1f).padding(vertical = 12.dp),
                decorationBox = { inner ->
                    Box {
                        if (text.isEmpty()) Text(hint, style = MaterialTheme.typography.bodyLarge, color = colors.textSecondary)
                        inner()
                    }
                },
            )
            Spacer(Modifier.width(8.dp))
            // A plain round box, not an IconButton: an IconButton grows to 48 dp outside its size and covers the text.
            val canSend = text.isNotBlank() && !sending
            // While Claude works and the box is empty, the button stops the turn. With text, it sends (the message waits).
            val showStop = working && text.isBlank()
            Box(
                Modifier
                    .padding(vertical = 2.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (text.isNotBlank() || (showStop && !stopping)) colors.accent else colors.chip)
                    .clickable(enabled = canSend || (showStop && !stopping)) {
                        when {
                            showStop -> onStop()
                            confirmResume -> confirming = true
                            else -> send()
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (showStop) {
                    Icon(Icons.Filled.Stop, if (stopping) "Stopping" else "Stop", tint = if (stopping) colors.textSecondary else Color.White)
                } else {
                    Icon(Icons.Outlined.ArrowUpward, "Send", tint = if (text.isNotBlank()) Color.White else colors.textSecondary)
                }
            }
        }
    }
}

/** A file or image that was part of a message on the PC. The phone shows only its type and name. */
@Composable
private fun AttachmentChip(item: FeedItem.Attachment) {
    val colors = AppTheme.colors
    Box(Modifier.fillMaxWidth(), contentAlignment = if (item.fromUser) Alignment.CenterEnd else Alignment.CenterStart) {
        Row(
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(colors.chip)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(if (item.image) Icons.Outlined.Image else Icons.Outlined.AttachFile, null,
                tint = colors.textSecondary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(item.label, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * The context window of a Claude Code model, from the model name in the transcript. Older models have 200K;
 * newer ones have 1M. The transcript does not say if a session uses a smaller window, so this is a best guess.
 */
private fun contextWindowOf(model: String?): Long {
    val m = model.orEmpty()
    val old = Regex("""claude-3|claude-(opus|sonnet|haiku)-4-[015](\D|$)|claude-(opus|sonnet)-4(-\d{8})?$""").containsMatchIn(m)
    return if (old) 200_000 else 1_000_000
}
