// PlayerHandle.kt

package com.mochyy.mochymusic.ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.Window
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.PlayerHorizontalPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.mochyy.mochymusic.ui.component.BottomSheetState
import com.mochyy.mochymusic.ui.component.drawBendingBar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** The handle's opacity while the player is being touched, and once it has been left alone. */
private const val HandleActiveAlpha = 0.6f
private const val HandleIdleAlpha = 0.15f

/** How long the player has to be left alone before the handle goes faint. */
private const val HandleIdleDelayMs = 4_000L

/** The bar: its length end to end (round caps included), its thickness, and its centre below the status bar. */
private val HandleLength = 100.dp
private val HandleThickness = 5.dp
private val HandleCentreBelowStatusBar = 10.dp

/** The chevron it bends into: end to end, and how far its point drops below its ends. */
private val ChevronLength = 44.dp
private val ChevronDepth = 10.dp

/** Where a tap on it lands: wider and taller than the bar itself, centred on it. */
private val HandleTargetWidth = 150.dp
private val HandleTargetHeight = 28.dp

/** How far open the sheet has to look for a tap on the handle to close it (the mini player sits at 0). */
private const val HandleTapMinProgress = 0.9f

/** How often the colour behind it is looked at again while the player is open. */
private const val HandleSampleIntervalMs = 1_500L

/** The strip just under the bar that's sampled for its colour: how far below the bar's centre, and how tall. */
private val SampleBelowCentre = 6.dp
private val SampleHeight = 10.dp
private const val SampleBitmapWidth = 24
private const val SampleBitmapHeight = 4

private val HandleWakeSpec = tween<Float>(durationMillis = 220, easing = FastOutSlowInEasing)
private val HandleFadeSpec = tween<Float>(durationMillis = 900, easing = FastOutSlowInEasing)
private val HandleBendSpring = spring<Float>(dampingRatio = 0.82f, stiffness = 420f)

/**
 * The handle's brightness: up while the player is touched, faint once it's been left alone for
 * [HandleIdleDelayMs]. Fed by the sheet's touches ([onTouchActivity]).
 */
@Stable
class PlayerHandleState internal constructor(private val scope: CoroutineScope) {
    internal val alpha = Animatable(HandleActiveAlpha)
    private var idleJob: Job? = null

    /** The player was touched (true), or the last finger left it (false). */
    fun onTouchActivity(touching: Boolean) {
        if (touching) {
            idleJob?.cancel()
            idleJob = null
            scope.launch { alpha.animateTo(HandleActiveAlpha, HandleWakeSpec) }
        } else {
            restartIdleWait()
        }
    }

    /** Bright, with a fresh wait before it fades: the player has just opened. */
    internal fun wake() {
        scope.launch { alpha.animateTo(HandleActiveAlpha, HandleWakeSpec) }
        restartIdleWait()
    }

    private fun restartIdleWait() {
        idleJob?.cancel()
        idleJob = scope.launch {
            delay(HandleIdleDelayMs)
            alpha.animateTo(HandleIdleAlpha, HandleFadeSpec)
        }
    }
}

@Composable
fun rememberPlayerHandleState(): PlayerHandleState {
    val scope = rememberCoroutineScope()
    return remember(scope) { PlayerHandleState(scope) }
}

/**
 * The open player's handle, at the top of every style: a bar that says the player is a sheet to
 * pull down. 60% while the player is touched, 15% once it's been left alone, black or white -
 * whichever reads over what's behind it. A tap on it closes the player; pressed, and while the
 * player closes, it bends into a chevron pointing down.
 *
 * Every per-frame value (the bend, the brightness, the colour's fade) is read while drawing, so
 * none of it recomposes anything.
 */
@Composable
fun PlayerHandle(
    state: PlayerHandleState,
    sheet: BottomSheetState,
    statusBarTop: Dp,
    /** False while something else owns the top of the player (the studio). */
    enabled: () -> Boolean,
    modifier: Modifier = Modifier,
    onMenuClick: (() -> Unit)? = null,
    hiddenAmount: () -> Float = { 0f },
) {
    val currentEnabled by rememberUpdatedState(enabled)

    LaunchedEffect(sheet, state) {
        snapshotFlow { sheet.isExpandedOrExpanding }.collectLatest { open -> if (open) state.wake() }
    }

    val topBarAlpha = {
        val sheetVis = ((sheet.progress - 0.55f) / 0.40f).coerceIn(0f, 1f)
        val immersiveVis = (1f - hiddenAmount()).coerceIn(0f, 1f)
        if (currentEnabled()) sheetVis * immersiveVis else 0f
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = statusBarTop + 8.dp, start = PlayerHorizontalPadding, end = PlayerHorizontalPadding)
            .graphicsLayer {
                val a = topBarAlpha()
                alpha = a
                translationY = -18.dp.toPx() * hiddenAmount().coerceIn(0f, 1f)
                compositingStrategy = CompositingStrategy.ModulateAlpha
            }
    ) {
        // Left: circular frosted glass collapse button (v)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.16f))
                .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                .clickable(enabled = sheet.progress >= HandleTapMinProgress) {
                    sheet.collapseSoft()
                },
        ) {
            Icon(
                painter = painterResource(R.drawable.expand_more),
                contentDescription = "Thu nhỏ",
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }

        // Center: frosted glass pill badge "Đang phát"
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .height(32.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.16f))
                .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(50))
                .clickable(enabled = sheet.progress >= HandleTapMinProgress) {
                    sheet.collapseSoft()
                }
                .padding(horizontal = 16.dp),
        ) {
            Text(
                text = "Đang phát",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }

        // Right: circular frosted glass menu button (...)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.16f))
                .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                .clickable(enabled = sheet.progress >= HandleTapMinProgress && onMenuClick != null) {
                    onMenuClick?.invoke()
                },
        ) {
            Icon(
                painter = painterResource(R.drawable.more_horiz),
                contentDescription = "Tùy chọn",
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * Whether what's behind the handle is light, so the handle should be black. Read off the window
 * itself - the one way that holds for every style, cover, canvas and page - from a small strip just
 * under the bar, while the player sits open, every [HandleSampleIntervalMs]. The copy is a few dozen
 * pixels, made on the render thread.
 */
@Composable
private fun rememberOnLightBehind(sheet: BottomSheetState, targetBounds: FloatArray): State<Boolean> {
    val onLight = remember { mutableStateOf(false) }
    val view = LocalView.current
    val density = LocalDensity.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(view, sheet, density) {
        val window = view.context.findActivity()?.window ?: return@LaunchedEffect
        val bitmap = Bitmap.createBitmap(SampleBitmapWidth, SampleBitmapHeight, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(SampleBitmapWidth * SampleBitmapHeight)
        val handler = Handler(Looper.getMainLooper())
        val halfBar = with(density) { HandleLength.toPx() } / 2f
        val below = with(density) { SampleBelowCentre.toPx() }
        val height = with(density) { SampleHeight.toPx() }
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            snapshotFlow { sheet.isExpanded }.collectLatest { open ->
                if (!open) return@collectLatest
                while (true) {
                    val cx = (targetBounds[0] + targetBounds[2]) / 2f
                    val cy = (targetBounds[1] + targetBounds[3]) / 2f
                    val rect = android.graphics.Rect(
                        (cx - halfBar).toInt(),
                        (cy + below).toInt(),
                        (cx + halfBar).toInt(),
                        (cy + below + height).toInt(),
                    )
                    if (rect.width() > 0 && rect.height() > 0 && copyWindow(window, rect, bitmap, handler)) {
                        bitmap.getPixels(pixels, 0, SampleBitmapWidth, 0, 0, SampleBitmapWidth, SampleBitmapHeight)
                        var sum = 0f
                        for (c in pixels) {
                            sum += 0.299f * ((c shr 16) and 0xFF) + 0.587f * ((c shr 8) and 0xFF) + 0.114f * (c and 0xFF)
                        }
                        val luminance = sum / (pixels.size * 255f)
                        // Apart, so a backdrop sitting near the line doesn't flip it back and forth.
                        onLight.value = if (onLight.value) luminance > 0.52f else luminance > 0.66f
                    }
                    delay(HandleSampleIntervalMs)
                }
            }
        }
    }
    return onLight
}

private suspend fun copyWindow(window: Window, rect: android.graphics.Rect, bitmap: Bitmap, handler: Handler): Boolean =
    suspendCancellableCoroutine { continuation ->
        try {
            PixelCopy.request(
                window,
                rect,
                bitmap,
                { result -> if (continuation.isActive) continuation.resume(result == PixelCopy.SUCCESS) },
                handler,
            )
        } catch (_: IllegalArgumentException) {
            // no surface to copy from yet (or any more)
            if (continuation.isActive) continuation.resume(false)
        }
    }

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
