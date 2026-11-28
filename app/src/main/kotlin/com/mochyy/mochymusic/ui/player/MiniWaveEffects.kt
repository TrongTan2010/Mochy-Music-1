package com.mochyy.mochymusic.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
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
import kotlin.math.sin

private const val M3TonalWavePeriodMs = 1900L
private const val M3CoverRingPeriodMs = 4800L
private const val M3ProgressWavePeriodMs = 950L

@Stable
private class WaveClockState(initialScale: Float) {
    var clockMs by mutableLongStateOf(0L)
    var scale by mutableFloatStateOf(initialScale)
}

/**
 * Self-contained wall-clock frame driver for Material 3 wave motion.
 * Read exclusively inside Canvas draw scopes -> zero recompositions per frame, pure 60/120fps draw invalidation.
 * Never stalls when system/app animator duration scale is 0 and smoothly eases wave amplitude on play/pause.
 */
@Composable
private fun rememberWaveClock(active: Boolean, running: Boolean): WaveClockState {
    val activeState by rememberUpdatedState(active)
    val runningState by rememberUpdatedState(running)
    val state = remember { WaveClockState(if (running) 1f else 0f) }

    LaunchedEffect(Unit) {
        var lastMs = 0L
        while (true) {
            withFrameMillis { now ->
                if (activeState) {
                    val dt = if (lastMs == 0L) 0.016f else ((now - lastMs) / 1000f).coerceIn(0.001f, 0.05f)
                    val target = if (runningState) 1f else 0f
                    val cur = state.scale
                    if (abs(target - cur) > 0.002f) {
                        state.scale = cur + (target - cur) * (dt * 7.5f).coerceAtMost(1f)
                    } else if (cur != target) {
                        state.scale = target
                    }
                    if (runningState || state.scale > 0.005f) {
                        state.clockMs = now
                    }
                }
                lastMs = now
            }
        }
    }
    return state
}

/**
 * Clean Material 3 Expressive tonal wave gliding smoothly across the lower area of the Mini-Player capsule.
 * Draws directly without an extra graphicsLayer so GPU RenderNode caching never freezes frames.
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
    val waveClock = rememberWaveClock(active = visible, running = playing)

    val accent = AppleMusicAccent
    val density = LocalDensity.current
    val startPx = with(density) { pillFrame.start.toPx() }
    val endPx = with(density) { pillFrame.end.toPx() }
    val heightPx = with(density) { pillFrame.height.toPx() }
    val pathFront = remember { Path() }
    val pathBack = remember { Path() }

    Canvas(
        modifier = Modifier
            .zIndex(0.5f)
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
    ) {
        val layerAlpha = (1f - progressProvider() / 0.22f).coerceIn(0f, 1f)
        if (layerAlpha <= 0.01f) return@Canvas
        val w = size.width
        val h = size.height
        if (w <= 4f || h <= 4f) return@Canvas

        val clockMs = waveClock.clockMs
        val waveScale = waveClock.scale
        val t = (clockMs % M3TonalWavePeriodMs).toFloat() / M3TonalWavePeriodMs.toFloat()
        val phase = t * 2f * PI.toFloat()
        val liveScale = (0.18f + 0.82f * waveScale).coerceIn(0f, 1f)
        val ampBack = 5.5.dp.toPx() * liveScale
        val ampFront = 7.2.dp.toPx() * liveScale
        val wavelength = 42.dp.toPx()
        val steps = 44

        // Back subtle Material 3 tonal echo wave
        val backBaseY = h * 0.64f
        pathBack.reset()
        for (i in 0..steps) {
            val u = i.toFloat() / steps
            val x = u * w
            val y = backBaseY + sin((x / wavelength) * 2f * PI.toFloat() - phase * 0.82f + 1.9f) * ampBack
            if (i == 0) pathBack.moveTo(x, y) else pathBack.lineTo(x, y)
        }
        pathBack.lineTo(w, h)
        pathBack.lineTo(0f, h)
        pathBack.close()
        drawPath(
            path = pathBack,
            brush = Brush.verticalGradient(
                0f to accent.copy(alpha = (0.16f + 0.12f * waveScale) * layerAlpha),
                1f to accent.copy(alpha = 0.02f * layerAlpha),
                startY = h * 0.42f,
                endY = h,
            ),
        )

        // Front Material 3 Expressive tonal wave + crisp rounded crest contour
        val frontBaseY = h * 0.72f
        pathFront.reset()
        for (i in 0..steps) {
            val u = i.toFloat() / steps
            val x = u * w
            val y = frontBaseY + sin((x / wavelength) * 2f * PI.toFloat() - phase) * ampFront
            if (i == 0) pathFront.moveTo(x, y) else pathFront.lineTo(x, y)
        }
        drawPath(
            path = pathFront,
            color = accent.copy(alpha = (0.28f + 0.46f * waveScale) * layerAlpha),
            style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        pathFront.lineTo(w, h)
        pathFront.lineTo(0f, h)
        pathFront.close()
        drawPath(
            path = pathFront,
            brush = Brush.verticalGradient(
                0f to accent.copy(alpha = (0.24f + 0.14f * waveScale) * layerAlpha),
                1f to accent.copy(alpha = 0.05f * layerAlpha),
                startY = h * 0.48f,
                endY = h,
            ),
        )
    }
}

/**
 * Material 3 Expressive 10-lobe scalloped/wavy circular ring rotating smoothly around the circular cover art.
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
    val waveClock = rememberWaveClock(active = visible, running = playing)

    val accent = AppleMusicAccent
    val ringPath = remember { Path() }

    Canvas(
        modifier = Modifier
            .zIndex(1.5f)
            .fillMaxSize()
    ) {
        val waveScale = waveClock.scale
        if (waveScale <= 0.01f) return@Canvas
        val layerAlpha = (1f - progressProvider() / 0.18f).coerceIn(0f, 1f) * waveScale
        if (layerAlpha <= 0.01f) return@Canvas
        val center = centerProvider()
        val rot = ((waveClock.clockMs % M3CoverRingPeriodMs).toFloat() / M3CoverRingPeriodMs.toFloat()) * 2f * PI.toFloat()
        val lobes = 10
        val rBase = baseRadiusPx + 2.6.dp.toPx()
        val rAmp = 1.7.dp.toPx() * waveScale
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
            color = accent.copy(alpha = 0.92f * layerAlpha),
            style = Stroke(width = 2.1.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

/**
 * High-clarity Material 3 Expressive wavy progress indicator along the lower edge of the Mini-Player.
 * Drawn directly on Canvas with wall-clock frame timing so it NEVER freezes on zero animator scale
 * and remains unmistakably wavy from 1% to 100% of any song.
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
    val effectivelyPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val playing = isPlaying || effectivelyPlaying
    val wavy = UiCustomTheme.miniWavyProgress

    val fraction = if (progressState.duration > 0L) {
        (progressState.position.toFloat() / progressState.duration.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val waveClock = rememberWaveClock(active = visible && wavy, running = playing && wavy)
    val accent = AppleMusicAccent
    val dockCollapse = LocalDockCollapseProgress.current
    val showCollapsedSearch = LocalDockCollapsedSearchEnabled.current
    val wavePath = remember { Path() }

    Canvas(
        modifier = Modifier
            .zIndex(3f)
            .fillMaxWidth()
            .height(pillFrame.height)
    ) {
        val layerAlpha = (1f - progressProvider() / 0.25f).coerceIn(0f, 1f)
        if (layerAlpha <= 0.01f) return@Canvas

        val c = dockCollapse().coerceIn(0f, 1f)
        val extraStart = if (pillFrame.merged) 0f else DockOrbClearance.toPx() * c
        val extraEnd = if (pillFrame.merged || !showCollapsedSearch) 0f else DockOrbClearance.toPx() * c
        val leftInset = (pillFrame.start + 20.dp).toPx() + extraStart
        val rightInset = (pillFrame.end + 20.dp).toPx() + extraEnd
        val barWidth = (size.width - leftInset - rightInset).coerceAtLeast(0f)
        if (barWidth <= 2f) return@Canvas

        val strokeW = 2.6.dp.toPx()
        val centerY = size.height - 6.5.dp.toPx()
        val endX = leftInset + barWidth
        val gapPx = 5.0.dp.toPx()

        // Ensure at least a visible wavy stub right when playback begins
        val effectiveFraction = if (playing && fraction < 0.08f) maxOf(fraction, 0.08f) else fraction
        val thumbX = leftInset + barWidth * effectiveFraction
        val waveScale = waveClock.scale
        val phase = (waveClock.clockMs % M3ProgressWavePeriodMs).toFloat() / M3ProgressWavePeriodMs.toFloat()

        // 1. Active played segment — crisp Material 3 Expressive sine wave (3.4dp amplitude, 13dp wavelength)
        val activeEnd = (thumbX - gapPx * 0.45f).coerceAtLeast(leftInset)
        val drawableLen = (activeEnd - leftInset).coerceAtLeast(0f)
        if (drawableLen > 0.5f) {
            if (wavy && waveScale > 0.01f && drawableLen > 2.dp.toPx()) {
                val wavelength = 13.dp.toPx()
                val amplitude = 3.4.dp.toPx() * waveScale
                val step = 1.4.dp.toPx()
                wavePath.reset()
                var px = 0f
                var first = true
                while (px < drawableLen) {
                    val t = px / wavelength - phase
                    val y = centerY + sin(t * 2f * PI.toFloat()) * amplitude
                    if (first) {
                        wavePath.moveTo(leftInset + px, y)
                        first = false
                    } else {
                        wavePath.lineTo(leftInset + px, y)
                    }
                    px += step
                }
                val endT = drawableLen / wavelength - phase
                wavePath.lineTo(leftInset + drawableLen, centerY + sin(endT * 2f * PI.toFloat()) * amplitude)
                drawPath(
                    path = wavePath,
                    color = accent.copy(alpha = layerAlpha),
                    style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            } else {
                drawLine(
                    color = accent.copy(alpha = layerAlpha),
                    start = Offset(leftInset, centerY),
                    end = Offset(activeEnd, centerY),
                    strokeWidth = strokeW,
                    cap = StrokeCap.Round,
                )
            }
        }

        // 2. Inactive remaining track — flat Material 3 track after clean gap + stop dot
        val inactiveStart = (thumbX + gapPx * 0.75f).coerceAtMost(endX)
        if (endX - inactiveStart > 1.dp.toPx()) {
            drawLine(
                color = Color.White.copy(alpha = 0.26f * layerAlpha),
                start = Offset(inactiveStart, centerY),
                end = Offset(endX, centerY),
                strokeWidth = strokeW,
                cap = StrokeCap.Round,
            )
            drawCircle(
                color = accent.copy(alpha = 0.90f * layerAlpha),
                radius = strokeW * 0.56f,
                center = Offset(endX, centerY),
            )
        }

        // 3. Crisp Material 3 Expressive vertical pill thumb at playhead
        if (effectiveFraction > 0.002f && effectiveFraction < 0.998f) {
            val thumbHalfH = 4.2.dp.toPx()
            drawLine(
                color = Color.White.copy(alpha = layerAlpha),
                start = Offset(thumbX, centerY - thumbHalfH),
                end = Offset(thumbX, centerY + thumbHalfH),
                strokeWidth = 2.6.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}

/** Clean 3-bar minimal equalizer indicator beside the Mini-Player song title. */
@Composable
internal fun MiniWaveEqualizerBars() {
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val waveClock = rememberWaveClock(active = true, running = isPlaying)
    val accent = AppleMusicAccent
    Canvas(modifier = Modifier.size(width = 11.dp, height = 11.dp)) {
        val barW = 2.2.dp.toPx()
        val gap = (size.width - barW * 3f) / 2f
        val p = (waveClock.clockMs % 100_000L) / 1000f * 6.5f
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
