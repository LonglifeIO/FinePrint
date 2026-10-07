package com.longlifeio.fineprint.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.util.Locale

/** A fixed palette (no wallpaper colours), so the signal colours look the same on every phone. */
private val LightColors = lightColorScheme(
    primary = Color(0xFF2D5F8B), onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E4F7), onPrimaryContainer = Color(0xFF0B2A45),
    secondary = Color(0xFF4F6170), onSecondary = Color.White,
    secondaryContainer = Color(0xFFD6E4F0), onSecondaryContainer = Color(0xFF0C1D2A),
    tertiary = Color(0xFF6B5778), tertiaryContainer = Color(0xFFF2DAFF), onTertiaryContainer = Color(0xFF251431),
    background = Color(0xFFFBFCFD), onBackground = Color(0xFF191C1F),
    surface = Color(0xFFFBFCFD), onSurface = Color(0xFF191C1F),
    surfaceVariant = Color(0xFFE1E5EA), onSurfaceVariant = Color(0xFF42474D),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF4F6F8), surfaceContainer = Color(0xFFEEF0F3),
    surfaceContainerHigh = Color(0xFFE8EBEE), surfaceContainerHighest = Color(0xFFE2E5E9),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA6C8EE), onPrimary = Color(0xFF0B2A45),
    primaryContainer = Color(0xFF23466A), onPrimaryContainer = Color(0xFFD3E4F7),
    secondary = Color(0xFFB7C9D8), onSecondary = Color(0xFF213240),
    secondaryContainer = Color(0xFF384957), onSecondaryContainer = Color(0xFFD6E4F0),
    tertiary = Color(0xFFD7BEE4), tertiaryContainer = Color(0xFF523F5F), onTertiaryContainer = Color(0xFFF2DAFF),
    background = Color(0xFF111417), onBackground = Color(0xFFE1E3E6),
    surface = Color(0xFF111417), onSurface = Color(0xFFE1E3E6),
    surfaceVariant = Color(0xFF41474D), onSurfaceVariant = Color(0xFFC1C7CE),
    surfaceContainerLowest = Color(0xFF0C0F11), surfaceContainerLow = Color(0xFF191C1F), surfaceContainer = Color(0xFF1D2023),
    surfaceContainerHigh = Color(0xFF272A2D), surfaceContainerHighest = Color(0xFF323538),
)

@Composable
fun FinePrintTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}

/** A small rounded tag such as "Granted" or "3 trackers"; [outline] draws it as a border instead of a fill. */
@Composable
fun StatusLabel(text: String, container: Color, content: Color, modifier: Modifier = Modifier, outline: Boolean = false) {
    Surface(
        color = if (outline) Color.Transparent else container,
        contentColor = content,
        shape = RoundedCornerShape(6.dp),
        border = if (outline) BorderStroke(1.dp, content) else null,
        modifier = modifier,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

fun trackerCount(n: Int): String = if (n == 1) "1 tracker" else "$n trackers"

fun seconds(ms: Long): String = String.format(Locale.getDefault(), "%.1f s", ms / 1000.0)
