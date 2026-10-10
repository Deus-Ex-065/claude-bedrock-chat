package dev.local.bedrockchat.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.local.bedrockchat.R

/** Colors beyond the Material scheme, matched to the reference app. */
@Immutable
data class ChatColors(
    val background: Color,
    val drawer: Color,
    val surface: Color,
    val userBubble: Color,
    val text: Color,
    val textSecondary: Color,
    val border: Color,
    val accent: Color,
    val primaryButton: Color,
    val onPrimaryButton: Color,
    val chip: Color,
    val codeBackground: Color,
    val link: Color,
    val scrim: Color,
)

private val LightColors = ChatColors(
    background = Color(0xFFFAF9F7),
    drawer = Color(0xFFF3F2EE),
    surface = Color(0xFFFFFFFF),
    userBubble = Color(0xFFF0EEE8),
    text = Color(0xFF1F1E1D),
    textSecondary = Color(0xFF73726C),
    border = Color(0xFFE3E1DA),
    accent = Color(0xFF0F7A72), // Teal, the app's own accent. Dark enough for white text on it.
    primaryButton = Color(0xFF141413),
    onPrimaryButton = Color(0xFFFFFFFF),
    chip = Color(0xFFF0EEE8),
    codeBackground = Color(0xFFF5F4F0),
    link = Color(0xFF2C5BB8),
    scrim = Color(0x66000000),
)

private val DarkColors = ChatColors(
    background = Color(0xFF262624),
    drawer = Color(0xFF1F1E1D),
    surface = Color(0xFF30302E),
    userBubble = Color(0xFF141413),
    text = Color(0xFFF5F4EF),
    textSecondary = Color(0xFFA6A39A),
    border = Color(0xFF3E3E3A),
    accent = Color(0xFF3DB8AB), // A lighter teal that reads on the dark background.
    primaryButton = Color(0xFFF5F4EF),
    onPrimaryButton = Color(0xFF141413),
    chip = Color(0xFF3A3A37),
    codeBackground = Color(0xFF1F1E1D),
    link = Color(0xFF8AB4F8),
    scrim = Color(0x99000000),
)

val LocalChatColors = staticCompositionLocalOf { LightColors }

private fun variable(res: Int, weight: Int, style: FontStyle = FontStyle.Normal) = Font(
    resId = res,
    weight = FontWeight(weight),
    style = style,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Serif face for Claude's replies and headings (Source Serif 4, OFL). */
val SerifFamily = FontFamily(
    variable(R.font.source_serif, 400),
    variable(R.font.source_serif, 500),
    variable(R.font.source_serif, 600),
    variable(R.font.source_serif, 700),
    variable(R.font.source_serif_italic, 400, FontStyle.Italic),
    variable(R.font.source_serif_italic, 700, FontStyle.Italic),
)

/** Sans face for UI chrome and user messages (Inter, OFL). */
val SansFamily = FontFamily(
    variable(R.font.inter, 400),
    variable(R.font.inter, 500),
    variable(R.font.inter, 600),
    variable(R.font.inter, 700),
)

val MonoFamily = FontFamily(
    variable(R.font.jetbrains_mono, 400),
    variable(R.font.jetbrains_mono, 700),
)

private val AppTypography = Typography().let { base ->
    Typography(
        displaySmall = TextStyle(fontFamily = SerifFamily, fontSize = 32.sp, lineHeight = 38.sp),
        headlineSmall = TextStyle(fontFamily = SerifFamily, fontSize = 24.sp, lineHeight = 30.sp),
        titleLarge = TextStyle(fontFamily = SerifFamily, fontSize = 22.sp, lineHeight = 28.sp),
        titleMedium = TextStyle(fontFamily = SansFamily, fontWeight = FontWeight.Medium, fontSize = 18.sp, lineHeight = 24.sp),
        titleSmall = TextStyle(fontFamily = SansFamily, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),
        bodyLarge = TextStyle(fontFamily = SansFamily, fontSize = 17.sp, lineHeight = 24.sp),
        bodyMedium = TextStyle(fontFamily = SansFamily, fontSize = 15.sp, lineHeight = 21.sp),
        bodySmall = TextStyle(fontFamily = SansFamily, fontSize = 13.sp, lineHeight = 18.sp),
        labelLarge = TextStyle(fontFamily = SansFamily, fontWeight = FontWeight.Medium, fontSize = 16.sp),
        labelMedium = TextStyle(fontFamily = SansFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp),
        labelSmall = TextStyle(fontFamily = SansFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp),
        headlineMedium = base.headlineMedium.copy(fontFamily = SerifFamily),
        headlineLarge = base.headlineLarge.copy(fontFamily = SerifFamily),
        displayMedium = base.displayMedium.copy(fontFamily = SerifFamily),
        displayLarge = base.displayLarge.copy(fontFamily = SerifFamily),
    )
}

/** Body style for assistant replies. */
val ReplyTextStyle = TextStyle(fontFamily = SerifFamily, fontSize = 18.sp, lineHeight = 27.sp)

private fun scheme(c: ChatColors, dark: Boolean): ColorScheme = (if (dark) darkColorScheme() else lightColorScheme()).copy(
    primary = c.primaryButton,
    onPrimary = c.onPrimaryButton,
    secondary = c.accent,
    background = c.background,
    onBackground = c.text,
    surface = c.background,
    onSurface = c.text,
    surfaceVariant = c.chip,
    onSurfaceVariant = c.textSecondary,
    surfaceContainerLow = c.surface,
    surfaceContainer = c.surface,
    surfaceContainerHigh = c.surface,
    surfaceContainerHighest = c.chip,
    outline = c.border,
    outlineVariant = c.border,
)

@Composable
fun ChatTheme(dark: Boolean, content: @Composable () -> Unit) {
    val colors = if (dark) DarkColors else LightColors
    CompositionLocalProvider(LocalChatColors provides colors) {
        MaterialTheme(colorScheme = scheme(colors, dark), typography = AppTypography, content = content)
    }
}

object AppTheme {
    val colors: ChatColors
        @Composable get() = LocalChatColors.current
}
