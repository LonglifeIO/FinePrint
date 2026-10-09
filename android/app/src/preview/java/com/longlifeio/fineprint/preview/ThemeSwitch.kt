package com.longlifeio.fineprint.preview

import android.app.Activity
import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.longlifeio.fineprint.ui.LocalPalette
import com.longlifeio.fineprint.ui.TOUCH

/** The preview's theme: System follows the phone; Light and Dark set every screen's, whatever the phone's mode. */
enum class ThemeChoice(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }

/** The switch's choice, in memory for as long as the process lives. Never saved: each new process starts at System. */
internal val themeChoice = mutableStateOf(ThemeChoice.SYSTEM)

/**
 * The screens in the chosen theme. Every theme wrapper reads dark mode from the configuration's night bits
 * (isSystemInDarkTheme), so Light and Dark hand the screens a copy with those bits set; System hands them the phone's
 * own. The status and navigation bar icons follow, so they stay readable.
 */
@Composable
fun PreviewTheme(content: @Composable () -> Unit) {
    val phone = LocalConfiguration.current
    val choice = themeChoice.value
    val configuration = remember(phone, choice) {
        if (choice == ThemeChoice.SYSTEM) phone else Configuration(phone).apply {
            val night = if (choice == ThemeChoice.DARK) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
        }
    }
    val dark = configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).run {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
    CompositionLocalProvider(LocalConfiguration provides configuration, content = content)
}

/** System, Light and Dark under the preview's notice: each 48dp tall, the chosen one ticked as well as filled. */
@Composable
fun ThemeSwitch() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Theme", style = MaterialTheme.typography.labelMedium, color = LocalPalette.current.muted)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            ThemeChoice.entries.forEachIndexed { i, choice ->
                SegmentedButton(
                    selected = themeChoice.value == choice,
                    onClick = { themeChoice.value = choice },
                    shape = SegmentedButtonDefaults.itemShape(i, ThemeChoice.entries.size),
                    modifier = Modifier.heightIn(min = TOUCH),
                ) { Text(choice.label) }
            }
        }
    }
}
