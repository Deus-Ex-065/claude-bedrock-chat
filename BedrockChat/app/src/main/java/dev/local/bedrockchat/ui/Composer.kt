package dev.local.bedrockchat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.local.bedrockchat.data.Attachment
import dev.local.bedrockchat.data.AttachmentKind
import dev.local.bedrockchat.data.Effort
import dev.local.bedrockchat.data.modelById
import java.io.File

@Composable
fun Composer(
    text: String,
    onTextChange: (String) -> Unit,
    placeholder: String,
    pending: List<Attachment>,
    modelId: String,
    effort: Effort,
    streaming: Boolean,
    speechAvailable: Boolean,
    onAdd: () -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onModel: () -> Unit,
    onSpeech: () -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val canSend = text.isNotBlank() || pending.isNotEmpty()
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(26.dp))
            .padding(10.dp)
    ) {
        if (pending.isNotEmpty()) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(start = 4.dp, top = 4.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                pending.forEach { AttachmentPreview(it, onRemove = { onRemoveAttachment(it.id) }) }
            }
        }
        BasicTextField(
            value = text,
            onValueChange = onTextChange,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.text),
            cursorBrush = SolidColor(colors.text),
            maxLines = 8,
            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = 8.dp, vertical = 10.dp),
            decorationBox = { inner ->
                Box {
                    if (text.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = colors.textSecondary)
                    inner()
                }
            },
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundButton(onClick = onAdd, background = colors.chip) {
                Icon(Icons.Outlined.Add, "Add to chat", tint = colors.text)
            }
            Spacer(Modifier.width(8.dp))
            // The chip shrinks on narrow screens, so the buttons on the right always fit.
            Box(Modifier.weight(1f)) {
            Row(
                Modifier
                    .clip(CircleShape)
                    .background(colors.chip)
                    .clickable(onClick = onModel)
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(modelById(modelId).name, style = MaterialTheme.typography.bodyMedium, color = colors.text,
                    maxLines = 1, softWrap = false)
                Spacer(Modifier.width(6.dp))
                // The effort label gives way first; the model name matters more.
                Text(effort.label, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary,
                    maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            }
            }
            Spacer(Modifier.width(8.dp))
            if (speechAvailable && !streaming) {
                RoundButton(onClick = onSpeech, background = colors.chip) {
                    Icon(Icons.Outlined.Mic, "Start speech input", tint = colors.text, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(8.dp))
            }
            when {
                streaming -> RoundButton(onClick = onStop, background = colors.chip) {
                    Icon(Icons.Rounded.Stop, "Stop", tint = colors.text)
                }
                else -> RoundButton(
                    onClick = { if (canSend) onSend() },
                    background = if (canSend) colors.primaryButton else colors.chip,
                ) {
                    Icon(Icons.Outlined.ArrowUpward, "Send", tint = if (canSend) colors.onPrimaryButton else colors.textSecondary)
                }
            }
        }
    }
}

@Composable
private fun RoundButton(onClick: () -> Unit, background: androidx.compose.ui.graphics.Color, content: @Composable () -> Unit) {
    Box(
        Modifier.size(46.dp).clip(CircleShape).background(background).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
fun AttachmentPreview(attachment: Attachment, onRemove: (() -> Unit)? = null, size: Int = 64) {
    val colors = AppTheme.colors
    Box {
        if (attachment.kind == AttachmentKind.IMAGE) {
            AsyncImage(
                model = File(attachment.path),
                contentDescription = attachment.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, colors.border, RoundedCornerShape(12.dp)),
            )
        } else {
            Row(
                Modifier
                    .heightIn(min = size.dp)
                    .width(180.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.chip)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (attachment.kind == AttachmentKind.PDF) Icons.Outlined.PictureAsPdf else Icons.AutoMirrored.Outlined.InsertDriveFile,
                    null, tint = colors.accent, modifier = Modifier.size(26.dp),
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(attachment.name, style = MaterialTheme.typography.bodyMedium, color = colors.text,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${if (attachment.kind == AttachmentKind.PDF) "PDF" else "Text"} · ${formatSize(attachment.sizeBytes)}",
                        style = MaterialTheme.typography.bodySmall, color = colors.textSecondary,
                    )
                }
            }
        }
        if (onRemove != null) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(3.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(colors.primaryButton)
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Close, "Remove", tint = colors.onPrimaryButton, modifier = Modifier.size(14.dp))
            }
        }
    }
}

fun formatSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
    bytes >= 1024 -> "${bytes / 1024} KB"
    else -> "$bytes B"
}
