// Theme.kt

package com.mochyy.mochymusic.ui.theme

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicColorScheme
import com.materialkolor.score.Score

val DefaultThemeColor = Color(0xFF8E8E8E)

/**
 * mochy renders dark-only.
 *
 * The light palette was never finished - large parts of the UI hardcode white text and dark
 * glass - so on a light-mode phone the app looked broken rather than light. Pinned here as one
 * switch instead of being decided independently at every call site.
 */
const val ForceDarkTheme = true

@Composable
fun MochyTheme(
    darkTheme: Boolean = ForceDarkTheme,
    pureBlack: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current

    // Dynamic (Material You) colour is deliberately not used - the app ships its own palette.
    // The branch is gone rather than dead-but-present: dynamic*ColorScheme is API 31.
    val baseColorScheme = run {

        if (darkTheme) {
            darkColorScheme(
                primary = Color(0xFFE0E0E0),
                onPrimary = Color.Black,
                primaryContainer = Color(0xFF333333),
                onPrimaryContainer = Color.White,
                secondary = Color(0xFFB0B0B0),
                onSecondary = Color.Black,
                secondaryContainer = Color(0xFF2C2C2C),
                onSecondaryContainer = Color.White,
                background = Color.Black,
                onBackground = Color.White,
                surface = Color.Black,
                onSurface = Color.White,
                surfaceVariant = Color(0xFF1A1A1A),
                onSurfaceVariant = Color.White,
                surfaceTint = Color.Black
            )
        } else {
            lightColorScheme(
                primary = Color.Black,
                onPrimary = Color.White,
                primaryContainer = Color(0xFFE0E0E0),
                onPrimaryContainer = Color.Black,
                secondary = Color(0xFF333333),
                onSecondary = Color.White,
                secondaryContainer = Color(0xFFCCCCCC),
                onSecondaryContainer = Color.Black,
                background = Color.White,
                onBackground = Color.Black,
                surface = Color.White,
                onSurface = Color.Black,
                surfaceVariant = Color(0xFFF0F0F0),
                onSurfaceVariant = Color.Black,
                surfaceTint = Color.White
            )
        }
    }

    val colorScheme = remember(baseColorScheme, pureBlack, darkTheme) {
        if (darkTheme && pureBlack) {
            baseColorScheme.pureBlack(true)
        } else {
            baseColorScheme
        }
    }

    val resolvedFonts = AppFonts.resolvedFonts()
    val typography = remember(resolvedFonts) { appTypography(resolvedFonts) }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
    ) {
        ProvideTextStyle(
            value = typography.bodyLarge,
            content = content
        )
    }
}

fun Bitmap.extractThemeColor(): Color {
    val colorsToPopulation = Palette.from(this)
        .maximumColorCount(8)
        .generate()
        .swatches
        .associate { it.rgb to it.population }
    val rankedColors = Score.score(colorsToPopulation)
    return Color(rankedColors.first())
}

/**
 * Harmonious pair of colors extracted from a playing song's cover art:
 * - [accent]: vivid, readable highlight for active dock icons, progress bar, and buttons on dark surfaces.
 * - [dockTint]: deep atmospheric glass tint for the bottom Dock, Home/Search orbs, and Mini-Player capsule.
 */
data class SongArtworkColors(
    val accent: Color,
    val dockTint: Color,
)

object SongColorCache {
    private val cache = android.util.LruCache<String, SongArtworkColors>(64)

    fun get(url: String?): SongArtworkColors? = url?.let { cache.get(it) }

    fun put(url: String, colors: SongArtworkColors) {
        cache.put(url, colors)
    }
}

fun Bitmap.extractSongColors(): SongArtworkColors {
    val palette = Palette.from(this)
        .maximumColorCount(16)
        .generate()
    val swatches = palette.swatches.associate { it.rgb to it.population }
    val scored = if (swatches.isNotEmpty()) Score.score(swatches).firstOrNull() else null

    val rawAccent = palette.vibrantSwatch?.rgb
        ?: palette.lightVibrantSwatch?.rgb
        ?: scored
        ?: palette.dominantSwatch?.rgb
        ?: 0xFFFA2D48.toInt()

    val rawDock = palette.darkVibrantSwatch?.rgb
        ?: palette.vibrantSwatch?.rgb
        ?: palette.dominantSwatch?.rgb
        ?: rawAccent

    val accentHsl = FloatArray(3)
    androidx.core.graphics.ColorUtils.colorToHSL(rawAccent, accentHsl)
    if (accentHsl[1] < 0.08f) {
        // Monochrome cover: crisp silver-white accent instead of dull grey
        accentHsl[1] = 0.04f
        accentHsl[2] = 0.86f
    } else {
        accentHsl[1] = accentHsl[1].coerceIn(0.56f, 0.92f)
        accentHsl[2] = accentHsl[2].coerceIn(0.56f, 0.72f)
    }
    val vividAccent = Color(androidx.core.graphics.ColorUtils.HSLToColor(accentHsl))

    val dockHsl = FloatArray(3)
    androidx.core.graphics.ColorUtils.colorToHSL(rawDock, dockHsl)
    if (dockHsl[1] < 0.08f) {
        dockHsl[1] = 0.06f
        dockHsl[2] = 0.22f
    } else {
        // Keep dock hue harmonious with the artwork, rich enough to tint frosted glass clearly without washing out white text
        dockHsl[1] = (dockHsl[1] * 0.95f).coerceIn(0.45f, 0.80f)
        dockHsl[2] = dockHsl[2].coerceIn(0.20f, 0.36f)
    }
    val richDockTint = Color(androidx.core.graphics.ColorUtils.HSLToColor(dockHsl))

    return SongArtworkColors(accent = vividAccent, dockTint = richDockTint)
}

fun extractSongColorsCompat(bitmap: Bitmap): SongArtworkColors = bitmap.extractSongColors()

fun Bitmap.extractGradientColors(): List<Color> {
    val extractedColors = Palette.from(this)
        .maximumColorCount(64)
        .generate()
        .swatches
        .associate { it.rgb to it.population }

    val orderedColors = Score.score(extractedColors, 2, 0xff4285f4.toInt(), true)
        .sortedByDescending { Color(it).luminance() }

    return if (orderedColors.size >= 2)
        listOf(Color(orderedColors[0]), Color(orderedColors[1]))
    else
        listOf(Color(0xFF595959), Color(0xFF0D0D0D))
}

fun ColorScheme.pureBlack(apply: Boolean) =
    if (apply) copy(
        surface = Color.Black,
        background = Color.Black
    ) else this

val ColorSaver = object : Saver<Color, Int> {
    override fun restore(value: Int): Color = Color(value)
    override fun SaverScope.save(value: Color): Int = value.toArgb()
}
