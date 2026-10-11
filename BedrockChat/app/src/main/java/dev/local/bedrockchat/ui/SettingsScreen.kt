package dev.local.bedrockchat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AltRoute
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.input.PasswordVisualTransformation
import dev.local.bedrockchat.data.Effort
import dev.local.bedrockchat.data.Routing
import dev.local.bedrockchat.data.geoPrefix
import dev.local.bedrockchat.data.REGIONS
import dev.local.bedrockchat.data.modelById
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.local.bedrockchat.data.ColorMode

private fun routingSummary(routing: Routing, region: String) = when (routing) {
    Routing.GLOBAL -> "Global · any region"
    Routing.GEOGRAPHIC -> "Geographic · ${geoPrefix(region)?.uppercase() ?: "none for $region"}"
    Routing.IN_REGION -> "In-region · $region only"
}

@Composable
private fun Chevron() =
    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = AppTheme.colors.textSecondary)

@Composable
fun SettingsScreen(
    region: String,
    routing: Routing,
    onSetRouting: suspend (Routing) -> String?,
    apiKey: String,
    displayName: String,
    colorMode: ColorMode,
    haptics: Boolean,
    chatNotifications: Boolean,
    onChatNotifications: (Boolean) -> Unit,
    modelId: String,
    effort: Effort,
    onSelectModel: (String) -> Unit,
    onSelectEffort: (Effort) -> Unit,
    onUpdateKey: suspend (String) -> String?,
    onUpdateRegion: suspend (String) -> String?,
    onOpenDrawer: () -> Unit,
    onDisplayName: (String) -> Unit,
    onColorMode: (ColorMode) -> Unit,
    onHaptics: (Boolean) -> Unit,
    onDeleteAll: () -> Unit,
    onSignOut: () -> Unit,
) {
    val colors = AppTheme.colors
    var editName by remember { mutableStateOf(false) }
    var pickColor by remember { mutableStateOf(false) }
    var confirmDeleteAll by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }
    var editKey by remember { mutableStateOf(false) }
    var pickRegion by remember { mutableStateOf(false) }
    var pickModel by remember { mutableStateOf(false) }
    var pickRouting by remember { mutableStateOf(false) }
    var routingBusy by remember { mutableStateOf<Routing?>(null) }
    var regionBusy by remember { mutableStateOf<String?>(null) }
    var regionError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val notifyToggle = rememberChatNotificationToggle(onChatNotifications)
    // Show the real state: without the permission, nothing can show.
    val notifyOn = chatNotifications && rememberNotificationPermission()

    Column(Modifier.fillMaxSize().background(colors.background).statusBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp)) {
            IconButton(onClick = onOpenDrawer, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(Icons.Outlined.Menu, "Open menu", tint = colors.text)
            }
            Text("Settings", style = MaterialTheme.typography.titleLarge, color = colors.text, modifier = Modifier.align(Alignment.Center))
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding()
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.border, RoundedCornerShape(18.dp))
                    .padding(horizontal = 16.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Amazon Bedrock", style = MaterialTheme.typography.titleSmall, color = colors.text)
                    Text("$region · ${routing.label} routing", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                }
                Text(
                    "Bedrock",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onPrimaryButton,
                    modifier = Modifier.clip(CircleShape).background(colors.primaryButton).padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }

            Spacer(Modifier.height(16.dp))
            CardGroup {
                SettingRow(Icons.Outlined.AccountCircle, "Profile", displayName.ifBlank { "Add your name" }, onClick = { editName = true })
            }

            Spacer(Modifier.height(16.dp))
            Text("Bedrock account", style = MaterialTheme.typography.labelMedium, color = colors.textSecondary,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
            CardGroup {
                SettingRow(Icons.Outlined.Key, "API key", "…" + apiKey.takeLast(6), onClick = { editKey = true }) { Chevron() }
                GroupDivider()
                SettingRow(Icons.Outlined.Public, "Region", regionBusy?.let { "Checking $it…" } ?: region,
                    onClick = { pickRegion = true }) { Chevron() }
                GroupDivider()
                SettingRow(Icons.Outlined.AltRoute, "Routing", routingBusy?.let { "Checking ${it.label}…" } ?: routingSummary(routing, region),
                    onClick = { pickRouting = true }) { Chevron() }
                GroupDivider()
                SettingRow(Icons.Outlined.AutoAwesome, "Default model", "${modelById(modelId).name} · ${effort.label}",
                    onClick = { pickModel = true }) { Chevron() }
            }
            regionError?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp))
            }

            Spacer(Modifier.height(16.dp))
            CardGroup {
                SettingRow(Icons.Outlined.DarkMode, "Color mode", colorMode.label, onClick = { pickColor = true })
                GroupDivider()
                SettingRow(Icons.Outlined.NotificationsNone, "Notify when Claude responds") {
                    Switch(
                        checked = notifyOn,
                        onCheckedChange = notifyToggle,
                        colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF2E7BE6), checkedThumbColor = Color.White),
                    )
                }
                GroupDivider()
                SettingRow(Icons.Outlined.Vibration, "Haptic feedback") {
                    Switch(
                        checked = haptics,
                        onCheckedChange = onHaptics,
                        colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF2E7BE6), checkedThumbColor = Color.White),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            CardGroup {
                SettingRow(Icons.Outlined.DeleteSweep, "Delete all chats", onClick = { confirmDeleteAll = true })
                GroupDivider()
                SettingRow(Icons.AutoMirrored.Outlined.Logout, "Log out", onClick = { confirmSignOut = true })
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "Chats are stored only on this device. Requests go directly to Amazon Bedrock in $region.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Spacer(Modifier.height(32.dp))
        }
    }

    if (editName) {
        var name by remember { mutableStateOf(displayName) }
        AlertDialog(
            onDismissRequest = { editName = false },
            title = { Text("What should Claude call you?") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Name") }) },
            confirmButton = { TextButton(onClick = { onDisplayName(name); editName = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { editName = false }) { Text("Cancel") } },
            containerColor = colors.background,
        )
    }
    if (editKey) {
        var key by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { if (!busy) editKey = false },
            title = { Text("Change API key") },
            text = {
                Column {
                    Text("Paste a new Bedrock API key. It is checked with one short request before it replaces the current key.",
                        style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        key, { key = it; error = null }, singleLine = true, label = { Text("New Bedrock API key") },
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    error?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = key.isNotBlank() && !busy, onClick = {
                    busy = true
                    scope.launch {
                        error = onUpdateKey(key)
                        busy = false
                        if (error == null) editKey = false
                    }
                }) {
                    if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Save")
                }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { editKey = false }) { Text("Cancel") } },
            containerColor = colors.background,
        )
    }
    if (pickRegion) {
        AppSheet("Region", onDismiss = { pickRegion = false }) {
            CardGroup(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                REGIONS.forEachIndexed { index, option ->
                    if (index > 0) GroupDivider()
                    SettingRow(null, option, onClick = {
                        pickRegion = false
                        // Always check, even the current region, so the error shown is from this attempt.
                        regionBusy = option
                        regionError = null
                        scope.launch {
                            regionError = onUpdateRegion(option)?.let { "Could not switch to $option: $it" }
                            regionBusy = null
                        }
                    }) {
                        if (option == region) Icon(Icons.Outlined.Check, "Selected", tint = colors.link)
                    }
                }
            }
        }
    }
    if (pickRouting) {
        AppSheet("Routing", onDismiss = { pickRouting = false }) {
            CardGroup {
                Routing.entries.forEachIndexed { index, option ->
                    if (index > 0) GroupDivider()
                    SettingRow(null, option.label, option.description, onClick = {
                        pickRouting = false
                        // Always check, even the current routing, so the error shown is from this attempt.
                        routingBusy = option
                        regionError = null
                        scope.launch {
                            regionError = onSetRouting(option)?.let { "Could not switch to ${option.label} routing: $it" }
                            routingBusy = null
                        }
                    }) {
                        if (option == routing) Icon(Icons.Outlined.Check, "Selected", tint = colors.link)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Each option is checked with one short request before it is used. Availability depends on what is enabled in your AWS account.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
    if (pickModel) {
        ModelSheet(modelId, effort, onSelectModel, onSelectEffort, onDismiss = { pickModel = false })
    }
    if (pickColor) {
        AppSheet("Color mode", onDismiss = { pickColor = false }) {
            CardGroup {
                ColorMode.entries.forEachIndexed { index, mode ->
                    if (index > 0) GroupDivider()
                    SettingRow(null, mode.label, onClick = { onColorMode(mode); pickColor = false }) {
                        if (mode == colorMode) Icon(Icons.Outlined.Check, "Selected", tint = colors.link)
                    }
                }
            }
        }
    }
    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text("Delete all chats?") },
            text = { Text("All chats and their attachments will be deleted from this device. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { onDeleteAll(); confirmDeleteAll = false }) { Text("Delete all", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteAll = false }) { Text("Cancel") } },
            containerColor = colors.background,
        )
    }
    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("Log out?") },
            text = { Text("The API key is removed from this device. Your chats stay on the device.") },
            confirmButton = { TextButton(onClick = { onSignOut(); confirmSignOut = false }) { Text("Log out") } },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Cancel") } },
            containerColor = colors.background,
        )
    }
}
