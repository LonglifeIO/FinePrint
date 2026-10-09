package com.longlifeio.fineprint.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.longlifeio.fineprint.R

/*
 * G5's design system, in one place: palette B ("Field notes") from the design brief, the type scale,
 * spacing and corners. Screens move onto it one at a time as the owner approves them (the home
 * first); FinePrintTheme serves the others until then.
 */

/** A chip's colours: its tint, the ink for its icon, word and count, and a hairline [border] where the tint alone barely shows (null: none). */
@Immutable
data class Tone(val container: Color, val content: Color, val border: Color? = null)

/**
 * Colour rule: three semantic hues plus grey, on indicator chips only. The buckets own the hues, and
 * the tiers take theirs by the formula that links them: Flagged = Goes elsewhere (indigo), Caution =
 * Used for more (ochre), Expected = Stays here (teal), No record yet = grey with a dashed edge. Not a
 * traffic light: nothing is red. Surfaces, cards, rows, headers, the bar and links stay neutral ink,
 * and colour never works alone (every chip has an icon and a word).
 *
 * Chips are tonal: the fill is a 20% tint of the hue over the card (a dark one in dark mode), and the
 * icon and word carry the hue, darkened in light mode and lightened in dark mode. If any of a theme's
 * tints sits under 1.3:1 against the card or the page, all three chips get a hairline border in their
 * hue, so a row never mixes bordered and unbordered chips.
 *
 * PaletteContrastTest checks, in both modes: ink and muted text 4.5:1 or more on every surface; chip
 * ink 4.5:1 or more on its tint, the card and the page; the borders, all or none in a theme; neutral
 * colours everywhere but the chips; and the three chip inks at least ΔE2000 25 apart
 * for normal, protanopic and deuteranopic vision (Machado 2009 simulation), so no two collapse for a
 * red-green colour-blind reader. The light teal ink is the darkest because of that last rule: any
 * lighter and it nears the ochre for a protanopic reader.
 */
@Immutable
data class Palette(
    val dark: Boolean,
    val surface: Color,
    val ink: Color,
    val muted: Color,
    /** Neutral fill for cards and the rows inside them. */
    val card: Color,
    /** Selected filters and other raised neutral fills. */
    val raised: Color,
    /** Dividers inside cards, and decorative outlines. */
    val divider: Color,
    /** Borders that mark a control, such as the search field (3:1 or more on surface). */
    val outline: Color,
    /** The empty segments of the segmented bar. */
    val track: Color,
    /** Stays here, and Expected. */
    val stays: Tone,
    /** Used for more, and Caution. */
    val more: Tone,
    /** Goes elsewhere, and Flagged. */
    val elsewhere: Tone,
    /** No record yet: grey, drawn with a dashed edge. */
    val noRecord: Tone,
    /**
     * A hairline round every card, in a theme where the card's fill alone barely parts from the page: all cards or none,
     * as with the chips' borders (cardFill draws it). Dark mode only, the owner's call; null: none.
     */
    val cardEdge: Color? = null,
)

/** How much of its hue a chip's tint holds over the card (the owner chose 20% over 12%). */
const val CHIP_TINT = 0.20f

/** The three hues the tints are made from, in both modes. */
private val TEAL = Color(0xFF3C9187)
private val OCHRE = Color(0xFF9F690A)
private val INDIGO = Color(0xFF3753A1)

/** [fraction] of [top] over [under], as drawing one at that opacity over the other would. */
private fun over(top: Color, under: Color, fraction: Float) = Color(
    red = top.red * fraction + under.red * (1 - fraction),
    green = top.green * fraction + under.green * (1 - fraction),
    blue = top.blue * fraction + under.blue * (1 - fraction),
)

/** WCAG contrast ratio. */
internal fun contrastRatio(a: Color, b: Color): Float {
    val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
    return (hi + 0.05f) / (lo + 0.05f)
}

/** Below this against the card or the page, a tint needs its hairline border. */
internal const val FAINT_TINT = 1.3f

/** A theme's three hue chips, each a hue and its ink: tinted over the card, and all bordered if any tint is faint. */
private fun tonal(card: Color, surface: Color, vararg chips: Pair<Color, Color>): List<Tone> {
    val fills = chips.map { (hue, _) -> over(hue, card, CHIP_TINT) }
    val faint = fills.any { minOf(contrastRatio(it, card), contrastRatio(it, surface)) < FAINT_TINT }
    return chips.mapIndexed { i, (_, ink) -> Tone(fills[i], ink, border = if (faint) over(ink, fills[i], 0.5f) else null) }
}

val FieldNotesLight: Palette = run {
    val card = Color(0xFFF0EEE6)
    val surface = Color(0xFFFBFAF7)
    val (stays, more, elsewhere) = tonal(card, surface, TEAL to Color(0xFF122B28), OCHRE to Color(0xFF7D5208), INDIGO to Color(0xFF3753A1))
    Palette(
        dark = false,
        surface = surface, ink = Color(0xFF1B1C1A), muted = Color(0xFF5D5B54),
        card = card, raised = Color(0xFFE4E1D7), divider = Color(0xFFD7D3C7), outline = Color(0xFF7C786E), track = Color(0xFFCFCABD),
        stays = stays, more = more, elsewhere = elsewhere,
        noRecord = Tone(Color(0xFFEEEEEE), Color(0xFF4A4A4A)),
    )
}

val FieldNotesDark: Palette = run {
    val card = Color(0xFF1F1D1A)
    val surface = Color(0xFF141311)
    val (stays, more, elsewhere) = tonal(card, surface, TEAL to Color(0xFFB0DED9), OCHRE to Color(0xFFCC870D), INDIGO to Color(0xFF768ED0))
    val divider = Color(0xFF3B3833)
    Palette(
        dark = true,
        surface = surface, ink = Color(0xFFE7E3DA), muted = Color(0xFFADA89D),
        card = card, raised = Color(0xFF2E2B27), divider = divider, outline = Color(0xFF8C877C), track = Color(0xFF4A4640),
        stays = stays, more = more, elsewhere = elsewhere,
        noRecord = Tone(Color(0xFF262626), Color(0xFFC9C9C9)),
        // In the dark the card sits barely above the page, so each card gets the divider's tone as its edge.
        cardEdge = divider,
    )
}

/** Spacing on a 4dp grid (the brief's tokens), and the named gaps the screens use. */
object Space {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp
    val screen = 16.dp
    val card = 20.dp
    val hero = 24.dp
    val betweenCards = 16.dp
}

/** Corner radii: rounded, not pill-everything. */
object Corner {
    val chip = 8.dp
    val subCard = 16.dp
    val card = 24.dp
    val hero = 28.dp
    val sheet = 28.dp
}

private fun font(res: Int, weight: Int, italic: Boolean = false, vararg axes: FontVariation.Setting) = Font(
    resId = res,
    weight = FontWeight(weight),
    style = if (italic) FontStyle.Italic else FontStyle.Normal,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight), *axes),
)

/** Fraunces at the soft, low-contrast settings the brief asks for, at an optical size for 20-24sp. */
private val soft = arrayOf(FontVariation.Setting("SOFT", 100f), FontVariation.Setting("WONK", 0f), FontVariation.Setting("opsz", 24f))

/** Atkinson Hyperlegible Next (Braille Institute; SIL OFL 1.1): every word on screen but two kinds. */
val Atkinson = FontFamily(
    font(R.font.atkinson_next, 400), font(R.font.atkinson_next, 600), font(R.font.atkinson_next, 700),
    font(R.font.atkinson_next_italic, 400, italic = true),
)

/** Fraunces (SIL OFL 1.1): card headlines and quoted words only, never body text. */
val Fraunces = FontFamily(
    font(R.font.fraunces, 600, false, *soft),
    font(R.font.fraunces_italic, 400, true, *soft), font(R.font.fraunces_italic, 500, true, *soft),
)

/** The headline at the top of a card, in Fraunces. */
val CardHeadline = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight(600), fontSize = 22.sp, lineHeight = 28.sp)

/** Quoted words (the store description in the detail hero), in Fraunces italic. */
val Quote = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight(400), fontStyle = FontStyle.Italic, fontSize = 22.sp, lineHeight = 30.sp)

/** Material's roles, all in Atkinson; sizes in sp so the system font scale applies. */
private val FieldNotesType: Typography = Typography().let { m ->
    fun TextStyle.atkinson(weight: Int? = null) = copy(fontFamily = Atkinson, fontWeight = weight?.let(::FontWeight) ?: fontWeight)
    Typography(
        displayLarge = m.displayLarge.atkinson(600), displayMedium = m.displayMedium.atkinson(600), displaySmall = m.displaySmall.atkinson(600),
        headlineLarge = m.headlineLarge.atkinson(600), headlineMedium = m.headlineMedium.atkinson(600), headlineSmall = m.headlineSmall.atkinson(600),
        titleLarge = m.titleLarge.atkinson(600),
        titleMedium = m.titleMedium.copy(fontFamily = Atkinson, fontWeight = FontWeight(600), fontSize = 17.sp, lineHeight = 24.sp),
        titleSmall = m.titleSmall.atkinson(600),
        bodyLarge = m.bodyLarge.copy(fontFamily = Atkinson, fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = m.bodyMedium.atkinson(), bodySmall = m.bodySmall.atkinson(),
        labelLarge = m.labelLarge.atkinson(600),
        // Eyebrows ("AT A GLANCE"): 12sp, upper case in code, tracked out.
        labelMedium = m.labelMedium.copy(fontFamily = Atkinson, fontWeight = FontWeight(600), letterSpacing = 0.08.em),
        labelSmall = m.labelSmall.atkinson(),
    )
}

private fun Palette.scheme(): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = ink, onPrimary = surface, primaryContainer = raised, onPrimaryContainer = ink,
        secondary = muted, onSecondary = surface, secondaryContainer = raised, onSecondaryContainer = ink,
        tertiary = muted, onTertiary = surface, tertiaryContainer = raised, onTertiaryContainer = ink,
        background = surface, onBackground = ink, surface = surface, onSurface = ink,
        surfaceVariant = card, onSurfaceVariant = muted, surfaceTint = Color.Transparent,
        outline = outline, outlineVariant = divider,
        surfaceContainerLowest = surface, surfaceContainerLow = card, surfaceContainer = card,
        surfaceContainerHigh = raised, surfaceContainerHighest = raised,
    )
}

val LocalPalette = staticCompositionLocalOf { FieldNotesLight }

/** The Field notes theme: the palette for the system's light or dark mode, Atkinson type, and the brief's corners. */
@Composable
fun FieldNotesTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val palette = if (dark) FieldNotesDark else FieldNotesLight
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(
            colorScheme = palette.scheme(),
            typography = FieldNotesType,
            shapes = Shapes(
                small = RoundedCornerShape(Corner.chip), medium = RoundedCornerShape(Corner.subCard),
                large = RoundedCornerShape(Corner.card), extraLarge = RoundedCornerShape(Corner.sheet),
            ),
            content = content,
        )
    }
}
