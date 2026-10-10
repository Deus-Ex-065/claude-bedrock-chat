package dev.local.bedrockchat.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Outline ghost used for incognito chats. */
val GhostIcon: ImageVector = ImageVector.Builder("Ghost", 24.dp, 24.dp, 24f, 24f).apply {
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.6f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ) {
        moveTo(5f, 20.5f)
        verticalLineTo(11f)
        arcTo(7f, 7f, 0f, false, true, 19f, 11f)
        verticalLineTo(20.5f)
        lineTo(16.7f, 18.8f)
        lineTo(14.3f, 20.5f)
        lineTo(12f, 18.8f)
        lineTo(9.7f, 20.5f)
        lineTo(7.3f, 18.8f)
        close()
    }
    path(fill = SolidColor(Color.Black)) {
        moveTo(9.5f, 11f)
        arcTo(1.1f, 1.1f, 0f, true, true, 9.49f, 11f)
        close()
        moveTo(14.5f, 11f)
        arcTo(1.1f, 1.1f, 0f, true, true, 14.49f, 11f)
        close()
    }
}.build()
