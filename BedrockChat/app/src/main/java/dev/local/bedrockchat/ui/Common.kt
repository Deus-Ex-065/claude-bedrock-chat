package dev.local.bedrockchat.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * The app's own mark: three stacked rock layers (strata), short on top and widest at the bottom.
 * [layerAlpha] sets the opacity of each layer, from the top. The working animation uses it.
 */
@Composable
fun Mark(
    modifier: Modifier = Modifier,
    color: Color = AppTheme.colors.accent,
    layerAlpha: (Int) -> Float = { 1f },
) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val bar = h * 0.22f
        val gap = h * 0.11f
        val top = (h - 3 * bar - 2 * gap) / 2
        listOf(0.5f, 0.75f, 1f).forEachIndexed { i, widthPart ->
            val bw = w * widthPart
            drawRoundRect(
                color = color.copy(alpha = color.alpha * layerAlpha(i)),
                topLeft = Offset((w - bw) / 2, top + i * (bar + gap)),
                size = androidx.compose.ui.geometry.Size(bw, bar),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(bar / 2, bar / 2),
            )
        }
    }
}

/** The mark while Claude is working: the layers light up one after another, top to bottom. */
@Composable
fun WorkingMark(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "mark")
    val phase by transition.animateFloat(0f, 3f, infiniteRepeatable(tween(1200)), label = "phase")
    Box(modifier.size(28.dp), contentAlignment = Alignment.Center) {
        Mark(Modifier.size(22.dp)) { i ->
            // The layer at the current phase is brightest. The others fade toward 0.3.
            val d = ((phase - i) % 3f + 3f) % 3f
            (1f - d / 3f * 0.7f).coerceIn(0.3f, 1f)
        }
    }
}

/** Bottom sheet with the reference app's header: close or back on the left, centred title. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSheet(
    title: String,
    onDismiss: () -> Unit,
    onBack: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = AppTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.background,
        shape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp),
    ) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            IconButton(onClick = onBack ?: onDismiss, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(if (onBack != null) Icons.AutoMirrored.Outlined.ArrowBack else Icons.Outlined.Close,
                    if (onBack != null) "Go back" else "Close sheet", tint = colors.text)
            }
            Text(title, style = MaterialTheme.typography.titleMedium, color = colors.text,
                textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.Center))
        }
        Column(Modifier.padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 24.dp), content = content)
    }
}

/** Rounded white card that groups rows, as in the reference sheets and settings. */
@Composable
fun CardGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(AppTheme.colors.surface),
        content = content,
    )
}

@Composable
fun GroupDivider() = HorizontalDivider(color = AppTheme.colors.background, thickness = 1.5.dp)

@Composable
fun SettingRow(
    icon: ImageVector?,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val colors = AppTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = colors.text, modifier = Modifier.size(24.dp))
            Box(Modifier.size(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = colors.text)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
        }
        trailing()
    }
}

/** Grey circle with an icon, as used for tiles and row leading icons. */
@Composable
fun IconCircle(icon: ImageVector, size: Int = 44, modifier: Modifier = Modifier) {
    Box(modifier.size(size.dp).clip(CircleShape).background(AppTheme.colors.chip), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = AppTheme.colors.text, modifier = Modifier.size((size * 0.5).dp))
    }
}
