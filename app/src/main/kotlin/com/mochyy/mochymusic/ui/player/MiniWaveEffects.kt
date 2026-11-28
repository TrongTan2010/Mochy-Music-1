package com.mochyy.mochymusic.ui.player

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.zIndex
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.ui.component.AppleMusicAccent
import com.mochyy.mochymusic.ui.component.DockOrbClearance
import com.mochyy.mochymusic.ui.component.LocalDockCollapseProgress
import com.mochyy.mochymusic.ui.component.LocalDockCollapsedSearchEnabled
import com.mochyy.mochymusic.ui.component.MiniPillFrame
import com.mochyy.mochymusic.ui.component.UiCustomTheme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

private const val M3TonalWavePeriodMs = 2200L
private const val M3CoverRingPeriodMs = 5200L

/**
 * Clean Material 3 Expressive tonal wave gliding smoothly across the lower area of the Mini-Player capsule.
 * Single tonal accent palette (no neon/rainbow clutter) — clearly visible while playing and smoothly
 * flattens into a calm horizon when paused.
 */
@Composable
internal fun MiniM3TonalWave(
    progressProvider: () -> Float,
    dockShiftPx: () -> Float,
    dockEndShiftPx: () -> Float,
    fullWidthPx: Float,
    pillFrame: MiniPillFrame,
    isPlaying: Boolean,
) {
    if (!UiCustomTheme.miniWavyProgress) return
    val playerConnection = LocalPlayerConnection.current ?: return
    val visible by remember { derivedStateOf { progressProvider() < 0.22f } }
    if (!visible) return

    val effectivelyPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val playing = isPlaying || effectivelyPlaying

    val waveScale by animateFloatAsState(
        targetValue = if (playing) 1f else 0f,
        animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing),
        label = "m3TonalWaveScale",
    )

    val needsClock = playing || waveScale > 0.01f
    val clockMs = remember { mutableLongStateOf(0L) }
    LaunchedEffect(needsClock, visible) {
        if (!needsClock || !visible) return@LaunchedEffect
        clockMs.longValue = System.nanoTime() / 1_000_000L
        while (true) {
            withFrameMillis { clockMs.longValue = it }
        }
    }

    val accent = AppleMusicAccent
    val density = LocalDensity.current
    val startPx = with(density) { pillFrame.start.toPx() }
    val endPx = with(density) { pillFrame.end.toPx() }
    val heightPx = with(density) { pillFrame.height.toPx() }
    val pathFront = remember { Path() }
    val pathBack = remember { Path() }

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

        val t = (clockMs.longValue % M3TonalWavePeriodMs).toFloat() / M3TonalWavePeriodMs.toFloat()
        val phase = t * 2f * PI.toFloat()
        val ampBack = 5.5.dp.toPx() * waveScale
        val ampFront = 6.8.dp.toPx() * waveScale
        val wavelength = 46.dp.toPx()
        val steps = 44

        // Back subtle tonal echo wave (pure Material 3 tonal surface tint)
        val backBaseY = h * 0.68f
        pathBack.reset()
        for (i in 0..steps) {
            val u = i.toFloat() / steps
            val x = u * w
            val y = backBaseY + sin((x / wavelength) * 2f * PI.toFloat() - phase * 0.85f + 1.9f) * ampBack
            if (i == 0) pathBack.moveTo(x, y) else pathBack.lineTo(x, y)
        }
        pathBack.lineTo(w, h)
        pathBack.lineTo(0f, h)
        pathBack.close()
        drawPath(
            path = pathBack,
            brush = Brush.verticalGradient(
                0f to accent.copy(alpha = 0.14f + 0.08f * waveScale),
                1f to accent.copy(alpha = 0.02f),
                startY = h * 0.45f,
                endY = h,
            ),
        )

        // Front Material 3 Expressive tonal wave + clean rounded crest line
        val frontBaseY = h * 0.74f
        pathFront.reset()
        for (i in 0..steps) {
            val u = i.toFloat() / steps
            val x = u * w
            val y = frontBaseY + sin((x / wavelength) * 2f * PI.toFloat() - phase) * ampFront
            if (i == 0) pathFront.moveTo(x, y) else pathFront.lineTo(x, y)
        }
        drawPath(
            path = pathFront,
            color = accent.copy(alpha = 0.48f * waveScale),
            style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        pathFront.lineTo(w, h)
        pathFront.lineTo(0f, h)
        pathFront.close()
        drawPath(
            path = pathFront,
            brush = Brush.verticalGradient(
                0f to accent.copy(alpha = 0.22f + 0.10f * waveScale),
                1f to accent.copy(alpha = 0.04f),
                startY = h * 0.50f,
                endY = h,
            ),
        )
    }
}

/**
 * Material 3 Expressive scalloped/wavy circular ring rotating gently around the Mini-Player circular cover art
 * (inspired by Material 3 Expressive CircularWavyProgressIndicator).
 */
@Composable
internal fun MiniM3CoverWavyRing(
    progressProvider: () -> Float,
    centerProvider: () -> Offset,
    baseRadiusPx: Float,
    isPlaying: Boolean,
) {
    if (!UiCustomTheme.miniWavyProgress) return
    val playerConnection = LocalPlayerConnection.current ?: return
    val visible by remember { derivedStateOf { progressProvider() < 0.18f } }
    if (!visible) return

    val effectivelyPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val playing = isPlaying || effectivelyPlaying

    val waveScale by animateFloatAsState(
        targetValue = if (playing) 1f else 0f,
        animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
        label = "m3CoverRingScale",
    )
    if (waveScale <= 0.01f) return

    val clockMs = remember { mutableLongStateOf(0L) }
    LaunchedEffect(playing, visible) {
        if (!playing || !visible) return@LaunchedEffect
        clockMs.longValue = System.nanoTime() / 1_000_000L
        while (true) {
            withFrameMillis { clockMs.longValue = it }
        }
    }

    val accent = AppleMusicAccent
    val ringPath = remember { Path() }

    Canvas(
        modifier = Modifier
            .zIndex(1f)
            .fillMaxSize()
            .graphicsLayer {
                alpha = (1f - progressProvider() / 0.18f).coerceIn(0f, 1f) * waveScale
            }
    ) {
        val center = centerProvider()
        val rot = ((clockMs.longValue % M3CoverRingPeriodMs).toFloat() / M3CoverRingPeriodMs.toFloat()) * 2f * PI.toFloat()
        val lobes = 10
        val rBase = baseRadiusPx + 2.4.dp.toPx()
        val rAmp = 1.4.dp.toPx() * waveScale
        val segments = 72
        ringPath.reset()
        for (i in 0..segments) {
            val theta = (i.toFloat() / segments) * 2f * PI.toFloat()
            val r = rBase + sin(lobes * theta - rot) * rAmp
            val x = center.x + cos(theta) * r
            val y = center.y + sin(theta) * r
            if (i == 0) ringPath.moveTo(x, y) else ringPath.lineTo(x, y)
        }
        ringPath.close()
        drawPath(
            path = ringPath,
            color = accent.copy(alpha = 0.85f),
            style = Stroke(width = 1.9.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

/**
 * Official Material 3 Expressive [LinearWavyProgressIndicator] hugging the bottom of the Mini-Player.
 * Uses Android's genuine Material 3 Expressive wavy indicator when [UiCustomTheme.miniWavyProgress] is enabled.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
    val effectivelyPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val playing = isPlaying || effectivelyPlaying

    val fraction = if (progressState.duration > 0L) {
        (progressState.position.toFloat() / progressState.duration.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val wavy = UiCustomTheme.miniWavyProgress
    val waveAmp by animateFloatAsState(
        targetValue = if (wavy && playing) 1f else 0f,
        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
        label = "m3OfficialWaveAmp",
    )

    val accent = AppleMusicAccent
    val inactiveTrack = Color.White.copy(alpha = 0.22f)
    val dockCollapse = LocalDockCollapseProgress.current
    val showCollapsedSearch = LocalDockCollapsedSearchEnabled.current
    val density = LocalDensity.current
    val strokePx = with(density) { 2.8.dp.toPx() }
    val stroke = remember(strokePx) { Stroke(width = strokePx, cap = StrokeCap.Round) }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .zIndex(2f)
            .layout { measurable, constraints ->
                val c = dockCollapse().coerceIn(0f, 1f)
                val extraStart = if (pillFrame.merged) 0f else DockOrbClearance.toPx() * c
                val extraEnd = if (pillFrame.merged || !showCollapsedSearch) 0f else DockOrbClearance.toPx() * c
                val leftInset = ((pillFrame.start + 22.dp).toPx() + extraStart).roundToInt()
                val rightInset = ((pillFrame.end + 22.dp).toPx() + extraEnd).roundToInt()
                val barW = (constraints.maxWidth - leftInset - rightInset).coerceAtLeast(1)
                val barH = 10.dp.roundToPx()
                val placeable = measurable.measure(Constraints.fixed(barW, barH))
                val topY = (pillFrame.height.toPx() - barH - 1.dp.toPx()).roundToInt().coerceAtLeast(0)
                layout(constraints.maxWidth, pillFrame.height.roundToPx()) {
                    placeable.place(leftInset, topY)
                }
            }
            .graphicsLayer {
                alpha = (1f - progressProvider() / 0.25f).coerceIn(0f, 1f)
            }
    ) {
        if (wavy) {
            LinearWavyProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth(),
                color = accent,
                trackColor = inactiveTrack,
                stroke = stroke,
                trackStroke = stroke,
                gapSize = 4.dp,
                stopSize = 3.dp,
                amplitude = { p -> if (p > 0f) waveAmp else 0f },
                wavelength = 15.dp,
                waveSpeed = 16.dp,
            )
        } else {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val y = size.height / 2f
                drawLine(
                    color = inactiveTrack,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = strokePx,
                    cap = StrokeCap.Round,
                )
                if (fraction > 0.001f) {
                    drawLine(
                        color = accent,
                        start = Offset(0f, y),
                        end = Offset(size.width * fraction, y),
                        strokeWidth = strokePx,
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
    }
}

/** Clean 3-bar minimal equalizer indicator beside the Mini-Player song title. */
@Composable
internal fun MiniWaveEqualizerBars() {
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val clockMs = remember { mutableLongStateOf(0L) }
    LaunchedEffect(isPlaying) {
        if (!isPlaying) return@LaunchedEffect
        clockMs.longValue = System.nanoTime() / 1_000_000L
        while (true) {
            withFrameMillis { clockMs.longValue = it }
        }
    }
    val accent = AppleMusicAccent
    Canvas(modifier = Modifier.size(width = 11.dp, height = 11.dp)) {
        val barW = 2.2.dp.toPx()
        val gap = (size.width - barW * 3f) / 2f
        val p = (clockMs.longValue % 100_000L) / 1000f * 6.5f
        val heights = if (isPlaying) {
            floatArrayOf(
                0.35f + 0.62f * abs(sin(p)),
                0.30f + 0.70f * abs(sin(p * 1.25f + 1.4f)),
                0.35f + 0.58f * abs(sin(p * 0.9f + 2.7f)),
            )
        } else {
            floatArrayOf(0.28f, 0.45f, 0.28f)
        }
        for (i in 0..2) {
            val h = (size.height * heights[i]).coerceAtLeast(barW)
            val x = i * (barW + gap)
            drawRoundRect(
                color = if (isPlaying) accent else Color.White.copy(alpha = 0.45f),
                topLeft = Offset(x, size.height - h),
                size = Size(barW, h),
                cornerRadius = CornerRadius(barW / 2f, barW / 2f),
            )
        }
    }
}
