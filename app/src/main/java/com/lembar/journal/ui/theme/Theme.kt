package com.lembar.journal.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.lembar.journal.R
import com.lembar.journal.data.ThemeMode
import com.lembar.journal.domain.Mood

@Immutable
data class LembarColors(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val border: Color,
    val ink: Color,
    val inkMuted: Color,
    val inkFaint: Color,
    val primary: Color,
    val onPrimary: Color,
    val accent: Color,
    val emptyDot: Color,
    val paper: Color,
    val danger: Color,
)

/** "Kertas": warm paper, charcoal buttons, pastel moods. */
val PaperColors = LembarColors(
    isDark = false,
    background = Color(0xFFF4F0EA),
    surface = Color(0xFFFFFDFA),
    surfaceAlt = Color(0xFFEBE6DE),
    border = Color(0xFFDAD4CA),
    ink = Color(0xFF2F2E2C),
    inkMuted = Color(0xFF77726B),
    inkFaint = Color(0xFFADA79E),
    primary = Color(0xFF3F3F3F),
    onPrimary = Color(0xFFF6F2EC),
    accent = Color(0xFFFF7A1A),
    emptyDot = Color(0xFFDFDBD5),
    paper = Color(0xFFFBF8F2),
    danger = Color(0xFFC2453D),
)

/** "Malam": near-black, one orange accent. */
val NightColors = LembarColors(
    isDark = true,
    background = Color(0xFF0F0F0F),
    surface = Color(0xFF1B1B1B),
    surfaceAlt = Color(0xFF252525),
    border = Color(0xFF2E2E2E),
    ink = Color(0xFFF3F3F1),
    inkMuted = Color(0xFF9A9A97),
    inkFaint = Color(0xFF5E5E5C),
    primary = Color(0xFFFF7A1A),
    onPrimary = Color(0xFFFFFFFF),
    accent = Color(0xFFFF7A1A),
    emptyDot = Color(0xFF2A2A2A),
    paper = Color(0xFFF4F0EA),
    danger = Color(0xFFFF6B5E),
)

object MoodPalette {
    val faceInk = Color(0xFF3A3A3A)
    fun color(mood: Mood): Color = when (mood) {
        Mood.Heavy -> Color(0xFFD8CBF0)
        Mood.Down -> Color(0xFFC5D6F3)
        Mood.Okay -> Color(0xFFCBE7C3)
        Mood.Good -> Color(0xFFF9D2B4)
        Mood.Great -> Color(0xFFFBE3A0)
    }
}

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun variable(res: Int, weight: Int) =
    Font(res, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

val SerifDisplay = FontFamily(Font(R.font.dm_serif_display, FontWeight.Normal))
val Grotesk = FontFamily(
    variable(R.font.space_grotesk, 400),
    variable(R.font.space_grotesk, 500),
    variable(R.font.space_grotesk, 600),
    variable(R.font.space_grotesk, 700),
)

@Immutable
data class LembarType(
    val display: TextStyle,
    val headline: TextStyle,
    val title: TextStyle,
    val body: TextStyle,
    val bodySmall: TextStyle,
    val label: TextStyle,
    val caption: TextStyle,
)

private fun typeFor(colors: LembarColors): LembarType {
    // Paper uses a serif for headings (design 1); Night uses the grotesk (design 2).
    val heading = if (colors.isDark) {
        TextStyle(fontFamily = Grotesk, fontWeight = FontWeight.Medium, letterSpacing = (-0.6).sp)
    } else {
        TextStyle(fontFamily = SerifDisplay, fontWeight = FontWeight.Normal, letterSpacing = (-0.2).sp)
    }
    val base = TextStyle(fontFamily = Grotesk, color = colors.ink)
    return LembarType(
        display = heading.copy(fontSize = 34.sp, lineHeight = 38.sp, color = colors.ink),
        headline = heading.copy(fontSize = 26.sp, lineHeight = 31.sp, color = colors.ink),
        title = base.copy(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
        body = base.copy(fontSize = 15.sp, lineHeight = 22.sp),
        bodySmall = base.copy(fontSize = 13.sp, lineHeight = 18.sp, color = colors.inkMuted),
        label = base.copy(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
        caption = base.copy(fontSize = 11.sp, lineHeight = 14.sp, color = colors.inkMuted, letterSpacing = 0.2.sp),
    )
}

private val LocalColors = staticCompositionLocalOf { PaperColors }
private val LocalType = staticCompositionLocalOf { typeFor(PaperColors) }

object Lembar {
    val colors: LembarColors @Composable get() = LocalColors.current
    val type: LembarType @Composable get() = LocalType.current
}

@Composable
fun isNight(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.System -> isSystemInDarkTheme()
    ThemeMode.Paper -> false
    ThemeMode.Night -> true
}

@Composable
fun LembarTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val colors = if (isNight(mode)) NightColors else PaperColors
    val type = typeFor(colors)
    // Material components (time picker, dialogs, snackbars) follow the same palette.
    val scheme = if (colors.isDark) {
        darkColorScheme(
            primary = colors.primary, onPrimary = colors.onPrimary,
            background = colors.background, onBackground = colors.ink,
            surface = colors.surface, onSurface = colors.ink,
            surfaceVariant = colors.surfaceAlt, onSurfaceVariant = colors.inkMuted,
            surfaceContainerHigh = colors.surface, surfaceContainerHighest = colors.surfaceAlt,
            surfaceContainer = colors.surface, surfaceContainerLow = colors.surface,
            outline = colors.border, outlineVariant = colors.border,
            secondaryContainer = colors.surfaceAlt, onSecondaryContainer = colors.ink,
            tertiaryContainer = colors.primary, onTertiaryContainer = colors.onPrimary,
            primaryContainer = colors.primary, onPrimaryContainer = colors.onPrimary,
            error = colors.danger,
        )
    } else {
        lightColorScheme(
            primary = colors.primary, onPrimary = colors.onPrimary,
            background = colors.background, onBackground = colors.ink,
            surface = colors.surface, onSurface = colors.ink,
            surfaceVariant = colors.surfaceAlt, onSurfaceVariant = colors.inkMuted,
            surfaceContainerHigh = colors.surface, surfaceContainerHighest = colors.surfaceAlt,
            surfaceContainer = colors.surface, surfaceContainerLow = colors.surface,
            outline = colors.border, outlineVariant = colors.border,
            secondaryContainer = colors.surfaceAlt, onSecondaryContainer = colors.ink,
            tertiaryContainer = colors.primary, onTertiaryContainer = colors.onPrimary,
            primaryContainer = colors.surfaceAlt, onPrimaryContainer = colors.ink,
            error = colors.danger,
        )
    }
    CompositionLocalProvider(LocalColors provides colors, LocalType provides type) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
