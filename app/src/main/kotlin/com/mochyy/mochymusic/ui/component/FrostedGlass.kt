// FrostedGlass.kt

package com.mochyy.mochymusic.ui.component

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.dp
import com.mochyy.mochymusic.constants.ChromeGlassStyle
import com.mochyy.mochymusic.constants.ChromeGlassStyleKey
import com.mochyy.mochymusic.constants.DisableBlurKey
import com.mochyy.mochymusic.utils.rememberEnumPreference
import com.mochyy.mochymusic.utils.rememberPreference

/*
 * Apple Music iOS 18 / Liquid Glass floating chrome for the nav dock and the mini player:
 * translucent obsidian-slate glass (#16161E at 68% alpha), directional top-to-bottom specular
 * rim and inner sheen, and a deeper floating shadow beneath both capsules.
 */

/** How much of the chrome colour covers the blurred app. */
private const val FrostTintAlpha = 0.68f

/** Specular rim thickness in real pixels for Liquid Glass capsules. */
private const val ChromeOutlineWidthPx = 2.4f
private val ChromeOutlineStroke = Stroke(ChromeOutlineWidthPx)
private val ChromeOutlineBrush = Brush.verticalGradient(
    0f to Color.White.copy(alpha = 0.30f),
    0.45f to Color.White.copy(alpha = 0.09f),
    1f to Color.White.copy(alpha = 0.18f),
)

/** Inner top specular refraction sheen across the curved Liquid Glass surface. */
private val LiquidGlassSheenBrush = Brush.verticalGradient(
    0f to Color.White.copy(alpha = 0.09f),
    0.42f to Color.Transparent,
    0.85f to Color.Transparent,
    1f to Color.White.copy(alpha = 0.035f),
)

/** Lifts the floating Liquid Glass dock and mini-player capsules off the page. */
val ChromeShadow = Shadow(radius = 16.dp, color = Color.Black.copy(alpha = 0.30f), spread = 1.dp)

/** The lit tab's pill in the nav bar, and the round press behind the mini player's buttons. */
val ChromeHighlightColor = Color.White.copy(alpha = 0.13f)

/**
 * A faint grain over the glass. A dark blur only has a few shades to work with, so without it the
 * glass shows bands; this breaks them up and is too faint to see as grain.
 */
private const val FrostGrainAlpha = 0.035f
private const val FrostGrainTileSize = 64

/** A small tile of grain, half lighter and half darker pixels, repeated over the glass. Made once. */
private val FrostGrainBrush: ShaderBrush by lazy {
    val random = java.util.Random(0x6A55)
    val pixels = IntArray(FrostGrainTileSize * FrostGrainTileSize) {
        val alpha = random.nextInt(256) shl 24
        if (random.nextBoolean()) alpha or 0xFFFFFF else alpha
    }
    val tile = android.graphics.Bitmap.createBitmap(
        pixels, FrostGrainTileSize, FrostGrainTileSize, android.graphics.Bitmap.Config.ARGB_8888,
    )
    ShaderBrush(ImageShader(tile.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
}

/**
 * The frosted glass behind the nav bar and the mini player.
 *
 * The blur reads a margin of the app around the glass (see `bleed` on [GlassPillBackground]). It
 * used to see only what was right under the glass and repeat the edge row past it, so anything
 * bright touching an edge was smeared across the whole bar: streaks while scrolling, and a flash
 * of glow each time a cover or a title crossed in. The parent has to clip it to its shape.
 */
@Composable
fun FrostedChromeGlass(
    state: GlassState,
    pureBlack: Boolean,
    accentWash: (() -> Color)?,
    modifier: Modifier = Modifier,
    // which bar this is, so a look's glass shader can draw each one its own way
    surface: ChromeSurface = ChromeSurface.NavBar,
    // the lit tab's pill for a glass that draws it, given the bar's size; read while drawing
    pill: ((Size) -> ChromePill?)? = null,
    // enables gpu fallback blur on android 10-11 for module bars while keeping default chrome zero-overhead
    fallbackBlur: Boolean = false,
) {
    val moduleGlass = rememberChromeModuleGlass()
    if (moduleGlass != null) {
        if (moduleGlass.isNative && moduleGlass.shader != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ModuleChromeGlass(state, moduleGlass, pureBlack, accentWash, surface, modifier, pill)
            return
        }
        val transpiled = moduleGlass.shader as? TranspiledAgsl
        if (!moduleGlass.isNative && transpiled != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var glFailed by remember(moduleGlass) { mutableStateOf(false) }
            if (!glFailed) {
                FallbackModuleChromeGlass(
                    state = state,
                    glass = moduleGlass,
                    transpiled = transpiled,
                    pureBlack = pureBlack,
                    accentWash = accentWash,
                    surface = surface,
                    onFailed = { glFailed = true },
                    modifier = modifier,
                    pill = pill,
                )
                return
            }
        }
        moduleGlass.fallback?.let { staticFallback ->
            StaticFallbackChromeGlass(staticFallback, pureBlack, accentWash, modifier)
            return
        }
    }
    val color = if (pureBlack) Color.Black else GlassChromeColor
    // the grain only matters on the finer, high quality glass
    val grain = LocalBlurQuality.current == com.mochyy.mochymusic.constants.BlurQuality.HIGH
    Box(
        modifier = modifier.drawWithContent {
            drawContent()
            drawRect(brush = LiquidGlassSheenBrush)
            if (grain) drawRect(brush = FrostGrainBrush, alpha = FrostGrainAlpha)
        }
    ) {
        GlassPillBackground(
            state = state,
            blurRadius = { GlassChromeBlurRadius },
            tint = color.copy(alpha = UiCustomTheme.glassOpacity.coerceIn(0.32f, 0.92f)),
            foundationColor = color,
            accentWash = accentWash,
            // Only reaches the margin's outer edge now, too far out to show.
            tileMode = android.graphics.Shader.TileMode.CLAMP,
            bleed = true,
            fallbackBlur = fallbackBlur,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** the look's glass while the bars draw it, so a bar can leave out the hairline, shadow or pill the glass draws itself; null while they keep mochy's frost */
@Composable
internal fun rememberChromeModuleGlass(): com.mochyy.mochymusic.modules.skin.ActiveGlass? {
    // android 13+ runs native agsl, 10-12 runs the gles 3.0 fallback, and 7-9 uses an optional static fallback; blur off keeps plain chrome
    val (style) = rememberEnumPreference(ChromeGlassStyleKey, defaultValue = ChromeGlassStyle.FROSTED)
    val (blurOff) = rememberPreference(DisableBlurKey, defaultValue = false)
    val glass = com.mochyy.mochymusic.modules.skin.ActiveSkin.glass ?: return null
    if (style != ChromeGlassStyle.MODULE || blurOff) return null
    val liveSupported = when {
        glass.isNative -> glass.shader != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        else -> glass.shader is TranspiledAgsl && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    }
    return glass.takeIf { liveSupported || it.fallback != null }
}

/** the bars that share the chrome glass; [kind] is the number a look's glass shader gets for each */
enum class ChromeSurface(val kind: Float) { NavBar(0f), MiniPlayer(1f), WideBar(2f) }

/**
 * The chrome's specular Liquid Glass rim round a rounded rect, kept just inside it so the parent's
 * clip can't shave half of it off. [alpha] fades it, for the mini player as it opens.
 */
fun DrawScope.drawChromeOutline(
    topLeft: Offset,
    size: Size,
    cornerRadius: Float,
    alpha: Float = 1f,
) {
    val half = ChromeOutlineWidthPx / 2f
    drawRoundRect(
        brush = ChromeOutlineBrush,
        topLeft = Offset(topLeft.x + half, topLeft.y + half),
        size = Size(size.width - ChromeOutlineWidthPx, size.height - ChromeOutlineWidthPx),
        cornerRadius = CornerRadius((cornerRadius - half).coerceAtLeast(0f)),
        style = ChromeOutlineStroke,
        alpha = alpha,
    )
}
