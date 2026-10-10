package dev.local.bedrockchat.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.local.bedrockchat.data.ClaudeModel
import dev.local.bedrockchat.data.Effort
import dev.local.bedrockchat.data.MODELS
import dev.local.bedrockchat.data.MORE_MODELS

@Composable
fun AddToChatSheet(onDismiss: () -> Unit, onCamera: () -> Unit, onPhotos: () -> Unit, onFiles: () -> Unit) {
    AppSheet("Add to chat", onDismiss) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile(Icons.Outlined.CameraAlt, "Camera", Modifier.weight(1f)) { onDismiss(); onCamera() }
            Tile(Icons.Outlined.Image, "Photos", Modifier.weight(1f)) { onDismiss(); onPhotos() }
            Tile(Icons.Outlined.UploadFile, "Files", Modifier.weight(1f)) { onDismiss(); onFiles() }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Images, PDFs, and text files are sent to Claude with your next message.",
            style = MaterialTheme.typography.bodySmall,
            color = AppTheme.colors.textSecondary,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun Tile(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    CardGroup(modifier.clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick)) {
        Column(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            IconCircle(icon, 58)
            Spacer(Modifier.height(10.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge, color = AppTheme.colors.text)
        }
    }
}

private enum class ModelPage { MODELS, EFFORT, MORE }

@Composable
fun ModelSheet(
    selectedId: String,
    effort: Effort,
    onSelectModel: (String) -> Unit,
    onSelectEffort: (Effort) -> Unit,
    onDismiss: () -> Unit,
) {
    var page by remember { mutableStateOf(ModelPage.MODELS) }
    when (page) {
        ModelPage.MODELS -> AppSheet("Select model", onDismiss) {
            CardGroup {
                MODELS.forEachIndexed { index, model ->
                    if (index > 0) GroupDivider()
                    ModelRow(model, model.id == selectedId) { onSelectModel(model.id); onDismiss() }
                }
            }
            Spacer(Modifier.height(16.dp))
            CardGroup {
                SettingRow(null, "Effort", onClick = { page = ModelPage.EFFORT }) {
                    Text(effort.label, style = MaterialTheme.typography.bodyLarge, color = AppTheme.colors.textSecondary)
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = AppTheme.colors.textSecondary)
                }
            }
            Spacer(Modifier.height(16.dp))
            CardGroup {
                SettingRow(null, "More models", onClick = { page = ModelPage.MORE }) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = AppTheme.colors.textSecondary)
                }
            }
        }
        ModelPage.EFFORT -> AppSheet("Effort", onDismiss, onBack = { page = ModelPage.MODELS }) {
            CardGroup {
                Effort.entries.forEachIndexed { index, option ->
                    if (index > 0) GroupDivider()
                    SettingRow(null, option.label, option.note, onClick = { onSelectEffort(option); onDismiss() }) {
                        if (option == effort) Icon(Icons.Outlined.Check, "Selected", tint = AppTheme.colors.link)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Higher effort means more thorough responses, but takes longer and uses more of your Bedrock quota.",
                style = MaterialTheme.typography.bodyMedium,
                color = AppTheme.colors.textSecondary,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        ModelPage.MORE -> AppSheet("More models", onDismiss, onBack = { page = ModelPage.MODELS }) {
            CardGroup {
                MORE_MODELS.forEachIndexed { index, model ->
                    if (index > 0) GroupDivider()
                    ModelRow(model, model.id == selectedId) { onSelectModel(model.id); onDismiss() }
                }
            }
        }
    }
}

@Composable
private fun ModelRow(model: ClaudeModel, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(model.name, style = MaterialTheme.typography.titleLarge.copy(fontFamily = SansFamily), color = AppTheme.colors.text)
            if (model.description.isNotBlank()) {
                Text(model.description, style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
            }
        }
        if (selected) Icon(Icons.Outlined.Check, "Selected", tint = AppTheme.colors.link, modifier = Modifier.size(26.dp))
    }
}
