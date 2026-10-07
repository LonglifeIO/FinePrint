package com.longlifeio.fineprint.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.longlifeio.fineprint.R

/*
 * G5 step 1, debug only: the design brief's palettes and font pairings, for mockups. No production
 * screen uses anything in this package; it isn't compiled into release builds.
 */

/** One bucket's colours: a tinted container, the text on it, and an accent for icons and bars. */
data class BucketColours(val container: Color, val onContainer: Color, val accent: Color)

data class Palette(
    val name: String,
    val dark: Boolean,
    val surface: Color,
    val onSurface: Color,
    val muted: Color,
    /** Tonal fill for cards on the warm off-white (or near-black) surface. */
    val card: Color,
    val outline: Color,
    /** Links and "Sources (n)". */
    val primary: Color,
    val staysHere: BucketColours,
    val usedForMore: BucketColours,
    val goesElsewhere: BucketColours,
    /** Tiers are neutral: one chip colour pair for all four, told apart by icon and word. */
    val tierChip: Color,
    val onTierChip: Color,
)

/** Palette A, "Harbour": blue / amber / plum from the Okabe-Ito set (the brief's table). */
val HarbourLight = Palette(
    "Harbour", false, Color(0xFFFAF8F5), Color(0xFF1C1B1A), Color(0xFF5E5A55), Color(0xFFF1EDE7), Color(0xFFD8D2C8), Color(0xFF0B5C94),
    BucketColours(Color(0xFFDCEEFB), Color(0xFF0B3D63), Color(0xFF0072B2)),
    BucketColours(Color(0xFFFDEBC8), Color(0xFF5A3A00), Color(0xFFB26B00)),
    BucketColours(Color(0xFFF3E0EC), Color(0xFF5E2348), Color(0xFFA8437F)),
    Color(0xFFE4E0EE), Color(0xFF2D2A3E),
)
val HarbourDark = Palette(
    "Harbour", true, Color(0xFF121417), Color(0xFFE6E2DC), Color(0xFFA9A49C), Color(0xFF1C1F24), Color(0xFF3A3E45), Color(0xFF7FB8E6),
    BucketColours(Color(0xFF12324A), Color(0xFFCFE6F7), Color(0xFF7FB8E6)),
    BucketColours(Color(0xFF3D2A05), Color(0xFFFBE3B5), Color(0xFFE9B44C)),
    BucketColours(Color(0xFF3B1A2E), Color(0xFFF4D6E8), Color(0xFFE59BC9)),
    Color(0xFF2D2A3E), Color(0xFFE4E0EE),
)

/** Palette B, "Field notes": teal / ochre / indigo on warmer neutrals. */
val FieldLight = Palette(
    "Field notes", false, Color(0xFFFBFAF7), Color(0xFF1B1C1A), Color(0xFF5D5B54), Color(0xFFF0EEE6), Color(0xFFD7D3C7), Color(0xFF006B5A),
    BucketColours(Color(0xFFD7F0EA), Color(0xFF0E4A40), Color(0xFF00806B)),
    BucketColours(Color(0xFFF6E7C1), Color(0xFF5C4300), Color(0xFFA06A00)),
    BucketColours(Color(0xFFE2E3F8), Color(0xFF2B2F7A), Color(0xFF4B50B0)),
    Color(0xFFE6E3EC), Color(0xFF2D2A3E),
)
val FieldDark = Palette(
    "Field notes", true, Color(0xFF141311), Color(0xFFE7E3DA), Color(0xFFADA89D), Color(0xFF1F1D1A), Color(0xFF3B3833), Color(0xFF6FD1BC),
    BucketColours(Color(0xFF0F3A33), Color(0xFFCDEDE5), Color(0xFF6FD1BC)),
    BucketColours(Color(0xFF3A2C08), Color(0xFFF3E2B8), Color(0xFFE3B95A)),
    BucketColours(Color(0xFF24264F), Color(0xFFDCDDF7), Color(0xFFA9ADF0)),
    Color(0xFF2D2A3E), Color(0xFFE4E0EE),
)

/** A body face, a display face and a face for quoted words. */
data class FontPair(val name: String, val body: FontFamily, val display: FontFamily, val quote: FontFamily)

private fun font(res: Int, weight: Int, italic: Boolean = false, vararg axes: FontVariation.Setting) = Font(
    resId = res,
    weight = FontWeight(weight),
    style = if (italic) FontStyle.Italic else FontStyle.Normal,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight), *axes),
)

/** Fraunces at its soft, low-contrast settings, as the brief asks. */
private val soft = arrayOf(FontVariation.Setting("SOFT", 100f), FontVariation.Setting("WONK", 0f), FontVariation.Setting("opsz", 48f))

val AtkinsonFraunces = FontPair(
    "Atkinson Hyperlegible Next + Fraunces",
    FontFamily(font(R.font.atkinson_next, 400), font(R.font.atkinson_next, 600), font(R.font.atkinson_next, 700), font(R.font.atkinson_next_italic, 400, true)),
    FontFamily(font(R.font.fraunces, 500, false, *soft), font(R.font.fraunces, 600, false, *soft)),
    FontFamily(font(R.font.fraunces_italic, 400, true, *soft), font(R.font.fraunces_italic, 500, true, *soft)),
)

val PlexLiterata = FontPair(
    "IBM Plex Sans + Literata",
    FontFamily(font(R.font.plex_sans, 400), font(R.font.plex_sans, 500), font(R.font.plex_sans, 600), font(R.font.plex_sans_italic, 400, true)),
    FontFamily(font(R.font.literata, 500, false, FontVariation.Setting("opsz", 36f)), font(R.font.literata, 600, false, FontVariation.Setting("opsz", 36f))),
    FontFamily(font(R.font.literata_italic, 400, true), font(R.font.literata_italic, 500, true)),
)

private fun typography(f: FontPair): Typography {
    val m = Typography()
    fun TextStyle.body(weight: Int? = null) = copy(fontFamily = f.body, fontWeight = weight?.let { FontWeight(it) } ?: fontWeight)
    fun TextStyle.display() = copy(fontFamily = f.display, fontWeight = FontWeight(600))
    return Typography(
        displayLarge = m.displayLarge.display(), displayMedium = m.displayMedium.display(), displaySmall = m.displaySmall.display(),
        headlineLarge = m.headlineLarge.display(), headlineMedium = m.headlineMedium.display(), headlineSmall = m.headlineSmall.display(),
        titleLarge = m.titleLarge.body(600), titleMedium = m.titleMedium.copy(fontFamily = f.body, fontWeight = FontWeight(600), fontSize = 17.sp, lineHeight = 24.sp),
        titleSmall = m.titleSmall.body(600),
        bodyLarge = m.bodyLarge.copy(fontFamily = f.body, fontSize = 16.sp, lineHeight = 24.sp), bodyMedium = m.bodyMedium.body(), bodySmall = m.bodySmall.body(),
        labelLarge = m.labelLarge.body(600), labelMedium = m.labelMedium.copy(fontFamily = f.body, fontWeight = FontWeight(600), letterSpacing = 0.08.em),
        labelSmall = m.labelSmall.body(),
    )
}

val LocalPalette = staticCompositionLocalOf { HarbourLight }
val LocalFonts = staticCompositionLocalOf { AtkinsonFraunces }

/**
 * A mockup's theme: the palette, the font pair and a font scale (1.5f for the 150% variant), set without
 * touching system settings. The scale defaults to the one in effect, so @Preview(fontScale = 1.5f) works too.
 */
@Composable
fun MockupTheme(palette: Palette, fonts: FontPair, fontScale: Float = LocalDensity.current.fontScale, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val scheme = if (palette.dark) {
        darkColorScheme(
            primary = palette.primary, background = palette.surface, surface = palette.surface, onSurface = palette.onSurface,
            onBackground = palette.onSurface, onSurfaceVariant = palette.muted, outline = palette.outline, outlineVariant = palette.outline,
            surfaceContainer = palette.card, surfaceContainerLow = palette.card, surfaceContainerHigh = palette.card,
        )
    } else {
        lightColorScheme(
            primary = palette.primary, background = palette.surface, surface = palette.surface, onSurface = palette.onSurface,
            onBackground = palette.onSurface, onSurfaceVariant = palette.muted, outline = palette.outline, outlineVariant = palette.outline,
            surfaceContainer = palette.card, surfaceContainerLow = palette.card, surfaceContainerHigh = palette.card,
        )
    }
    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale), LocalPalette provides palette, LocalFonts provides fonts) {
        MaterialTheme(colorScheme = scheme, typography = typography(fonts), content = content)
    }
}
