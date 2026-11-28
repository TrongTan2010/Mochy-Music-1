package com.mochyy.mochymusic.ui.player

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.graphics.graphicsLayer
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

private const val MiniWavePeriodMs = 1100L

/**
 * Authentic Material 3 Expressive wavy linear progress indicator along the lower edge of the Mini-Player:
 * - Active (played) segment ripples clearly as a smooth Material 3 sine wave (3.0dp amplitude, 12dp wavelength)
 *   while playing and flattens smoothly into a crisp straight line when paused.
 * - Clean Material 3 vertical capsule playhead & gap separating the wavy active segment from the flat remaining track.
 * - Signature Material 3 stop indicator dot at the far right of the inactive track.
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

    val fraction = if (progressState.duration > 0L) {
        (progressState.position.toFloat() / progressState.duration.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val wavy = UiCustomTheme.miniWavyProgress
    val waveScale by animateFloatAsState(
        targetValue = if (wavy && playing) 1f else 0f,
        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
        label = "m3MiniWaveScale",
    )

    // Wall-clock frame loop (same proven pattern as SeekBarStyles.kt), unaffected by animator duration scale
    val needsClock = wavy && (playing || waveScale > 0.01f)
    val clockMs = remember { mutableLongStateOf(0L) }
    LaunchedEffect(needsClock, visible) {
        if (!needsClock || !visible) return@LaunchedEffect
        clockMs.longValue = System.nanoTime() / 1_000_000L
        while (true) {
            withFrameMillis { clockMs.longValue = it }
        }
    }

    val accent = AppleMusicAccent
    val inactiveTrack = Color.White.copy(alpha = 0.22f)
    val dockCollapse = LocalDockCollapseProgress.current
    val showCollapsedSearch = LocalDockCollapsedSearchEnabled.current
    val wavePath = remember { Path() }

    Canvas(
        modifier = Modifier
            .zIndex(2f)
            .fillMaxWidth()
            .height(pillFrame.height)
            .graphicsLayer {
                alpha = (1f - progressProvider() / 0.25f).coerceIn(0f, 1f)
            }
    ) {
        val c = dockCollapse().coerceIn(0f, 1f)
        val extraStart = if (pillFrame.merged) 0f else DockOrbClearance.toPx() * c
        val extraEnd = if (pillFrame.merged || !showCollapsedSearch) 0f else DockOrbClearance.toPx() * c
        val leftInset = (pillFrame.start + 22.dp).toPx() + extraStart
        val rightInset = (pillFrame.end + 22.dp).toPx() + extraEnd
        val barWidth = (size.width - leftInset - rightInset).coerceAtLeast(0f)
        if (barWidth <= 2f) return@Canvas

        val strokeW = 2.2.dp.toPx()
        // Lifted 6dp from bottom edge so the full 3.0dp Material 3 wave peaks are cleanly inside the pill clip
        val centerY = size.height - 6.0.dp.toPx()
        val endX = leftInset + barWidth
        val gapPx = 4.5.dp.toPx()
        val thumbX = leftInset + barWidth * fraction

        val phase = if (wavy && waveScale > 0.005f) {
            (clockMs.longValue % MiniWavePeriodMs).toFloat() / MiniWavePeriodMs.toFloat()
        } else 0f

        // 1. Active played segment — unmistakable Material 3 Expressive smooth sine wave
        val activeLen = (thumbX - leftInset).coerceAtLeast(0f)
        if (activeLen > 0.5f) {
            val activeEnd = (thumbX - gapPx * 0.45f).coerceAtLeast(leftInset)
            val drawableLen = (activeEnd - leftInset).coerceAtLeast(0f)
            if (waveScale > 0.01f && drawableLen > 2.dp.toPx()) {
                val wavelength = 12.dp.toPx()
                val amplitude = 3.0.dp.toPx() * waveScale
                val step = 1.5.dp.toPx()
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
                    color = accent,
                    style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            } else if (drawableLen > 0.5f) {
                drawLine(
                    color = accent,
                    start = Offset(leftInset, centerY),
                    end = Offset(activeEnd, centerY),
                    strokeWidth = strokeW,
                    cap = StrokeCap.Round,
                )
            }
        }

        // 2. Inactive remaining track — flat Material 3 line starting after the gap
        val inactiveStart = (thumbX + gapPx * 0.75f).coerceAtMost(endX)
        if (endX - inactiveStart > 1.dp.toPx()) {
            drawLine(
                color = inactiveTrack,
                start = Offset(inactiveStart, centerY),
                end = Offset(endX, centerY),
                strokeWidth = strokeW,
                cap = StrokeCap.Round,
            )
            // Material 3 Expressive stop indicator dot at track end
            drawCircle(
                color = accent.copy(alpha = 0.85f),
                radius = strokeW * 0.55f,
                center = Offset(endX, centerY),
            )
        }

        // 3. Crisp Material 3 Expressive vertical pill thumb at playhead
        if (fraction > 0.002f && fraction < 0.998f) {
            val thumbHalfH = 3.8.dp.toPx()
            drawLine(
                color = Color.White,
                start = Offset(thumbX, centerY - thumbHalfH),
                end = Offset(thumbX, centerY + thumbHalfH),
                strokeWidth = 2.4.dp.toPx(),
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
