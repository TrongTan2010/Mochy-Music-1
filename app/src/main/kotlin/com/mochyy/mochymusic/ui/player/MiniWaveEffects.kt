package com.mochyy.mochymusic.ui.player

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
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
import kotlin.math.sin

private const val M3ProgressWavePeriodMs = 1100L

@Stable
private class WaveClockState(initialScale: Float) {
    var clockMs by mutableLongStateOf(0L)
    var scale by mutableFloatStateOf(initialScale)
}

/**
 * Self-contained wall-clock frame driver for subtle Mini-Player motion.
 * Read exclusively inside Canvas draw scopes -> zero recompositions per frame, smooth 60/120fps draw invalidation.
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
 * Sleek, low-profile hairline progress bar tucked cleanly along the flat bottom edge of the Mini-Player capsule.
 * Never collides with cover art, text, or capsule corners. When [UiCustomTheme.miniWavyProgress] is enabled,
 * draws a delicate tapered micro-wave; otherwise draws a crisp straight line.
 */
@Composable
internal fun MiniWavyProgressBar(
    progressProvider: () -> Float,
    pillFrame: MiniPillFrame,
    isPlaying: Boolean,
) {
    if (!UiCustomTheme.miniShowProgress) return
    val playerConnection = LocalPlayerConnection.current ?: return
    val visible by remember { derivedStateOf { progressProvider() < 0.22f } }
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
        val layerAlpha = (1f - progressProvider() / 0.22f).coerceIn(0f, 1f)
        if (layerAlpha <= 0.01f) return@Canvas

        val c = dockCollapse().coerceIn(0f, 1f)
        val extraStart = if (pillFrame.merged) 0f else DockOrbClearance.toPx() * c
        val extraEnd = if (pillFrame.merged || !showCollapsedSearch) 0f else DockOrbClearance.toPx() * c
        // Stay strictly inside the flat bottom chord of the 60dp capsule (30dp corner radius)
        val leftInset = (pillFrame.start + 26.dp).toPx() + extraStart
        val rightInset = (pillFrame.end + 26.dp).toPx() + extraEnd
        val barWidth = (size.width - leftInset - rightInset).coerceAtLeast(0f)
        if (barWidth <= 4f) return@Canvas

        val strokeW = 2.dp.toPx()
        val centerY = size.height - 4.2.dp.toPx()
        val endX = leftInset + barWidth
        val thumbX = leftInset + barWidth * fraction

        // 1. Subtle full-width background track
        drawLine(
            color = Color.White.copy(alpha = 0.14f * layerAlpha),
            start = Offset(leftInset, centerY),
            end = Offset(endX, centerY),
            strokeWidth = strokeW,
            cap = StrokeCap.Round,
        )

        // 2. Active played segment — crisp straight hairline or subtle tapered micro-wave
        val drawableLen = (thumbX - leftInset).coerceAtLeast(0f)
        if (drawableLen > 0.5f) {
            val waveScale = waveClock.scale
            if (wavy && waveScale > 0.01f && drawableLen > 6.dp.toPx()) {
                val wavelength = 12.dp.toPx()
                val maxAmp = 1.15.dp.toPx() * waveScale
                val taperPx = 6.dp.toPx()
                val step = 1.5.dp.toPx()
                val phase = (waveClock.clockMs % M3ProgressWavePeriodMs).toFloat() / M3ProgressWavePeriodMs.toFloat()

                wavePath.reset()
                var px = 0f
                var first = true
                while (px < drawableLen) {
                    val edgeTaper = minOf(px / taperPx, (drawableLen - px) / taperPx, 1f).coerceIn(0f, 1f)
                    val t = px / wavelength - phase
                    val y = centerY + sin(t * 2f * PI.toFloat()) * maxAmp * edgeTaper
                    if (first) {
                        wavePath.moveTo(leftInset + px, y)
                        first = false
                    } else {
                        wavePath.lineTo(leftInset + px, y)
                    }
                    px += step
                }
                wavePath.lineTo(leftInset + drawableLen, centerY)
                drawPath(
                    path = wavePath,
                    color = accent.copy(alpha = 0.92f * layerAlpha),
                    style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            } else {
                drawLine(
                    color = accent.copy(alpha = 0.92f * layerAlpha),
                    start = Offset(leftInset, centerY),
                    end = Offset(thumbX, centerY),
                    strokeWidth = strokeW,
                    cap = StrokeCap.Round,
                )
            }
        }

        // 3. Delicate round tip indicator at playhead (no oversized vertical bar)
        if (fraction in 0.005f..0.995f) {
            drawCircle(
                color = Color.White.copy(alpha = 0.94f * layerAlpha),
                radius = 1.9.dp.toPx(),
                center = Offset(thumbX, centerY),
            )
        }
    }
}

/** Clean 3-bar minimal equalizer indicator beside the Mini-Player song title (full-width pill only). */
@Composable
internal fun MiniWaveEqualizerBars() {
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val waveClock = rememberWaveClock(active = true, running = isPlaying)
    val accent = AppleMusicAccent
    Canvas(modifier = Modifier.size(width = 9.5.dp, height = 9.5.dp)) {
        val barW = 2.dp.toPx()
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
