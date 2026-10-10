package dev.local.bedrockchat.ui

import android.text.format.DateUtils
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.local.bedrockchat.data.ConversationSummary

@Composable
fun ChatsScreen(
    summaries: List<ConversationSummary>,
    onOpenDrawer: () -> Unit,
    onOpen: (String) -> Unit,
    onNewChat: () -> Unit,
    onRename: (String, String) -> Unit,
    onToggleStar: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    val colors = AppTheme.colors
    var query by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf<ConversationSummary?>(null) }
    var deleting by remember { mutableStateOf<ConversationSummary?>(null) }
    val filtered = summaries.filter { query.isBlank() || it.title.contains(query.trim(), ignoreCase = true) }

    Box(Modifier.fillMaxSize().background(colors.background).statusBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp)) {
                IconButton(onClick = onOpenDrawer, modifier = Modifier.align(Alignment.CenterStart)) {
                    Icon(Icons.Outlined.Menu, "Open menu", tint = colors.text)
                }
                Text("Chats", style = MaterialTheme.typography.titleLarge, color = colors.text, modifier = Modifier.align(Alignment.Center))
            }
            Row(
                Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .clip(CircleShape)
                    .background(colors.chip)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Search, "Search", tint = colors.textSecondary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.text),
                    cursorBrush = SolidColor(colors.text),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        Box {
                            if (query.isEmpty()) Text("Search", style = MaterialTheme.typography.bodyLarge, color = colors.textSecondary)
                            inner()
                        }
                    },
                )
            }
            Spacer(Modifier.height(8.dp))
            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (summaries.isEmpty()) "Ask Claude anything. Your chats with Claude will show up here."
                        else "No chats match your search.",
                        style = MaterialTheme.typography.bodyLarge, color = colors.textSecondary, textAlign = TextAlign.Center,
                    )
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(filtered, key = { it.id }) { chat ->
                        ChatRow(chat, onOpen = { onOpen(chat.id) }, onRename = { renaming = chat },
                            onToggleStar = { onToggleStar(chat.id) }, onDelete = { deleting = chat })
                    }
                    item { Spacer(Modifier.height(96.dp)) }
                }
            }
        }

        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(20.dp)
                .size(60.dp)
                .shadow(6.dp, CircleShape)
                .clip(CircleShape)
                .background(colors.primaryButton)
                .combinedClickable(onClick = onNewChat),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.AddComment, "New chat", tint = colors.onPrimaryButton)
        }
    }

    renaming?.let { chat ->
        var title by remember(chat.id) { mutableStateOf(chat.title) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename chat") },
            text = { OutlinedTextField(title, { title = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { onRename(chat.id, title); renaming = null }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } },
            containerColor = colors.background,
        )
    }
    deleting?.let { chat ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete chat?") },
            text = { Text("\"${chat.title}\" will be deleted from this device.") },
            confirmButton = {
                TextButton(onClick = { onDelete(chat.id); deleting = null }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
            containerColor = colors.background,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatRow(
    chat: ConversationSummary,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onToggleStar: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = AppTheme.colors
    var menu by remember { mutableStateOf(false) }
    Box {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .clip(RoundedCornerShape(14.dp))
                .combinedClickable(onClick = onOpen, onLongClick = { menu = true })
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (chat.starred) {
                    Icon(Icons.Outlined.Star, null, tint = colors.textSecondary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(chat.title, style = MaterialTheme.typography.bodyLarge, color = colors.text,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                DateUtils.getRelativeTimeSpanString(chat.updatedAt).toString(),
                style = MaterialTheme.typography.bodySmall, color = colors.textSecondary,
            )
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = colors.surface) {
            DropdownMenuItem(
                text = { Text(if (chat.starred) "Unstar" else "Star") },
                leadingIcon = { Icon(if (chat.starred) Icons.Outlined.Star else Icons.Outlined.StarBorder, null) },
                onClick = { menu = false; onToggleStar() },
            )
            DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(Icons.Outlined.DriveFileRenameOutline, null) },
                onClick = { menu = false; onRename() })
            DropdownMenuItem(
                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) },
                onClick = { menu = false; onDelete() },
            )
        }
    }
}
