package dev.local.bedrockchat.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.local.bedrockchat.ChatViewModel
import dev.local.bedrockchat.PcViewModel
import dev.local.bedrockchat.data.ConversationSummary
import kotlinx.coroutines.launch

private enum class Screen { CHAT, CHATS, SETTINGS, PC, PC_SESSION }

@Composable
fun MainScreen(vm: ChatViewModel) {
    val colors = AppTheme.colors
    val state by vm.state.collectAsStateWithLifecycle()
    val summaries by vm.summaries.collectAsStateWithLifecycle()
    val displayName by vm.displayName.collectAsStateWithLifecycle()
    val offerNotifications by vm.offerChatNotifications.collectAsStateWithLifecycle()
    val chatNotifications by vm.chatNotifications.collectAsStateWithLifecycle()
    val haptics by vm.haptics.collectAsStateWithLifecycle()
    val readLinks by vm.readLinks.collectAsStateWithLifecycle()
    val memoryOn by vm.memoryOn.collectAsStateWithLifecycle()
    val memoryItems by vm.memoryItems.collectAsStateWithLifecycle()
    val credentials by vm.credentials.collectAsStateWithLifecycle()
    val colorMode by vm.colorMode.collectAsStateWithLifecycle()
    val notice by vm.notices.collectAsStateWithLifecycle()
    val askName by vm.askName.collectAsStateWithLifecycle()
    val pc: PcViewModel = viewModel()
    val pcState by pc.state.collectAsStateWithLifecycle()

    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var screen by rememberSaveable { mutableStateOf(Screen.CHAT) }
    val snackbar = remember { SnackbarHostState() }

    val openRequest by dev.local.bedrockchat.PcScreenState.openRequest.collectAsStateWithLifecycle()
    LaunchedEffect(openRequest) {
        openRequest?.let {
            pc.open(it)
            screen = Screen.PC_SESSION
            scope.launch { drawer.close() }
            dev.local.bedrockchat.PcScreenState.openRequest.value = null
        }
    }
    val openChat by dev.local.bedrockchat.AppVisibility.openChat.collectAsStateWithLifecycle()
    LaunchedEffect(openChat) {
        openChat?.let {
            if (it.isNotBlank() && it != vm.state.value.conversation.id) vm.openChat(it)
            screen = Screen.CHAT
            scope.launch { drawer.close() }
            dev.local.bedrockchat.AppVisibility.openChat.value = null
        }
    }
    LaunchedEffect(notice) {
        notice?.let { snackbar.showSnackbar(it); vm.consumeNotice() }
    }
    BackHandler(enabled = drawer.isOpen) { scope.launch { drawer.close() } }
    BackHandler(enabled = !drawer.isOpen && screen != Screen.CHAT && screen != Screen.PC_SESSION) { screen = Screen.CHAT }

    fun go(target: Screen) {
        screen = target
        scope.launch { drawer.close() }
    }

    val actions = remember(vm) {
        ChatActions(
            send = vm::send,
            stop = vm::stop,
            retry = vm::retry,
            editAndResend = vm::editAndResend,
            newChat = vm::newChat,
            addAttachments = vm::addAttachments,
            removeAttachment = vm::removeAttachment,
            setModel = vm::setModel,
            setEffort = vm::setEffort,
            rename = vm::renameChat,
            toggleStar = vm::toggleStar,
            delete = vm::deleteChat,
            setChatNotifications = vm::setChatNotifications,
            dismissChatNotificationsOffer = vm::dismissChatNotificationsOffer,
        )
    }

    ModalNavigationDrawer(
        drawerState = drawer,
        scrimColor = colors.scrim,
        drawerContent = {
            DrawerContent(
                summaries = summaries,
                currentId = state.conversation.id,
                displayName = displayName,
                onChats = { go(Screen.CHATS) },
                onPc = { go(Screen.PC) },
                onOpenChat = { vm.openChat(it); go(Screen.CHAT) },
                onNewChat = { vm.newChat(); go(Screen.CHAT) },
                onSettings = { go(Screen.SETTINGS) },
            )
        },
    ) {
        Box(Modifier.fillMaxSize()) {
            when (screen) {
                Screen.CHAT -> ChatScreen(state, displayName, haptics, offerNotifications, actions, onOpenDrawer = { scope.launch { drawer.open() } })
                Screen.CHATS -> ChatsScreen(
                    summaries = summaries,
                    onOpenDrawer = { scope.launch { drawer.open() } },
                    onOpen = { vm.openChat(it); screen = Screen.CHAT },
                    onNewChat = { vm.newChat(); screen = Screen.CHAT },
                    onRename = vm::renameChat,
                    onToggleStar = vm::toggleStar,
                    onDelete = vm::deleteChat,
                )
                Screen.PC -> PcSessionsScreen(
                    pc, pcState,
                    onOpenDrawer = { scope.launch { drawer.open() } },
                    onOpenSession = { pc.open(it); screen = Screen.PC_SESSION },
                )
                Screen.PC_SESSION -> PcSessionScreen(pc, pcState, onBack = { pc.close(); screen = Screen.PC })
                Screen.SETTINGS -> SettingsScreen(
                    region = credentials?.region.orEmpty(),
                    routing = credentials?.routing ?: dev.local.bedrockchat.data.Routing.GLOBAL,
                    onSetRouting = vm::setRouting,
                    apiKey = credentials?.apiKey.orEmpty(),
                    displayName = displayName,
                    colorMode = colorMode,
                    haptics = haptics,
                    chatNotifications = chatNotifications,
                    readLinks = readLinks,
                    onReadLinks = vm::setReadLinks,
                    memoryOn = memoryOn,
                    onMemory = vm::setMemory,
                    memoryItems = memoryItems,
                    onDeleteMemoryItem = vm::deleteMemoryItem,
                    onClearMemory = vm::clearMemory,
                    onChatNotifications = vm::setChatNotifications,
                    modelId = state.modelId,
                    effort = state.effort,
                    onSelectModel = vm::setModel,
                    onSelectEffort = vm::setEffort,
                    onUpdateKey = { vm.updateCredentials(apiKey = it) },
                    onUpdateRegion = { vm.updateCredentials(region = it) },
                    onOpenDrawer = { scope.launch { drawer.open() } },
                    onDisplayName = vm::setDisplayName,
                    onColorMode = vm::setColorMode,
                    onHaptics = vm::setHaptics,
                    onDeleteAll = vm::deleteAllChats,
                    onSignOut = vm::signOut,
                )
            }
            if (askName) NameDialog(vm::answerName)
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 96.dp))
        }
    }
}

@Composable
private fun DrawerContent(
    summaries: List<ConversationSummary>,
    currentId: String,
    displayName: String,
    onChats: () -> Unit,
    onPc: () -> Unit,
    onOpenChat: (String) -> Unit,
    onNewChat: () -> Unit,
    onSettings: () -> Unit,
) {
    val colors = AppTheme.colors
    ModalDrawerSheet(
        drawerContainerColor = colors.drawer,
        drawerShape = RoundedCornerShape(topEnd = 0.dp, bottomEnd = 24.dp),
        modifier = Modifier.width(320.dp),
    ) {
        Column(Modifier.fillMaxHeight().statusBarsPadding().navigationBarsPadding()) {
            Text(
                "Bedrock Chat", // The app's own name. The model is Claude; the app is not.
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Medium, fontSize = 30.sp),
                color = colors.text,
                modifier = Modifier.padding(start = 24.dp, top = 18.dp, bottom = 18.dp),
            )
            DrawerItem(Icons.Outlined.ChatBubbleOutline, "Chats", onChats)
            DrawerItem(Icons.Outlined.Computer, "PC sessions", onPc)

            if (summaries.isNotEmpty()) {
                Text("Recents", style = MaterialTheme.typography.labelMedium, color = colors.textSecondary,
                    modifier = Modifier.padding(start = 24.dp, top = 20.dp, bottom = 6.dp))
            }
            LazyColumn(Modifier.weight(1f)) {
                items(summaries.take(30), key = { it.id }) { chat ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (chat.id == currentId) colors.chip else Color.Transparent)
                            .clickable { onOpenChat(chat.id) }
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (chat.starred) {
                            Icon(Icons.Outlined.Star, null, tint = colors.textSecondary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(chat.title, style = MaterialTheme.typography.bodyLarge, color = colors.text,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(56.dp).clip(CircleShape).background(colors.accent).clickable(onClick = onSettings),
                    contentAlignment = Alignment.Center,
                ) {
                    // Without a name, a person icon, not a made-up letter.
                    displayName.firstOrNull()?.let {
                        Text(it.uppercase(), color = Color.White, style = MaterialTheme.typography.titleSmall)
                    } ?: Icon(Icons.Outlined.Person, "Profile", tint = Color.White)
                }
                Spacer(Modifier.weight(1f))
                Row(
                    Modifier
                        .height(56.dp)
                        .clip(CircleShape)
                        .background(colors.primaryButton)
                        .clickable(onClick = onNewChat)
                        .padding(horizontal = 22.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Add, null, tint = colors.onPrimaryButton)
                    Spacer(Modifier.width(8.dp))
                    Text("New chat", style = MaterialTheme.typography.titleSmall, color = colors.onPrimaryButton)
                }
            }
        }
    }
}

/** Asked once after the first sign-in. The greeting and the profile button use the name. */
@Composable
private fun NameDialog(onAnswer: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = {},
        title = { Text("What should Claude call you?") },
        text = {
            Column {
                Text("You can change it later in Settings, under Profile.", style = MaterialTheme.typography.bodyMedium,
                    color = AppTheme.colors.textSecondary, modifier = Modifier.padding(bottom = 12.dp))
                OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Name") })
            }
        },
        confirmButton = { TextButton(onClick = { onAnswer(name) }, enabled = name.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = { onAnswer("") }) { Text("Skip") } },
        containerColor = AppTheme.colors.background,
    )
}

@Composable
private fun DrawerItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    val colors = AppTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .height(56.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = colors.text, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp), color = colors.text)
    }
}
