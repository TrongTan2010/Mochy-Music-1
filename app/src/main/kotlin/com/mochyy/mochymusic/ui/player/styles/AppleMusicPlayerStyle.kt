package com.mochyy.mochymusic.ui.player.styles

import android.content.Context
import android.media.AudioManager
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.modules.skin.SheetKind
import com.mochyy.mochymusic.ui.component.AppleMusicAccent
import com.mochyy.mochymusic.ui.player.flagship.AmbientBackdrop
import com.mochyy.mochymusic.ui.player.flagship.FollowPicture
import com.mochyy.mochymusic.ui.player.flagship.rememberAmbientColors
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import com.mochyy.mochymusic.ui.utils.resize
import com.mochyy.mochymusic.utils.makeTimeString
import kotlinx.coroutines.delay

private val AppleBackdropScrim = Brush.verticalGradient(
    0f to Color.Black.copy(alpha = 0.16f),
    0.45f to Color.Black.copy(alpha = 0.08f),
    0.78f to Color.Black.copy(alpha = 0.34f),
    1f to Color.Black.copy(alpha = 0.54f),
)

/**
 * Dedicated Apple Music iOS 18/26 Player Style.
 * Features an animated cover-tinted mesh backdrop, play/pause breathing hero artwork,
 * tactile capsule scrubbing bar, authentic Apple Music transport controls, live media
 * volume slider, and 3-icon Apple Music bottom toolbar (Lyrics • Output • Queue).
 */
@Composable
internal fun AppleMusicPlayerStyle(
    actions: StyleActions,
    modifier: Modifier = Modifier,
    preview: Boolean = false,
) {
    val connection = LocalPlayerConnection.current
    val tones = rememberStyleTones()
    val track = rememberStyleTrack()
    val ambient = rememberAmbientColors()
    FollowPicture(ambient, track.artwork)
    val backdropClock = rememberBackdropClock(running = !preview)

    val transport = connection?.uiState?.transportState?.collectAsState()?.value
    val progressState = connection?.uiState?.progressState?.collectAsState()?.value
    val currentSong = connection?.currentSong?.collectAsState(initial = null)?.value
    val playingFromMap = connection?.service?.playingFrom?.collectAsState()?.value.orEmpty()

    val isPlaying = transport?.isPlaying == true
    val isLiked = currentSong?.song?.liked == true
    val mediaId = currentSong?.id ?: connection?.mediaMetadata?.value?.id
    val sourceBadge = remember(mediaId, playingFromMap) {
        val from = mediaId?.let { playingFromMap[it] }
        when {
            from == null -> null
            !from.quality.isNullOrBlank() -> "${from.name} · ${from.quality}"
            else -> from.name
        }
    }

    val durationMs = (progressState?.duration ?: 0L).coerceAtLeast(0L)
    val rawPositionMs = (progressState?.position ?: 0L).coerceAtLeast(0L)

    // Hero cover gently scales up when playing and settles back when paused (signature Apple Music spring)
    val coverScale by animateFloatAsState(
        targetValue = if (isPlaying || preview) 1f else 0.86f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 260f),
        label = "appleCoverBreath",
    )

    StyleFrame(
        preview = preview,
        modifier = modifier,
        tones = tones,
        background = {
            Box(Modifier.fillMaxSize()) {
                val customBg = com.mochyy.mochymusic.ui.component.UiCustomTheme.customBgPath
                if (com.mochyy.mochymusic.ui.component.UiCustomTheme.applyBgToPlayer && !customBg.isNullOrBlank()) {
                    com.mochyy.mochymusic.ui.component.CustomUiWallpaperLayer(modifier = Modifier.fillMaxSize())
                } else {
                    AmbientBackdrop(
                        colors = ambient,
                        time = if (preview) ({ 0f }) else backdropClock,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(AppleBackdropScrim),
                )
            }
        },
    ) { landscape, _, _ ->
        val insets = styleInsets(landscape)
        if (landscape) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = insets.start + 28.dp,
                        end = insets.end + 28.dp,
                        top = insets.top + 16.dp,
                        bottom = insets.bottom + 16.dp,
                    ),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(0.45f)
                        .fillMaxHeight(),
                ) {
                    AppleMusicHeroCover(
                        artworkUrl = track.artwork,
                        scale = coverScale,
                        modifier = Modifier
                            .fillMaxHeight(0.84f)
                            .aspectRatio(1f),
                    )
                }
                Column(
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(0.55f)
                        .fillMaxHeight(),
                ) {
                    AppleMusicTitleRow(
                        title = track.title.ifBlank { "Đang phát" },
                        artist = track.artist,
                        isLiked = isLiked,
                        onToggleLike = { if (!preview) connection?.service?.toggleLike() },
                        onMenu = { if (!preview) actions.onOtherMenu() },
                    )
                    Spacer(Modifier.height(18.dp))
                    AppleMusicSeekBar(
                        positionMs = rawPositionMs,
                        durationMs = durationMs,
                        sourceBadge = sourceBadge,
                        enabled = !preview,
                        onSeek = { target -> connection?.seekTo(target) },
                    )
                    Spacer(Modifier.height(22.dp))
                    AppleMusicTransportRow(
                        isPlaying = isPlaying,
                        onPrevious = { if (!preview) connection?.seekToPrevious() },
                        onPlayPause = { if (!preview) connection?.togglePlayPause() },
                        onNext = { if (!preview) connection?.seekToNext() },
                    )
                    Spacer(Modifier.height(20.dp))
                    AppleMusicVolumeBar(enabled = !preview)
                    Spacer(Modifier.height(16.dp))
                    AppleMusicBottomToolbar(
                        onLyrics = { if (!preview) actions.onLyrics?.invoke() },
                        onOutput = { if (!preview) actions.onOpenSheet?.invoke(SheetKind.Output) },
                        onQueue = { if (!preview) actions.onQueue?.invoke() },
                    )
                }
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = insets.start + 28.dp,
                        end = insets.end + 28.dp,
                        top = insets.top + 10.dp,
                        bottom = insets.bottom + 20.dp,
                    ),
            ) {
                // Top iOS drag grabber pill
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(5.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.34f))
                        .clickable(
                            enabled = !preview,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { actions.onClose() },
                )

                // Hero Artwork centered in flexible upper stage
                BoxWithConstraints(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 18.dp),
                ) {
                    val coverSize = minOf(maxWidth, maxHeight)
                    AppleMusicHeroCover(
                        artworkUrl = track.artwork,
                        scale = coverScale,
                        modifier = Modifier.size(coverSize),
                    )
                }

                AppleMusicTitleRow(
                    title = track.title.ifBlank { "Đang phát" },
                    artist = track.artist,
                    isLiked = isLiked,
                    onToggleLike = { if (!preview) connection?.service?.toggleLike() },
                    onMenu = { if (!preview) actions.onOtherMenu() },
                )

                Spacer(Modifier.height(18.dp))

                AppleMusicSeekBar(
                    positionMs = rawPositionMs,
                    durationMs = durationMs,
                    sourceBadge = sourceBadge,
                    enabled = !preview,
                    onSeek = { target -> connection?.seekTo(target) },
                )

                Spacer(Modifier.height(26.dp))

                AppleMusicTransportRow(
                    isPlaying = isPlaying,
                    onPrevious = { if (!preview) connection?.seekToPrevious() },
                    onPlayPause = { if (!preview) connection?.togglePlayPause() },
                    onNext = { if (!preview) connection?.seekToNext() },
                )

                Spacer(Modifier.height(26.dp))

                AppleMusicVolumeBar(enabled = !preview)

                Spacer(Modifier.height(18.dp))

                AppleMusicBottomToolbar(
                    onLyrics = { if (!preview) actions.onLyrics?.invoke() },
                    onOutput = { if (!preview) actions.onOpenSheet?.invoke(SheetKind.Output) },
                    onQueue = { if (!preview) actions.onQueue?.invoke() },
                )
            }
        }
    }
}

@Composable
private fun AppleMusicHeroCover(
    artworkUrl: String?,
    scale: Float,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(com.mochyy.mochymusic.ui.component.UiCustomTheme.playerCoverCornerDp.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = 24.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.65f),
                spotColor = Color.Black.copy(alpha = 0.72f),
            )
            .clip(shape)
            .background(Color(0xFF1F1F26)),
    ) {
        if (!artworkUrl.isNullOrBlank()) {
            AsyncImage(
                model = artworkUrl.resize(1200, 1200),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.music_note),
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.45f),
                modifier = Modifier.size(64.dp),
            )
        }
    }
}

@Composable
private fun AppleMusicTitleRow(
    title: String,
    artist: String,
    isLiked: Boolean,
    onToggleLike: () -> Unit,
    onMenu: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 21.sp,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
            )
            if (artist.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = artist,
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp,
                    color = Color.White.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.14f))
                .clickable(onClick = onToggleLike),
        ) {
            Icon(
                painter = painterResource(if (isLiked) R.drawable.favorite else R.drawable.favorite_border),
                contentDescription = "Yêu thích",
                tint = if (isLiked) AppleMusicAccent else Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.14f))
                .clickable(onClick = onMenu),
        ) {
            Icon(
                painter = painterResource(R.drawable.more_horiz),
                contentDescription = "Tùy chọn",
                tint = Color.White.copy(alpha = 0.92f),
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

@Composable
private fun AppleMusicSeekBar(
    positionMs: Long,
    durationMs: Long,
    sourceBadge: String?,
    enabled: Boolean,
    onSeek: (Long) -> Unit,
) {
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var barWidthPx by remember { mutableFloatStateOf(1f) }
    var lastPositionMs by remember { mutableLongStateOf(positionMs) }
    if (!dragging) lastPositionMs = positionMs

    val liveFraction = if (durationMs > 0L) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
    val shownFraction = if (dragging) dragFraction else liveFraction
    val shownPosMs = if (dragging && durationMs > 0L) (dragFraction * durationMs).toLong() else lastPositionMs
    val remainingMs = (durationMs - shownPosMs).coerceAtLeast(0L)

    val trackHeight by animateDpAsState(
        targetValue = if (dragging) 11.dp else 6.dp,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 420f),
        label = "appleSeekHeight",
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .onSizeChanged { barWidthPx = it.width.toFloat().coerceAtLeast(1f) }
                .then(
                    if (enabled && durationMs > 0L) {
                        Modifier
                            .pointerInput(durationMs) {
                                detectTapGestures { offset ->
                                    val f = (offset.x / barWidthPx).coerceIn(0f, 1f)
                                    onSeek((f * durationMs).toLong())
                                }
                            }
                            .pointerInput(durationMs) {
                                detectHorizontalDragGestures(
                                    onDragStart = { offset ->
                                        dragging = true
                                        dragFraction = (offset.x / barWidthPx).coerceIn(0f, 1f)
                                    },
                                    onHorizontalDrag = { change, _ ->
                                        change.consume()
                                        dragFraction = (change.position.x / barWidthPx).coerceIn(0f, 1f)
                                    },
                                    onDragEnd = {
                                        onSeek((dragFraction * durationMs).toLong())
                                        dragging = false
                                    },
                                    onDragCancel = { dragging = false },
                                )
                            }
                    } else Modifier
                ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(trackHeight)
                    .drawBehind {
                        val cr = CornerRadius(size.height / 2f, size.height / 2f)
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.22f),
                            size = size,
                            cornerRadius = cr,
                        )
                        val fillW = (size.width * shownFraction).coerceIn(0f, size.width)
                        if (fillW > 0f) {
                            drawRoundRect(
                                color = if (dragging) Color.White else Color.White.copy(alpha = 0.88f),
                                size = Size(fillW, size.height),
                                cornerRadius = cr,
                            )
                        }
                    },
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = makeTimeString(shownPosMs),
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 11.5.sp,
                color = Color.White.copy(alpha = 0.62f),
            )
            if (!sourceBadge.isNullOrBlank()) {
                Text(
                    text = sourceBadge,
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 10.5.sp,
                    color = Color.White.copy(alpha = 0.78f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White.copy(alpha = 0.13f))
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                )
            }
            Text(
                text = if (durationMs > 0L) "-${makeTimeString(remainingMs)}" else "0:00",
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 11.5.sp,
                color = Color.White.copy(alpha = 0.62f),
            )
        }
    }
}

@Composable
private fun AppleMusicTransportRow(
    isPlaying: Boolean,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
        modifier = Modifier.fillMaxWidth(),
    ) {
        AppleBounceButton(
            iconRes = R.drawable.skip_previous,
            contentDescription = "Bài trước",
            iconSize = 36.dp,
            hitSize = 58.dp,
            onClick = onPrevious,
        )
        AppleBounceButton(
            iconRes = if (isPlaying) R.drawable.pause_applemusic else R.drawable.play_applemusic,
            contentDescription = if (isPlaying) "Tạm dừng" else "Phát",
            iconSize = 46.dp,
            hitSize = 68.dp,
            onClick = onPlayPause,
        )
        AppleBounceButton(
            iconRes = R.drawable.skip_next,
            contentDescription = "Bài tiếp",
            iconSize = 36.dp,
            hitSize = 58.dp,
            onClick = onNext,
        )
    }
}

@Composable
private fun AppleBounceButton(
    iconRes: Int,
    contentDescription: String,
    iconSize: Dp,
    hitSize: Dp,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.84f else 1f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 500f),
        label = "appleBtnScale",
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(hitSize)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
private fun AppleMusicVolumeBar(enabled: Boolean) {
    val context = LocalContext.current
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }
    val maxVol = remember(audioManager) {
        (audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15).coerceAtLeast(1)
    }
    var fraction by remember {
        mutableFloatStateOf(
            ((audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: (maxVol / 2)).toFloat() / maxVol).coerceIn(0f, 1f)
        )
    }
    var dragging by remember { mutableStateOf(false) }
    var barWidthPx by remember { mutableFloatStateOf(1f) }

    // Keep volume bar synced with hardware volume keys while open
    LaunchedEffect(enabled, audioManager) {
        if (!enabled || audioManager == null) return@LaunchedEffect
        while (true) {
            if (!dragging) {
                val cur = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                fraction = (cur.toFloat() / maxVol).coerceIn(0f, 1f)
            }
            delay(400)
        }
    }

    val trackHeight by animateDpAsState(
        targetValue = if (dragging) 10.dp else 5.5.dp,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 420f),
        label = "appleVolHeight",
    )

    fun applyVolume(newFraction: Float) {
        val clamped = newFraction.coerceIn(0f, 1f)
        fraction = clamped
        val targetStep = (clamped * maxVol).toInt().coerceIn(0, maxVol)
        runCatching {
            audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, targetStep, 0)
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(
            painter = painterResource(R.drawable.volume_down),
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.size(16.dp),
        )
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .weight(1f)
                .height(22.dp)
                .onSizeChanged { barWidthPx = it.width.toFloat().coerceAtLeast(1f) }
                .then(
                    if (enabled && audioManager != null) {
                        Modifier
                            .pointerInput(Unit) {
                                detectTapGestures { offset ->
                                    applyVolume(offset.x / barWidthPx)
                                }
                            }
                            .pointerInput(Unit) {
                                detectHorizontalDragGestures(
                                    onDragStart = { offset ->
                                        dragging = true
                                        applyVolume(offset.x / barWidthPx)
                                    },
                                    onHorizontalDrag = { change, _ ->
                                        change.consume()
                                        applyVolume(change.position.x / barWidthPx)
                                    },
                                    onDragEnd = { dragging = false },
                                    onDragCancel = { dragging = false },
                                )
                            }
                    } else Modifier
                ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(trackHeight)
                    .drawBehind {
                        val cr = CornerRadius(size.height / 2f, size.height / 2f)
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.22f),
                            size = size,
                            cornerRadius = cr,
                        )
                        val fillW = (size.width * fraction).coerceIn(0f, size.width)
                        if (fillW > 0f) {
                            drawRoundRect(
                                color = if (dragging) Color.White else Color.White.copy(alpha = 0.85f),
                                size = Size(fillW, size.height),
                                cornerRadius = cr,
                            )
                        }
                    },
            )
        }
        Icon(
            painter = painterResource(R.drawable.volume_up),
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun AppleMusicBottomToolbar(
    onLyrics: () -> Unit,
    onOutput: () -> Unit,
    onQueue: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
        modifier = Modifier.fillMaxWidth(),
    ) {
        AppleToolbarIcon(
            iconRes = R.drawable.lyrics,
            contentDescription = "Lời bài hát",
            onClick = onLyrics,
        )
        AppleToolbarIcon(
            iconRes = R.drawable.speaker_apple,
            contentDescription = "Thiết bị phát",
            onClick = onOutput,
        )
        AppleToolbarIcon(
            iconRes = R.drawable.queue_music,
            contentDescription = "Danh sách phát",
            onClick = onQueue,
        )
    }
}

@Composable
private fun AppleToolbarIcon(
    iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = Color.White.copy(alpha = 0.78f),
            modifier = Modifier.size(22.dp),
        )
    }
}
