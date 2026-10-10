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

/** The app's own mark: a four-point sparkle. */
@Composable
fun Spark(modifier: Modifier = Modifier, color: Color = AppTheme.colors.accent) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val c = Offset(w / 2, h / 2)
        val path = Path().apply {
            moveTo(c.x, 0f)
            cubicTo(c.x + w * 0.05f, h * 0.30f, w * 0.70f, c.y - h * 0.05f, w, c.y)
            cubicTo(w * 0.70f, c.y + h * 0.05f, c.x + w * 0.05f, h * 0.70f, c.x, h)
            cubicTo(c.x - w * 0.05f, h * 0.70f, w * 0.30f, c.y + h * 0.05f, 0f, c.y)
            cubicTo(w * 0.30f, c.y - h * 0.05f, c.x - w * 0.05f, h * 0.30f, c.x, 0f)
            close()
        }
        drawPath(path, color)
    }
}

/** Sparkle that turns slowly while Claude is working. */
@Composable
fun WorkingSpark(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "spark")
    val angle by transition.animateFloat(0f, 90f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "angle")
    val scale by transition.animateFloat(0.8f, 1.05f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "scale")
    Box(modifier.size(28.dp), contentAlignment = Alignment.Center) {
        Spark(Modifier.size(22.dp * scale).rotate(angle))
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
