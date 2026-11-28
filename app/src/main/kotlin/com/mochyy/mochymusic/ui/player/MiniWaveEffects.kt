package com.mochyy.mochymusic.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.ui.component.AppleMusicAccent
import com.mochyy.mochymusic.ui.component.DockOrbClearance
import com.mochyy.mochymusic.ui.component.LocalDockCollapseProgress
import com.mochyy.mochymusic.ui.component.LocalDockCollapsedSearchEnabled
import com.mochyy.mochymusic.ui.component.MiniPillFrame
import com.mochyy.mochymusic.ui.component.UiCustomTheme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ceil
import kotlin.math.sin

enum class MiniWaveStyle(val id: String, val label: String) {
    ALL("all", "Tất cả sóng"),
    LIQUID("liquid", "Sóng lỏng"),
    SPECTRUM("spectrum", "Phổ sóng"),
    NEON("neon", "Neon Twin"),
    RIPPLE("ripple", "Xung sóng"),
    ;

    companion object {
        fun fromId(id: String?): MiniWaveStyle = entries.firstOrNull { it.id == id } ?: ALL
    }
}

/** Shifts a color's RGB channels slightly to create a complementary harmonic wave color. */
private fun harmonicAccent(base: Color): Color = Color(
    red = (base.blue * 0.55f + base.red * 0.45f).coerceIn(0.15f, 1f),
    green = (base.green * 0.5f + 0.36f).coerceIn(0.20f, 1f),
    blue = (base.red * 0.45f + base.blue * 0.55f + 0.25f).coerceIn(0.35f, 1f),
    alpha = 1f,
)

private fun secondaryHarmonic(base: Color): Color = Color(
    red = (base.red * 0.65f + 0.30f).coerceIn(0.2f, 1f),
    green = (base.blue * 0.45f + 0.22f).coerceIn(0.15f, 1f),
    blue = (base.green * 0.35f + 0.60f).coerceIn(0.35f, 1f),
    alpha = 1f,
)

/**
 * Multi-layer animated wave engine rendered inside the Mini-Player glass capsule:
 * - Liquid Aurora multi-ribbon gradient sine waves
 * - Symmetrical harmonic frequency spectrum bars along the capsule floor
 * - Twin glowing neon oscilloscope wave threads
 * - Expanding horizontal sonar ripple arcs
 */
@Composable
internal fun MiniPlayerBackgroundWaves(
    progressProvider: () -> Float,
    dockShiftPx: () -> Float,
    dockEndShiftPx: () -> Float,
    fullWidthPx: Float,
    pillFrame: MiniPillFrame,
    isPlaying: Boolean,
) {
    if (!UiCustomTheme.miniShowBgWave) return
    val visible by remember { derivedStateOf { progressProvider() < 0.22f } }
    if (!visible) return

    val style = MiniWaveStyle.fromId(UiCustomTheme.miniWaveStyle)
    val intensity = UiCustomTheme.miniWaveIntensity.coerceIn(0.35f, 1.6f)
    val accent = AppleMusicAccent
    val harmonic = remember(accent) { harmonicAccent(accent) }
    val harmonic2 = remember(accent) { secondaryHarmonic(accent) }

    val energy by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.14f,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "miniBgWaveEnergy",
    )

    val phase = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isPlaying, visible) {
        if (!visible) return@LaunchedEffect
        var lastNs = 0L
        while (true) {
            withFrameNanos { now ->
                if (lastNs != 0L) {
                    val dt = ((now - lastNs) / 1_000_000_000f).coerceAtMost(0.05f)
                    val speed = if (isPlaying) 3.4f else 0.55f
                    phase.floatValue = (phase.floatValue + dt * speed) % (PI.toFloat() * 200f)
                }
                lastNs = now
            }
        }
    }

    val density = LocalDensity.current
    val startPx = with(density) { pillFrame.start.toPx() }
    val endPx = with(density) { pillFrame.end.toPx() }
    val heightPx = with(density) { pillFrame.height.toPx() }

    val path1 = remember { Path() }
    val path2 = remember { Path() }
    val path3 = remember { Path() }

    Canvas(
        modifier = Modifier
            .layout { measurable, constraints ->
                val p = progressProvider()
                val left = floor((startPx + dockShiftPx()) * (1f - p)).toInt()
                val right = ceil(fullWidthPx - (endPx + dockEndShiftPx()) * (1f - p)).toInt()
                val w = (right - left).coerceAtLeast(1)
                val h = ceil(heightPx).toInt().coerceAtLeast(1)
                val placeable = measurable.measure(Constraints.fixed(w, h))
                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeable.place(left, 0)
                }
            }
            .graphicsLayer {
                alpha = (1f - progressProvider() / 0.22f).coerceIn(0f, 1f)
            }
    ) {
        val w = size.width
        val h = size.height
        if (w <= 4f || h <= 4f) return@Canvas
        val t = phase.floatValue
        val ampScale = energy * intensity

        // 1. Liquid Aurora Ribbons (shown in ALL and LIQUID)
        if (style == MiniWaveStyle.ALL || style == MiniWaveStyle.LIQUID) {
            val baseA = if (style == MiniWaveStyle.LIQUID) 0.34f else 0.22f
            drawLiquidRibbon(
                path = path1,
                w = w,
                h = h,
                baseY = h * 0.62f,
                amplitude = h * 0.24f * ampScale,
                freq = 2.1f,
                phase = t * 0.92f,
                harmonicPhase = t * 1.45f,
                brush = Brush.verticalGradient(
                    0f to accent.copy(alpha = (baseA * intensity).coerceIn(0f, 0.55f)),
                    1f to accent.copy(alpha = 0.02f),
                    startY = h * 0.22f,
                    endY = h,
                ),
                crestColor = Color.White.copy(alpha = (0.28f * energy * intensity).coerceIn(0f, 0.5f)),
                crestWidth = 1.2.dp.toPx(),
            )
            drawLiquidRibbon(
                path = path2,
                w = w,
                h = h,
                baseY = h * 0.70f,
                amplitude = h * 0.20f * ampScale,
                freq = 3.0f,
                phase = -t * 1.15f + 1.8f,
                harmonicPhase = t * 0.8f + 0.9f,
                brush = Brush.verticalGradient(
                    0f to harmonic.copy(alpha = (baseA * 0.85f * intensity).coerceIn(0f, 0.48f)),
                    1f to harmonic.copy(alpha = 0.01f),
                    startY = h * 0.30f,
                    endY = h,
                ),
                crestColor = harmonic.copy(alpha = (0.35f * energy * intensity).coerceIn(0f, 0.6f)),
                crestWidth = 1.0.dp.toPx(),
            )
            if (style == MiniWaveStyle.LIQUID) {
                drawLiquidRibbon(
                    path = path3,
                    w = w,
                    h = h,
                    baseY = h * 0.78f,
                    amplitude = h * 0.16f * ampScale,
                    freq = 4.2f,
                    phase = t * 1.55f + 3.2f,
                    harmonicPhase = -t * 1.1f,
                    brush = Brush.verticalGradient(
                        0f to harmonic2.copy(alpha = (0.24f * intensity).coerceIn(0f, 0.42f)),
                        1f to Color.Transparent,
                        startY = h * 0.45f,
                        endY = h,
                    ),
                    crestColor = Color.White.copy(alpha = (0.22f * energy).coerceIn(0f, 0.4f)),
                    crestWidth = 0.9.dp.toPx(),
                )
            }
        }

        // 2. Symmetrical Harmonic Spectrum Bars (shown in ALL and SPECTRUM)
        if (style == MiniWaveStyle.ALL || style == MiniWaveStyle.SPECTRUM) {
            val barCount = if (style == MiniWaveStyle.SPECTRUM) 30 else 22
            val horizontalPad = 14.dp.toPx()
            val usableW = (w - horizontalPad * 2f).coerceAtLeast(10f)
            val step = usableW / barCount
            val barW = (step * 0.46f).coerceIn(1.5.dp.toPx(), 3.2.dp.toPx())
            val maxBarH = h * (if (style == MiniWaveStyle.SPECTRUM) 0.62f else 0.36f) * ampScale.coerceAtMost(1.35f)
            val barAlpha = (if (style == MiniWaveStyle.SPECTRUM) 0.50f else 0.28f) * intensity.coerceAtMost(1.3f)

            for (i in 0 until barCount) {
                val u = i.toFloat() / (barCount - 1).coerceAtLeast(1)
                // Bell envelope so wave bars taper gracefully at the pill ends
                val env = sin(u * PI.toFloat()).coerceAtLeast(0.08f)
                val w1 = sin(t * 1.9f + u * 9.5f)
                val w2 = cos(t * 2.7f - u * 14.0f + 1.1f)
                val w3 = sin(t * 1.1f + u * 5.0f + 2.4f)
                val mag = (0.24f + 0.76f * abs(w1 * 0.52f + w2 * 0.30f + w3 * 0.18f)) * env
                val bh = (maxBarH * mag).coerceAtLeast(barW)
                val x = horizontalPad + i * step + (step - barW) / 2f
                val barColor = if (i % 3 == 0) harmonic else accent

                drawRoundRect(
                    brush = Brush.verticalGradient(
                        0f to Color.White.copy(alpha = (barAlpha * 1.1f).coerceIn(0f, 0.75f)),
                        0.35f to barColor.copy(alpha = barAlpha.coerceIn(0f, 0.68f)),
                        1f to barColor.copy(alpha = 0.04f),
                        startY = h - bh,
                        endY = h,
                    ),
                    topLeft = Offset(x, h - bh),
                    size = Size(barW, bh),
                    cornerRadius = CornerRadius(barW / 2f, barW / 2f),
                )
                if (style == MiniWaveStyle.SPECTRUM && energy > 0.3f) {
                    // Floating peak sparkle dot above each bar
                    drawCircle(
                        color = Color.White.copy(alpha = (0.45f * env * energy).coerceIn(0f, 0.65f)),
                        radius = barW * 0.45f,
                        center = Offset(x + barW / 2f, (h - bh - 3.dp.toPx()).coerceAtLeast(4.dp.toPx())),
                    )
                }
            }
        }

        // 3. Twin Glowing Neon Oscilloscope Sine Waves (shown in ALL and NEON)
        if (style == MiniWaveStyle.ALL || style == MiniWaveStyle.NEON) {
            val strokeBoost = if (style == MiniWaveStyle.NEON) 1.35f else 0.85f
            drawOscilloscopeWave(
                path = path1,
                w = w,
                centerY = h * 0.50f,
                amplitude = h * 0.26f * ampScale,
                cycles = 2.6f,
                phase = t * 1.65f,
                strokeWidth = (1.6f * strokeBoost).dp.toPx(),
                glowWidth = (5.0f * strokeBoost).dp.toPx(),
                color = accent,
                alpha = (0.58f * energy * intensity).coerceIn(0f, 0.85f),
            )
            drawOscilloscopeWave(
                path = path2,
                w = w,
                centerY = h * 0.52f,
                amplitude = h * 0.21f * ampScale,
                cycles = 3.5f,
                phase = -t * 2.05f + 1.3f,
                strokeWidth = (1.2f * strokeBoost).dp.toPx(),
                glowWidth = (4.0f * strokeBoost).dp.toPx(),
                color = harmonic,
                alpha = (0.48f * energy * intensity).coerceIn(0f, 0.78f),
            )
            if (style == MiniWaveStyle.NEON) {
                drawOscilloscopeWave(
                    path = path3,
                    w = w,
                    centerY = h * 0.50f,
                    amplitude = h * 0.14f * ampScale,
                    cycles = 5.0f,
                    phase = t * 2.6f + 2.5f,
                    strokeWidth = 1.0.dp.toPx(),
                    glowWidth = 3.0.dp.toPx(),
                    color = Color.White,
                    alpha = (0.42f * energy * intensity).coerceIn(0f, 0.72f),
                )
            }
        }

        // 4. Horizontal Sonar Ripple Arcs emanating from the cover side (shown in ALL and RIPPLE)
        if (style == MiniWaveStyle.ALL || style == MiniWaveStyle.RIPPLE) {
            val origin = Offset(30.dp.toPx(), h * 0.5f)
            val ringCount = if (style == MiniWaveStyle.RIPPLE) 5 else 3
            val maxR = w * 0.92f
            val baseAlpha = (if (style == MiniWaveStyle.RIPPLE) 0.42f else 0.20f) * energy * intensity
            for (k in 0 until ringCount) {
                val p = ((t * 0.22f + k.toFloat() / ringCount) % 1f)
                val r = 24.dp.toPx() + p * maxR
                val fade = (1f - p) * (1f - p) * sin(p.coerceAtMost(0.15f) / 0.15f * (PI.toFloat() / 2f))
                val c = if (k % 2 == 0) accent else harmonic
                drawCircle(
                    color = c.copy(alpha = (baseAlpha * fade).coerceIn(0f, 0.55f)),
                    radius = r,
                    center = origin,
                    style = Stroke(width = (1.8f - 0.9f * p).dp.toPx()),
                )
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLiquidRibbon(
    path: Path,
    w: Float,
    h: Float,
    baseY: Float,
    amplitude: Float,
    freq: Float,
    phase: Float,
    harmonicPhase: Float,
    brush: Brush,
    crestColor: Color,
    crestWidth: Float,
) {
    val steps = 42
    path.reset()
    for (i in 0..steps) {
        val u = i.toFloat() / steps
        val x = u * w
        val env = 0.35f + 0.65f * sin(u * PI.toFloat())
        val y = baseY +
            sin(u * PI.toFloat() * 2f * freq + phase) * amplitude * env +
            sin(u * PI.toFloat() * 4f * freq * 0.55f + harmonicPhase) * amplitude * 0.35f * env
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    // Stroke the luminous crest line first before closing for fill
    drawPath(
        path = path,
        color = crestColor,
        style = Stroke(width = crestWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
    path.lineTo(w, h)
    path.lineTo(0f, h)
    path.close()
    drawPath(path = path, brush = brush)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawOscilloscopeWave(
    path: Path,
    w: Float,
    centerY: Float,
    amplitude: Float,
    cycles: Float,
    phase: Float,
    strokeWidth: Float,
    glowWidth: Float,
    color: Color,
    alpha: Float,
) {
    if (alpha <= 0.005f) return
    val steps = 48
    path.reset()
    for (i in 0..steps) {
        val u = i.toFloat() / steps
        val x = u * w
        val env = sin(u * PI.toFloat())
        val envSq = env * env
        val y = centerY + sin(u * PI.toFloat() * 2f * cycles + phase) * amplitude * envSq
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(
        path = path,
        color = color.copy(alpha = (alpha * 0.28f).coerceIn(0f, 0.38f)),
        style = Stroke(width = glowWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
    drawPath(
        path = path,
        brush = Brush.horizontalGradient(
            0f to Color.Transparent,
            0.18f to color.copy(alpha = alpha),
            0.50f to Color.White.copy(alpha = (alpha * 0.95f).coerceIn(0f, 0.9f)),
            0.82f to color.copy(alpha = alpha),
            1f to Color.Transparent,
        ),
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

/**
 * Expanding concentric sonic wave rings radiating behind the circular spinning Mini-Player cover art.
 */
@Composable
internal fun MiniCoverSonicRipples(
    progressProvider: () -> Float,
    centerProvider: () -> Offset,
    baseRadiusPx: Float,
    isPlaying: Boolean,
) {
    if (!UiCustomTheme.miniShowCoverWave) return
    val visible by remember { derivedStateOf { progressProvider() < 0.18f } }
    if (!visible) return

    val accent = AppleMusicAccent
    val harmonic = remember(accent) { harmonicAccent(accent) }
    val intensity = UiCustomTheme.miniWaveIntensity.coerceIn(0.35f, 1.6f)

    val energy by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = tween(350),
        label = "coverWaveEnergy",
    )
    if (energy <= 0.01f) return

    val phase = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isPlaying, visible) {
        if (!isPlaying || !visible) return@LaunchedEffect
        var lastNs = 0L
        while (true) {
            withFrameNanos { now ->
                if (lastNs != 0L) {
                    val dt = ((now - lastNs) / 1_000_000_000f).coerceAtMost(0.05f)
                    phase.floatValue = (phase.floatValue + dt * 0.65f) % 100f
                }
                lastNs = now
            }
        }
    }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = (1f - progressProvider() / 0.18f).coerceIn(0f, 1f) * energy
            }
    ) {
        val center = centerProvider()
        val t = phase.floatValue

        // Soft pulsing aura behind cover
        val pulse = 0.5f + 0.5f * sin(t * PI.toFloat() * 4f)
        val auraR = baseRadiusPx * (1.28f + 0.18f * pulse)
        drawCircle(
            brush = Brush.radialGradient(
                0f to accent.copy(alpha = (0.38f * intensity).coerceIn(0f, 0.55f)),
                0.65f to accent.copy(alpha = (0.14f * intensity).coerceIn(0f, 0.25f)),
                1f to Color.Transparent,
                center = center,
                radius = auraR,
            ),
            radius = auraR,
            center = center,
        )

        // 3 expanding concentric sonic wave rings
        for (i in 0..2) {
            val p = (t + i / 3f) % 1f
            val r = baseRadiusPx * (0.96f + p * 0.72f)
            val fade = (1f - p) * (1f - p)
            val ringColor = if (i == 1) harmonic else accent
            drawCircle(
                color = ringColor.copy(alpha = (0.62f * fade * intensity).coerceIn(0f, 0.80f)),
                radius = r,
                center = center,
                style = Stroke(width = (1.8f - 0.9f * p).dp.toPx()),
            )
        }
    }
}

/**
 * Animated squiggly wavy progress bar hugging the bottom of the Mini-Player capsule,
 * smoothly flattening when paused and topped with a glowing radial playhead spark.
 */
@Composable
internal fun MiniWavyProgressBar(
    progressProvider: () -> Float,
    pillFrame: MiniPillFrame,
    isPlaying: Boolean,
) {
    if (!UiCustomTheme.miniShowProgress) return
    val playerConnection = LocalPlayerConnection.current ?: return
    val visible by remember { derivedStateOf { progressProvider() < 0.25f } }
    if (!visible) return

    val progressState by playerConnection.uiState.progressState.collectAsState()
    val fraction = if (progressState.duration > 0L) {
        (progressState.position.toFloat() / progressState.duration.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val wavy = UiCustomTheme.miniWavyProgress
    val intensity = UiCustomTheme.miniWaveIntensity.coerceIn(0.4f, 1.6f)
    val waveAmpAnim by animateFloatAsState(
        targetValue = if (wavy && isPlaying) 1f else 0f,
        animationSpec = tween(320, easing = FastOutSlowInEasing),
        label = "miniWavyAmp",
    )

    val wavePhase = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(wavy, isPlaying, visible) {
        if (!wavy || !isPlaying || !visible) return@LaunchedEffect
        var lastNs = 0L
        while (true) {
            withFrameNanos { now ->
                if (lastNs != 0L) {
                    val dt = ((now - lastNs) / 1_000_000_000f).coerceAtMost(0.05f)
                    wavePhase.floatValue = (wavePhase.floatValue + dt * 7.5f) % (PI.toFloat() * 200f)
                }
                lastNs = now
            }
        }
    }

    val accent = AppleMusicAccent
    val dockCollapse = LocalDockCollapseProgress.current
    val showCollapsedSearch = LocalDockCollapsedSearchEnabled.current
    val wavePath = remember { Path() }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(pillFrame.height)
            .graphicsLayer {
                alpha = (1f - progressProvider() / 0.25f).coerceIn(0f, 1f)
            }
    ) {
        val c = dockCollapse().coerceIn(0f, 1f)
        val extraStart = if (pillFrame.merged) 0f else DockOrbClearance.toPx() * c
        val extraEnd = if (pillFrame.merged || !showCollapsedSearch) 0f else DockOrbClearance.toPx() * c
        val leftInset = (pillFrame.start + 28.dp).toPx() + extraStart
        val rightInset = (pillFrame.end + 28.dp).toPx() + extraEnd
        val barWidth = (size.width - leftInset - rightInset).coerceAtLeast(0f)
        if (barWidth <= 0f) return@Canvas

        val strokeH = 2.4.dp.toPx()
        val centerY = size.height - 4.2.dp.toPx()
        val cr = CornerRadius(strokeH / 2f, strokeH / 2f)

        // Subtle track line
        drawRoundRect(
            color = Color.White.copy(alpha = 0.14f),
            topLeft = Offset(leftInset, centerY - strokeH / 2f),
            size = Size(barWidth, strokeH),
            cornerRadius = cr,
        )

        if (fraction > 0.001f) {
            val fillW = barWidth * fraction
            val endX = leftInset + fillW
            val activeBrush = Brush.horizontalGradient(
                0f to accent.copy(alpha = 0.78f),
                0.80f to accent,
                1f to Color.White.copy(alpha = 0.96f),
                startX = leftInset,
                endX = endX,
            )

            var tipY = centerY
            if (waveAmpAnim > 0.02f && fillW > 4.dp.toPx()) {
                val amp = 1.85.dp.toPx() * waveAmpAnim * intensity.coerceAtMost(1.35f)
                val wavelength = 13.dp.toPx()
                val p = wavePhase.floatValue
                val samples = ((fillW / 2.2.dp.toPx()).toInt()).coerceIn(6, 96)
                wavePath.reset()
                for (i in 0..samples) {
                    val u = i.toFloat() / samples
                    val x = leftInset + u * fillW
                    // Gentle taper at start & right at the playhead
                    val edgeTaper = (minOf(u * 8f, (1f - u) * 10f, 1f)).coerceIn(0f, 1f)
                    val y = centerY + sin((x - leftInset) / wavelength * (PI.toFloat() * 2f) - p) * amp * edgeTaper
                    if (i == 0) wavePath.moveTo(x, y) else wavePath.lineTo(x, y)
                    if (i == samples) tipY = y
                }
                // Soft wave bloom underneath
                drawPath(
                    path = wavePath,
                    color = accent.copy(alpha = 0.32f * waveAmpAnim),
                    style = Stroke(width = strokeH * 2.4f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
                drawPath(
                    path = wavePath,
                    brush = activeBrush,
                    style = Stroke(width = strokeH, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            } else {
                drawRoundRect(
                    brush = activeBrush,
                    topLeft = Offset(leftInset, centerY - strokeH / 2f),
                    size = Size(fillW, strokeH),
                    cornerRadius = cr,
                )
            }

            // Luminous playhead spark riding the wave tip
            val tipCenter = Offset(endX, tipY)
            val glowR = 6.dp.toPx()
            drawCircle(
                brush = Brush.radialGradient(
                    0f to Color.White.copy(alpha = 0.92f),
                    0.42f to accent.copy(alpha = 0.56f),
                    1f to Color.Transparent,
                    center = tipCenter,
                    radius = glowR,
                ),
                radius = glowR,
                center = tipCenter,
            )
        }
    }
}

/** 5-bar traveling-wave equalizer indicator beside the Mini-Player song title. */
@Composable
internal fun MiniWaveEqualizerBars() {
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val phase = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isPlaying) {
        if (!isPlaying) return@LaunchedEffect
        var lastNs = 0L
        while (true) {
            withFrameNanos { now ->
                if (lastNs != 0L) {
                    val dt = ((now - lastNs) / 1_000_000_000f).coerceAtMost(0.05f)
                    phase.floatValue = (phase.floatValue + dt * 7.2f) % (PI.toFloat() * 200f)
                }
                lastNs = now
            }
        }
    }
    val accent = AppleMusicAccent
    val harmonic = remember(accent) { harmonicAccent(accent) }
    Canvas(modifier = Modifier.size(width = 16.dp, height = 12.dp)) {
        val count = 5
        val barW = 2.0.dp.toPx()
        val gap = (size.width - barW * count) / (count - 1)
        val p = phase.floatValue
        for (i in 0 until count) {
            val factor = if (isPlaying) {
                val wave = sin(p - i * 0.95f) * 0.55f + sin(p * 1.45f + i * 1.3f) * 0.45f
                0.26f + 0.72f * abs(wave)
            } else {
                when (i) {
                    0, 4 -> 0.24f
                    1, 3 -> 0.38f
                    else -> 0.52f
                }
            }
            val h = (size.height * factor).coerceAtLeast(barW)
            val x = i * (barW + gap)
            val y = (size.height - h) / 2f
            drawRoundRect(
                color = if (isPlaying) (if (i == 2) Color.White else if (i % 2 == 0) accent else harmonic) else Color.White.copy(alpha = 0.42f),
                topLeft = Offset(x, y),
                size = Size(barW, h),
                cornerRadius = CornerRadius(barW / 2f, barW / 2f),
            )
        }
    }
}

/** Expanding shockwave ring when a Mini-Player button is tapped. */
@Composable
internal fun MiniButtonWaveOverlay(
    triggerCount: Int,
    color: Color = AppleMusicAccent,
    modifier: Modifier = Modifier,
) {
    val progress = remember { Animatable(1f) }
    LaunchedEffect(triggerCount) {
        if (triggerCount <= 0) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis = 380, easing = FastOutSlowInEasing))
    }
    if (progress.value >= 0.99f) return
    Canvas(modifier = modifier.fillMaxSize()) {
        val p = progress.value
        val maxR = minOf(size.width, size.height) * 0.62f
        val r = maxR * (0.28f + 0.72f * p)
        val alpha = (1f - p) * (1f - p)
        drawCircle(
            color = color.copy(alpha = 0.55f * alpha),
            radius = r,
            style = Stroke(width = (2.4f - 1.6f * p).dp.toPx()),
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.35f * alpha),
            radius = r * 0.76f,
            style = Stroke(width = 1.0.dp.toPx()),
        )
    }
}

/**
 * Large, silky multi-layer Aurora Audio Waves flowing across the bottom of the full-screen player.
 * Works seamlessly on Flagship, Apple Music, Liquid Glass, Vinyl CD, and Minimal Dark styles.
 */
@Composable
fun FullPlayerAuroraWaves(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    preview: Boolean = false,
) {
    if (!UiCustomTheme.playerShowWave) return
    val accent = AppleMusicAccent
    val harmonic = remember(accent) { harmonicAccent(accent) }
    val harmonic2 = remember(accent) { secondaryHarmonic(accent) }
    val intensity = UiCustomTheme.miniWaveIntensity.coerceIn(0.35f, 1.6f)

    val energy by animateFloatAsState(
        targetValue = if (isPlaying || preview) 1f else 0.16f,
        animationSpec = tween(480, easing = FastOutSlowInEasing),
        label = "fullPlayerWaveEnergy",
    )

    val phase = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isPlaying, preview) {
        if (preview) return@LaunchedEffect
        var lastNs = 0L
        while (true) {
            withFrameNanos { now ->
                if (lastNs != 0L) {
                    val dt = ((now - lastNs) / 1_000_000_000f).coerceAtMost(0.05f)
                    val speed = if (isPlaying) 2.2f else 0.42f
                    phase.floatValue = (phase.floatValue + dt * speed) % (PI.toFloat() * 200f)
                }
                lastNs = now
            }
        }
    }

    val p1 = remember { Path() }
    val p2 = remember { Path() }
    val p3 = remember { Path() }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        if (w <= 4f || h <= 4f) return@Canvas
        val t = phase.floatValue
        val bandTop = h * 0.66f
        val bandH = h - bandTop
        val amp = bandH * 0.24f * energy * intensity

        drawLiquidRibbon(
            path = p1,
            w = w,
            h = h,
            baseY = h * 0.80f,
            amplitude = amp,
            freq = 1.4f,
            phase = t * 0.82f,
            harmonicPhase = t * 1.3f,
            brush = Brush.verticalGradient(
                0f to accent.copy(alpha = (0.18f * intensity).coerceIn(0f, 0.34f)),
                1f to accent.copy(alpha = 0.02f),
                startY = bandTop,
                endY = h,
            ),
            crestColor = Color.White.copy(alpha = (0.18f * energy * intensity).coerceIn(0f, 0.35f)),
            crestWidth = 1.4.dp.toPx(),
        )

        drawLiquidRibbon(
            path = p2,
            w = w,
            h = h,
            baseY = h * 0.85f,
            amplitude = amp * 0.86f,
            freq = 2.0f,
            phase = -t * 1.05f + 2.1f,
            harmonicPhase = t * 0.75f + 0.8f,
            brush = Brush.verticalGradient(
                0f to harmonic.copy(alpha = (0.16f * intensity).coerceIn(0f, 0.30f)),
                1f to Color.Transparent,
                startY = bandTop + bandH * 0.15f,
                endY = h,
            ),
            crestColor = harmonic.copy(alpha = (0.28f * energy * intensity).coerceIn(0f, 0.48f)),
            crestWidth = 1.2.dp.toPx(),
        )

        drawLiquidRibbon(
            path = p3,
            w = w,
            h = h,
            baseY = h * 0.90f,
            amplitude = amp * 0.65f,
            freq = 2.8f,
            phase = t * 1.42f + 4.0f,
            harmonicPhase = -t * 0.95f,
            brush = Brush.verticalGradient(
                0f to harmonic2.copy(alpha = (0.14f * intensity).coerceIn(0f, 0.26f)),
                1f to Color.Transparent,
                startY = bandTop + bandH * 0.32f,
                endY = h,
            ),
            crestColor = Color.White.copy(alpha = (0.22f * energy * intensity).coerceIn(0f, 0.42f)),
            crestWidth = 1.0.dp.toPx(),
        )
    }
}
