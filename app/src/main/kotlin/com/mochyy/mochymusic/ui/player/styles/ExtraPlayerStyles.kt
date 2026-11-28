package com.mochyy.mochymusic.ui.player.styles

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
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
import androidx.compose.runtime.withFrameNanos
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
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.extensions.toggleRepeatMode
import com.mochyy.mochymusic.modules.skin.SheetKind
import com.mochyy.mochymusic.ui.component.AppleMusicAccent
import com.mochyy.mochymusic.ui.component.CustomUiWallpaperLayer
import com.mochyy.mochymusic.ui.component.UiCustomTheme
import com.mochyy.mochymusic.ui.player.flagship.AmbientBackdrop
import com.mochyy.mochymusic.ui.player.flagship.FollowPicture
import com.mochyy.mochymusic.ui.player.flagship.rememberAmbientColors
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import com.mochyy.mochymusic.ui.utils.resize
import com.mochyy.mochymusic.utils.makeTimeString

private val GlassCardRim = Brush.verticalGradient(
    0f to Color.White.copy(alpha = 0.34f),
    0.5f to Color.White.copy(alpha = 0.08f),
    1f to Color.White.copy(alpha = 0.22f),
)

private val EditorialScrim = Brush.verticalGradient(
    0f to Color.Black.copy(alpha = 0.15f),
    0.45f to Color.Black.copy(alpha = 0.55f),
    0.78f to Color(0xFF0A0A0D).copy(alpha = 0.92f),
    1f to Color(0xFF070709),
)

@Composable
private fun StyleBackdropWithWallpaper(
    artworkUrl: String?,
    preview: Boolean,
    darkScrim: Brush,
) {
    val ambient = rememberAmbientColors()
    FollowPicture(ambient, artworkUrl)
    val clock = rememberBackdropClock(running = !preview)
    Box(Modifier.fillMaxSize()) {
        val customBg = UiCustomTheme.customBgPath
        if (UiCustomTheme.applyBgToPlayer && !customBg.isNullOrBlank()) {
            CustomUiWallpaperLayer(modifier = Modifier.fillMaxSize())
        } else {
            AmbientBackdrop(
                colors = ambient,
                time = if (preview) ({ 0f }) else clock,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(darkScrim),
        )
    }
}

// ============================================================================================
// 1. LIQUID GLASS PLAYER (iOS 26 / ColorOS 17 Frosted Floating Glass Cards)
// ============================================================================================

@Composable
internal fun LiquidGlassPlayerStyle(
    actions: StyleActions,
    modifier: Modifier = Modifier,
    preview: Boolean = false,
) {
    val connection = LocalPlayerConnection.current
    val tones = rememberStyleTones()
    val track = rememberStyleTrack()
    val transport = connection?.uiState?.transportState?.collectAsState()?.value
    val progress = connection?.uiState?.progressState?.collectAsState()?.value
    val currentSong = connection?.currentSong?.collectAsState(initial = null)?.value

    val isPlaying = transport?.isPlaying == true
    val isLiked = currentSong?.song?.liked == true
    val durationMs = (progress?.duration ?: 0L).coerceAtLeast(0L)
    val positionMs = (progress?.position ?: 0L).coerceAtLeast(0L)
    val coverCorner = UiCustomTheme.playerCoverCornerDp.dp
    val accent = AppleMusicAccent
    val glassAlpha = (UiCustomTheme.glassOpacity * 0.42f).coerceIn(0.16f, 0.44f)

    StyleFrame(
        preview = preview,
        modifier = modifier,
        tones = tones,
        background = {
            StyleBackdropWithWallpaper(
                artworkUrl = track.artwork,
                preview = preview,
                darkScrim = Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.20f),
                    0.5f to Color.Black.copy(alpha = 0.28f),
                    1f to Color.Black.copy(alpha = 0.56f),
                ),
            )
        },
    ) { landscape, _, _ ->
        val insets = styleInsets(landscape)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = insets.start + 22.dp,
                    end = insets.end + 22.dp,
                    top = insets.top + 10.dp,
                    bottom = insets.bottom + 18.dp,
                ),
        ) {
            // Top glass handle
            Box(
                modifier = Modifier
                    .width(42.dp)
                    .height(5.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.36f))
                    .clickable(enabled = !preview) { actions.onClose() },
            )

            // Frosted glass cover frame
            BoxWithConstraints(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
            ) {
                val frameSize = minOf(maxWidth, maxHeight)
                val frameShape = RoundedCornerShape((coverCorner + 6.dp).coerceAtMost(48.dp))
                val artShape = RoundedCornerShape(coverCorner)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(frameSize)
                        .clip(frameShape)
                        .background(Color(0xFF14141C).copy(alpha = glassAlpha))
                        .border(1.1.dp, GlassCardRim, frameShape)
                        .padding(8.dp),
                ) {
                    CoverArtBox(
                        artworkUrl = track.artwork,
                        shape = artShape,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            // Frosted Glass Control Card (Title + Seek + Transport)
            val cardShape = RoundedCornerShape(28.dp)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(cardShape)
                    .background(Color(0xFF14141C).copy(alpha = (glassAlpha + 0.14f).coerceIn(0.28f, 0.62f)))
                    .border(1.dp, GlassCardRim, cardShape)
                    .padding(horizontal = 18.dp, vertical = 16.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title.ifBlank { "Đang phát" },
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                        )
                        if (track.artist.isNotBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = track.artist,
                                fontFamily = InterFontFamily,
                                fontSize = 14.sp,
                                color = Color.White.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    GlassCircleButton(
                        iconRes = if (isLiked) R.drawable.favorite else R.drawable.favorite_border,
                        tint = if (isLiked) accent else Color.White,
                        size = 38.dp,
                        iconSize = 19.dp,
                        onClick = { if (!preview) connection?.service?.toggleLike() },
                    )
                }

                Spacer(Modifier.height(14.dp))

                SharedCapsuleSeekBar(
                    positionMs = positionMs,
                    durationMs = durationMs,
                    activeColor = accent,
                    enabled = !preview,
                    onSeek = { connection?.seekTo(it) },
                )

                Spacer(Modifier.height(16.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    GlassCircleButton(
                        iconRes = R.drawable.skip_previous,
                        tint = Color.White,
                        size = 48.dp,
                        iconSize = 26.dp,
                        onClick = { if (!preview) connection?.seekToPrevious() },
                    )
                    // Luminous accent glass Play orb
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(64.dp)
                            .shadow(16.dp, CircleShape, ambientColor = accent, spotColor = accent)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(accent, accent.copy(alpha = 0.78f))
                                )
                            )
                            .border(1.dp, Color.White.copy(alpha = 0.42f), CircleShape)
                            .clickable(enabled = !preview) { connection?.togglePlayPause() },
                    ) {
                        Icon(
                            painter = painterResource(if (isPlaying) R.drawable.pause_applemusic else R.drawable.play_applemusic),
                            contentDescription = "Phát / Tạm dừng",
                            tint = Color.White,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                    GlassCircleButton(
                        iconRes = R.drawable.skip_next,
                        tint = Color.White,
                        size = 48.dp,
                        iconSize = 26.dp,
                        onClick = { if (!preview) connection?.seekToNext() },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Floating Glass Action Pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFF14141C).copy(alpha = (glassAlpha + 0.10f).coerceIn(0.24f, 0.58f)))
                    .border(0.9.dp, GlassCardRim, RoundedCornerShape(50))
                    .padding(horizontal = 10.dp),
            ) {
                StyleNavAction(R.drawable.lyrics, "Lời bài hát") { if (!preview) actions.onLyrics?.invoke() }
                StyleNavAction(R.drawable.speaker_apple, "Thiết bị") { if (!preview) actions.onOpenSheet?.invoke(SheetKind.Output) }
                StyleNavAction(R.drawable.queue_music, "Danh sách") { if (!preview) actions.onQueue?.invoke() }
                StyleNavAction(R.drawable.more_horiz, "Thêm") { if (!preview) actions.onOtherMenu() }
            }
        }
    }
}

// ============================================================================================
// 2. VINYL CD PLAYER (Continuously Rotating Glossy Disc + Glow Controls)
// ============================================================================================

@Composable
internal fun VinylCdPlayerStyle(
    actions: StyleActions,
    modifier: Modifier = Modifier,
    preview: Boolean = false,
) {
    val connection = LocalPlayerConnection.current
    val tones = rememberStyleTones()
    val track = rememberStyleTrack()
    val transport = connection?.uiState?.transportState?.collectAsState()?.value
    val progress = connection?.uiState?.progressState?.collectAsState()?.value
    val currentSong = connection?.currentSong?.collectAsState(initial = null)?.value

    val isPlaying = transport?.isPlaying == true
    val isLiked = currentSong?.song?.liked == true
    val durationMs = (progress?.duration ?: 0L).coerceAtLeast(0L)
    val positionMs = (progress?.position ?: 0L).coerceAtLeast(0L)
    val accent = AppleMusicAccent

    // Smooth continuous rotation (26 deg/sec) that pauses cleanly when paused
    var angle by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isPlaying, preview) {
        if (preview || !isPlaying) return@LaunchedEffect
        var lastNs = 0L
        while (true) {
            withFrameNanos { now ->
                if (lastNs != 0L) {
                    val dtSec = (now - lastNs) / 1_000_000_000f
                    angle = (angle + dtSec * 26f) % 360f
                }
                lastNs = now
            }
        }
    }

    StyleFrame(
        preview = preview,
        modifier = modifier,
        tones = tones,
        background = {
            StyleBackdropWithWallpaper(
                artworkUrl = track.artwork,
                preview = preview,
                darkScrim = Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.28f),
                    0.55f to Color.Black.copy(alpha = 0.42f),
                    1f to Color.Black.copy(alpha = 0.72f),
                ),
            )
        },
    ) { landscape, _, _ ->
        val insets = styleInsets(landscape)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = insets.start + 26.dp,
                    end = insets.end + 26.dp,
                    top = insets.top + 10.dp,
                    bottom = insets.bottom + 20.dp,
                ),
        ) {
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(5.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.34f))
                    .clickable(enabled = !preview) { actions.onClose() },
            )

            // Spinning Vinyl / CD Stage
            BoxWithConstraints(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 18.dp),
            ) {
                val discSize = minOf(maxWidth, maxHeight) * 0.94f
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(discSize)
                        .shadow(28.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
                        .graphicsLayer { rotationZ = angle },
                ) {
                    // Outer dark vinyl disc with concentric grooves & glossy sheen
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val r = size.minDimension / 2f
                        drawCircle(
                            brush = Brush.radialGradient(
                                0f to Color(0xFF202026),
                                0.55f to Color(0xFF101014),
                                0.85f to Color(0xFF18181F),
                                1f to Color(0xFF0A0A0E),
                            ),
                            radius = r,
                        )
                        // Subtle vinyl grooves
                        for (i in 1..6) {
                            val gr = r * (0.56f + i * 0.065f)
                            drawCircle(
                                color = Color.White.copy(alpha = if (i % 2 == 0) 0.07f else 0.035f),
                                radius = gr,
                                style = Stroke(width = 1.dp.toPx()),
                            )
                        }
                        // Diagonal glossy sheen reflection
                        drawCircle(
                            brush = Brush.sweepGradient(
                                0.0f to Color.Transparent,
                                0.12f to Color.White.copy(alpha = 0.15f),
                                0.24f to Color.Transparent,
                                0.50f to Color.Transparent,
                                0.62f to Color.White.copy(alpha = 0.13f),
                                0.74f to Color.Transparent,
                                1.0f to Color.Transparent,
                            ),
                            radius = r * 0.98f,
                        )
                        // Outer chrome hairline rim
                        drawCircle(
                            color = Color.White.copy(alpha = 0.22f),
                            radius = r - 1.dp.toPx(),
                            style = Stroke(width = 1.5.dp.toPx()),
                        )
                    }

                    // Center album label
                    CoverArtBox(
                        artworkUrl = track.artwork,
                        shape = CircleShape,
                        modifier = Modifier
                            .size(discSize * 0.54f)
                            .border(2.dp, accent.copy(alpha = 0.55f), CircleShape),
                    )

                    // Spindle center hole
                    Box(
                        modifier = Modifier
                            .size(discSize * 0.12f)
                            .clip(CircleShape)
                            .background(Color(0xFF111116))
                            .border(2.dp, Color.White.copy(alpha = 0.35f), CircleShape),
                    )
                }
            }

            // Centered Editorial Title & Artist
            Text(
                text = track.title.ifBlank { "Đang phát" },
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .basicMarquee(iterations = Int.MAX_VALUE),
            )
            if (track.artist.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = track.artist,
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                    color = Color.White.copy(alpha = 0.68f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(22.dp))

            SharedCapsuleSeekBar(
                positionMs = positionMs,
                durationMs = durationMs,
                activeColor = accent,
                enabled = !preview,
                onSeek = { connection?.seekTo(it) },
            )

            Spacer(Modifier.height(24.dp))

            // Transport row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth(),
            ) {
                StyleNavAction(
                    iconRes = if (isLiked) R.drawable.favorite else R.drawable.favorite_border,
                    contentDescription = "Yêu thích",
                    tint = if (isLiked) accent else Color.White.copy(alpha = 0.78f),
                ) { if (!preview) connection?.service?.toggleLike() }

                StyleNavAction(R.drawable.skip_previous, "Bài trước", iconSize = 32.dp) {
                    if (!preview) connection?.seekToPrevious()
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(66.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable(enabled = !preview) { connection?.togglePlayPause() },
                ) {
                    Icon(
                        painter = painterResource(if (isPlaying) R.drawable.pause_applemusic else R.drawable.play_applemusic),
                        contentDescription = "Phát / Tạm dừng",
                        tint = Color.Black,
                        modifier = Modifier.size(30.dp),
                    )
                }

                StyleNavAction(R.drawable.skip_next, "Bài tiếp", iconSize = 32.dp) {
                    if (!preview) connection?.seekToNext()
                }

                StyleNavAction(R.drawable.more_horiz, "Tùy chọn") {
                    if (!preview) actions.onOtherMenu()
                }
            }

            Spacer(Modifier.height(20.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth(),
            ) {
                StyleNavAction(R.drawable.lyrics, "Lời bài hát") { if (!preview) actions.onLyrics?.invoke() }
                StyleNavAction(R.drawable.speaker_apple, "Thiết bị") { if (!preview) actions.onOpenSheet?.invoke(SheetKind.Output) }
                StyleNavAction(R.drawable.queue_music, "Danh sách") { if (!preview) actions.onQueue?.invoke() }
            }
        }
    }
}

// ============================================================================================
// 3. MINIMAL DARK / EDITORIAL PLAYER (Full Shuffle/Repeat + Live Glowing Lyrics Preview Card)
// ============================================================================================

@Composable
internal fun MinimalDarkPlayerStyle(
    actions: StyleActions,
    modifier: Modifier = Modifier,
    preview: Boolean = false,
) {
    val connection = LocalPlayerConnection.current
    val tones = rememberStyleTones()
    val track = rememberStyleTrack()
    val lyrics = rememberStyleLyrics(readings = false)
    val transport = connection?.uiState?.transportState?.collectAsState()?.value
    val progress = connection?.uiState?.progressState?.collectAsState()?.value
    val currentSong = connection?.currentSong?.collectAsState(initial = null)?.value

    val isPlaying = transport?.isPlaying == true
    val shuffleOn = transport?.shuffleModeEnabled == true
    val repeatMode = transport?.repeatMode ?: Player.REPEAT_MODE_OFF
    val isLiked = currentSong?.song?.liked == true
    val durationMs = (progress?.duration ?: 0L).coerceAtLeast(0L)
    val positionMs = (progress?.position ?: 0L).coerceAtLeast(0L)
    val accent = AppleMusicAccent
    val coverCorner = UiCustomTheme.playerCoverCornerDp.dp

    val activeLyricText = remember(lyrics.lines, positionMs) {
        if (lyrics.lines.isEmpty()) null
        else {
            val idx = lyrics.lines.indexOfLast { it.time <= positionMs }.coerceAtLeast(0)
            lyrics.lines.getOrNull(idx)?.text?.takeIf { it.isNotBlank() }
        }
    }

    StyleFrame(
        preview = preview,
        modifier = modifier,
        tones = tones,
        background = {
            StyleBackdropWithWallpaper(
                artworkUrl = track.artwork,
                preview = preview,
                darkScrim = EditorialScrim,
            )
        },
    ) { landscape, _, _ ->
        val insets = styleInsets(landscape)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = insets.start + 24.dp,
                    end = insets.end + 24.dp,
                    top = insets.top + 10.dp,
                    bottom = insets.bottom + 18.dp,
                ),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .width(38.dp)
                        .height(5.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.32f))
                        .clickable(enabled = !preview) { actions.onClose() },
                )
                Text(
                    text = track.album.ifBlank { "ĐANG PHÁT" }.uppercase(),
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 1.2.sp,
                    color = Color.White.copy(alpha = 0.65f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                    textAlign = TextAlign.Center,
                )
                StyleNavAction(R.drawable.more_horiz, "Tùy chọn", iconSize = 20.dp) {
                    if (!preview) actions.onOtherMenu()
                }
            }

            BoxWithConstraints(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 14.dp),
            ) {
                val size = minOf(maxWidth, maxHeight)
                CoverArtBox(
                    artworkUrl = track.artwork,
                    shape = RoundedCornerShape(coverCorner),
                    modifier = Modifier
                        .size(size)
                        .shadow(22.dp, RoundedCornerShape(coverCorner)),
                )
            }

            // Title + Like row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title.ifBlank { "Đang phát" },
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 21.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                    )
                    if (track.artist.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = track.artist,
                            fontFamily = InterFontFamily,
                            fontSize = 14.5.sp,
                            color = Color.White.copy(alpha = 0.68f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                StyleNavAction(
                    iconRes = if (isLiked) R.drawable.favorite else R.drawable.favorite_border,
                    contentDescription = "Yêu thích",
                    tint = if (isLiked) accent else Color.White.copy(alpha = 0.85f),
                    iconSize = 24.dp,
                ) { if (!preview) connection?.service?.toggleLike() }
            }

            Spacer(Modifier.height(14.dp))

            SharedCapsuleSeekBar(
                positionMs = positionMs,
                durationMs = durationMs,
                activeColor = accent,
                enabled = !preview,
                onSeek = { connection?.seekTo(it) },
            )

            Spacer(Modifier.height(16.dp))

            // Full 5-button Editorial Transport (Shuffle • Prev • Play • Next • Repeat)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                StyleNavAction(
                    iconRes = if (shuffleOn) R.drawable.shuffle_on else R.drawable.shuffle,
                    contentDescription = "Trộn bài",
                    tint = if (shuffleOn) accent else Color.White.copy(alpha = 0.62f),
                    iconSize = 21.dp,
                ) { if (!preview) connection?.player?.shuffleModeEnabled = !shuffleOn }

                StyleNavAction(R.drawable.skip_previous, "Bài trước", iconSize = 32.dp) {
                    if (!preview) connection?.seekToPrevious()
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(accent)
                        .clickable(enabled = !preview) { connection?.togglePlayPause() },
                ) {
                    Icon(
                        painter = painterResource(if (isPlaying) R.drawable.pause_applemusic else R.drawable.play_applemusic),
                        contentDescription = "Phát / Tạm dừng",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp),
                    )
                }

                StyleNavAction(R.drawable.skip_next, "Bài tiếp", iconSize = 32.dp) {
                    if (!preview) connection?.seekToNext()
                }

                val repeatIcon = when (repeatMode) {
                    Player.REPEAT_MODE_ONE -> R.drawable.repeat_one_on
                    Player.REPEAT_MODE_ALL -> R.drawable.repeat_on
                    else -> R.drawable.repeat
                }
                StyleNavAction(
                    iconRes = repeatIcon,
                    contentDescription = "Lặp lại",
                    tint = if (repeatMode != Player.REPEAT_MODE_OFF) accent else Color.White.copy(alpha = 0.62f),
                    iconSize = 21.dp,
                ) { if (!preview) connection?.player?.toggleRepeatMode() }
            }

            Spacer(Modifier.height(16.dp))

            // Embedded Live Glowing Lyrics Preview Card + Queue/Output icons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(accent.copy(alpha = 0.16f))
                        .border(0.8.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
                        .clickable(enabled = !preview) { actions.onLyrics?.invoke() }
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                ) {
                    val glowStrength = UiCustomTheme.lyricsGlowStrength
                    Text(
                        text = activeLyricText ?: "Chạm để mở lời bài hát phát sáng…",
                        style = TextStyle(
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            color = Color.White,
                            shadow = if (glowStrength > 0.05f) {
                                Shadow(
                                    color = accent.copy(alpha = (0.75f * glowStrength).coerceIn(0f, 1f)),
                                    offset = Offset.Zero,
                                    blurRadius = (16f * glowStrength).coerceIn(4f, 28f),
                                )
                            } else null,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                GlassCircleButton(
                    iconRes = R.drawable.speaker_apple,
                    tint = Color.White.copy(alpha = 0.85f),
                    size = 42.dp,
                    iconSize = 19.dp,
                    onClick = { if (!preview) actions.onOpenSheet?.invoke(SheetKind.Output) },
                )
                GlassCircleButton(
                    iconRes = R.drawable.queue_music,
                    tint = Color.White.copy(alpha = 0.85f),
                    size = 42.dp,
                    iconSize = 19.dp,
                    onClick = { if (!preview) actions.onQueue?.invoke() },
                )
            }
        }
    }
}

// ============================================================================================
// Shared helpers for Extra Player Styles
// ============================================================================================

@Composable
private fun CoverArtBox(
    artworkUrl: String?,
    shape: androidx.compose.ui.graphics.Shape,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(shape)
            .background(Color(0xFF1D1D24)),
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
                modifier = Modifier.size(56.dp),
            )
        }
    }
}

@Composable
private fun GlassCircleButton(
    iconRes: Int,
    tint: Color,
    size: Dp,
    iconSize: Dp,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.86f else 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 480f),
        label = "glassBtnScale",
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.14f))
            .border(0.8.dp, Color.White.copy(alpha = 0.24f), CircleShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
private fun StyleNavAction(
    iconRes: Int,
    contentDescription: String,
    tint: Color = Color.White.copy(alpha = 0.82f),
    iconSize: Dp = 22.dp,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
private fun SharedCapsuleSeekBar(
    positionMs: Long,
    durationMs: Long,
    activeColor: Color,
    enabled: Boolean,
    onSeek: (Long) -> Unit,
) {
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var barWidthPx by remember { mutableFloatStateOf(1f) }
    var lastPosMs by remember { mutableLongStateOf(positionMs) }
    if (!dragging) lastPosMs = positionMs

    val liveFraction = if (durationMs > 0L) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
    val shownFraction = if (dragging) dragFraction else liveFraction
    val shownPosMs = if (dragging && durationMs > 0L) (dragFraction * durationMs).toLong() else lastPosMs
    val remainingMs = (durationMs - shownPosMs).coerceAtLeast(0L)

    val trackHeight by animateDpAsState(
        targetValue = if (dragging) 10.dp else 5.5.dp,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 420f),
        label = "sharedSeekH",
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(22.dp)
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
            val useM3Wave = UiCustomTheme.miniWavyProgress
            val playingNow = LocalPlayerConnection.current?.isEffectivelyPlaying?.collectAsState()?.value == true
            if (useM3Wave) {
                com.mochyy.mochymusic.ui.component.SeekBarTrack(
                    style = com.mochyy.mochymusic.ui.component.SeekBarStyle.M3_EXPRESSIVE_LINE,
                    fraction = shownFraction,
                    activeColor = activeColor,
                    inactiveColor = Color.White.copy(alpha = 0.22f),
                    animateWave = (playingNow || preview) && !dragging,
                    active = dragging,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
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
                                    color = activeColor,
                                    size = Size(fillW, size.height),
                                    cornerRadius = cr,
                                )
                            }
                        },
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = makeTimeString(shownPosMs),
                fontFamily = InterFontFamily,
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.6f),
            )
            Text(
                text = if (durationMs > 0L) "-${makeTimeString(remainingMs)}" else "0:00",
                fontFamily = InterFontFamily,
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.6f),
            )
        }
    }
}
