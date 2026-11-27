package com.mochyy.mochymusic.ui.screens.settings

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.mochyy.mochymusic.LocalPlayerAwareWindowInsets
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.CustomAccentColorKey
import com.mochyy.mochymusic.constants.CustomBgApplyAppKey
import com.mochyy.mochymusic.constants.CustomBgApplyPlayerKey
import com.mochyy.mochymusic.constants.CustomBgBlurKey
import com.mochyy.mochymusic.constants.CustomBgDimKey
import com.mochyy.mochymusic.constants.CustomBgPathKey
import com.mochyy.mochymusic.constants.CustomDockCornerKey
import com.mochyy.mochymusic.constants.CustomGlassOpacityKey
import com.mochyy.mochymusic.constants.CustomLyricsGlowKey
import com.mochyy.mochymusic.constants.CustomPlayerCoverCornerKey
import com.mochyy.mochymusic.constants.PlayerStyle
import com.mochyy.mochymusic.constants.PlayerStyleKey
import com.mochyy.mochymusic.ui.component.AppSwitch
import com.mochyy.mochymusic.ui.component.CustomUiWallpaperLayer
import com.mochyy.mochymusic.ui.component.UiCustomTheme
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import com.mochyy.mochymusic.utils.rememberEnumPreference
import com.mochyy.mochymusic.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

const val UiCustomizerRoute = "ui_customizer"

private val CardBg = Color(0xFF1A1A20)

private data class AccentSwatch(val name: String, val argb: Int)

private val AccentSwatches = listOf(
    AccentSwatch("Apple Coral", 0xFFFA2D48.toInt()),
    AccentSwatch("Spotify Green", 0xFF1DB954.toInt()),
    AccentSwatch("SoundCloud", 0xFFFF5500.toInt()),
    AccentSwatch("Electric Blue", 0xFF0A84FF.toInt()),
    AccentSwatch("Violet Neon", 0xFFAF52DE.toInt()),
    AccentSwatch("Cyan Glow", 0xFF32D5FF.toInt()),
    AccentSwatch("Gold Amber", 0xFFFFB300.toInt()),
    AccentSwatch("Rose Pink", 0xFFFF375F.toInt()),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UiCustomizerScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var playerStyle by rememberEnumPreference(PlayerStyleKey, PlayerStyle.FLAGSHIP)
    var accentArgb by rememberPreference(CustomAccentColorKey, UiCustomTheme.DefaultAccentArgb)
    var dockCorner by rememberPreference(CustomDockCornerKey, UiCustomTheme.DefaultDockCornerDp)
    var glassOpacity by rememberPreference(CustomGlassOpacityKey, UiCustomTheme.DefaultGlassOpacity)
    var lyricsGlow by rememberPreference(CustomLyricsGlowKey, UiCustomTheme.DefaultLyricsGlow)
    var coverCorner by rememberPreference(CustomPlayerCoverCornerKey, UiCustomTheme.DefaultPlayerCoverCornerDp)

    var bgPath by rememberPreference(CustomBgPathKey, "")
    var bgBlur by rememberPreference(CustomBgBlurKey, UiCustomTheme.DefaultBgBlurDp)
    var bgDim by rememberPreference(CustomBgDimKey, UiCustomTheme.DefaultBgDimAlpha)
    var applyBgApp by rememberPreference(CustomBgApplyAppKey, true)
    var applyBgPlayer by rememberPreference(CustomBgApplyPlayerKey, true)

    val accent = Color(accentArgb)
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding()

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val savedPath = withContext(Dispatchers.IO) {
                runCatching {
                    val outFile = File(context.filesDir, "custom_ui_bg_${System.currentTimeMillis()}.jpg")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        outFile.outputStream().use { output -> input.copyTo(output) }
                    } ?: return@runCatching null
                    // Remove previous saved wallpaper file to avoid clutter
                    val prev = bgPath.takeIf { it.isNotBlank() }?.let { File(it) }
                    if (prev != null && prev.exists() && prev.absolutePath != outFile.absolutePath) {
                        runCatching { prev.delete() }
                    }
                    outFile.absolutePath
                }.getOrNull()
            }
            if (!savedPath.isNullOrBlank()) {
                bgPath = savedPath
                UiCustomTheme.customBgPath = savedPath
                Toast.makeText(context, "Đã áp dụng ảnh nền mới", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Không thể đọc ảnh đã chọn", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val allStyles = remember {
        listOf(
            PlayerStyle.APPLE_MUSIC to "Apple Music",
            PlayerStyle.LIQUID_GLASS to "Liquid Glass",
            PlayerStyle.VINYL_CD to "Vinyl CD",
            PlayerStyle.MINIMAL_DARK to "Minimal Dark",
            PlayerStyle.FLAGSHIP to "Flagship",
            PlayerStyle.TURNTABLE to "Turntable",
            PlayerStyle.POSTER to "Poster",
            PlayerStyle.TIMELINE to "Timeline",
            PlayerStyle.LYRICS to "Lyrics Stage",
            PlayerStyle.IPOD to "iPod",
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (bgPath.isNotBlank() && applyBgApp) {
            CustomUiWallpaperLayer(
                path = bgPath,
                blurDp = bgBlur,
                dimAlpha = bgDim,
                modifier = Modifier.fillMaxSize(),
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = statusTop + 12.dp,
                bottom = bottomInset + 28.dp,
                start = 18.dp,
                end = 18.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Top bar
            item(key = "header") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.12f))
                            .clickable { navController.navigateUp() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back),
                            contentDescription = "Quay lại",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Tùy biến giao diện",
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            color = Color.White,
                        )
                        Text(
                            text = "Đổi ảnh nền, màu sắc, kiểu Player, Dock & Lyrics Glow",
                            fontFamily = InterFontFamily,
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.62f),
                        )
                    }
                }
            }

            // Live interactive preview card
            item(key = "live_preview") {
                val previewShape = RoundedCornerShape(24.dp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(186.dp)
                        .clip(previewShape)
                        .background(
                            Brush.linearGradient(
                                listOf(accent.copy(alpha = 0.45f), Color(0xFF14141D), Color(0xFF0A0A0F))
                            )
                        )
                        .border(0.9.dp, Color.White.copy(alpha = 0.16f), previewShape),
                ) {
                    if (bgPath.isNotBlank()) {
                        CustomUiWallpaperLayer(
                            path = bgPath,
                            blurDp = bgBlur,
                            dimAlpha = bgDim,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Column(
                        verticalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                    ) {
                        // Sample glowing lyric line
                        Text(
                            text = "Xem trước lời bài hát phát sáng ✨",
                            style = TextStyle(
                                fontFamily = InterFontFamily,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 17.sp,
                                color = Color.White,
                                shadow = if (lyricsGlow > 0.05f) {
                                    Shadow(
                                        color = accent.copy(alpha = (0.85f * lyricsGlow).coerceIn(0f, 1f)),
                                        offset = Offset.Zero,
                                        blurRadius = (24f * lyricsGlow).coerceIn(4f, 40f),
                                    )
                                } else null,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )

                        // Sample Mini-Player + Dock glass preview
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val pillShape = RoundedCornerShape(dockCorner.dp)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .clip(pillShape)
                                    .background(Color(0xFF16161E).copy(alpha = glassOpacity))
                                    .border(0.8.dp, Color.White.copy(alpha = 0.24f), pillShape)
                                    .padding(horizontal = 8.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(RoundedCornerShape((coverCorner * 0.5f).coerceAtMost(15f).dp))
                                        .background(accent),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.music_note),
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = "Mini-Player · ${playerStyle.displayName}",
                                    fontFamily = InterFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = Color.White,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Icon(
                                    painter = painterResource(R.drawable.play_applemusic),
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp),
                                )
                            }

                            Row(
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(42.dp)
                                    .clip(pillShape)
                                    .background(Color(0xFF16161E).copy(alpha = glassOpacity))
                                    .border(0.8.dp, Color.White.copy(alpha = 0.24f), pillShape)
                                    .padding(horizontal = 10.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(accent.copy(alpha = 0.22f))
                                        .padding(horizontal = 14.dp, vertical = 5.dp),
                                ) {
                                    Text(
                                        text = "Trang chủ",
                                        fontFamily = InterFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = accent,
                                    )
                                }
                                Text("Tìm kiếm", fontFamily = InterFontFamily, fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
                                Text("Thư viện", fontFamily = InterFontFamily, fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
                                Text("Cài đặt", fontFamily = InterFontFamily, fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
                            }
                        }
                    }
                }
            }

            // 1. Custom Wallpaper Photo Picker Card
            item(key = "wallpaper_card") {
                CustomizerSectionCard(title = "Ảnh nền giao diện (Thư viện ảnh)") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .border(0.8.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(14.dp)),
                        ) {
                            if (bgPath.isNotBlank()) {
                                AsyncImage(
                                    model = File(bgPath),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                Icon(
                                    painter = painterResource(R.drawable.album),
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.45f),
                                    modifier = Modifier.size(26.dp),
                                )
                            }
                        }
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(50))
                                        .background(accent)
                                        .clickable { imagePicker.launch("image/*") }
                                        .padding(vertical = 9.dp),
                                ) {
                                    Text(
                                        text = if (bgPath.isBlank()) "Chọn ảnh từ máy" else "Đổi ảnh khác",
                                        fontFamily = InterFontFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.5.sp,
                                        color = Color.White,
                                    )
                                }
                                if (bgPath.isNotBlank()) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(50))
                                            .background(Color.White.copy(alpha = 0.12f))
                                            .clickable {
                                                val prev = File(bgPath)
                                                bgPath = ""
                                                UiCustomTheme.customBgPath = null
                                                scope.launch(Dispatchers.IO) { runCatching { prev.delete() } }
                                                Toast.makeText(context, "Đã xóa ảnh nền", Toast.LENGTH_SHORT).show()
                                            }
                                            .padding(horizontal = 14.dp, vertical = 9.dp),
                                    ) {
                                        Text(
                                            text = "Xóa ảnh",
                                            fontFamily = InterFontFamily,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 12.5.sp,
                                            color = Color.White,
                                        )
                                    }
                                }
                            }
                            Text(
                                text = if (bgPath.isBlank()) "Chưa chọn ảnh nền tùy chỉnh" else "Đang dùng ảnh nền từ thư viện",
                                fontFamily = InterFontFamily,
                                fontSize = 11.5.sp,
                                color = Color.White.copy(alpha = 0.58f),
                            )
                        }
                    }

                    if (bgPath.isNotBlank()) {
                        Spacer(Modifier.height(14.dp))
                        CustomizerSwitchRow(
                            label = "Áp dụng cho toàn ứng dụng",
                            checked = applyBgApp,
                            onCheckedChange = {
                                applyBgApp = it
                                UiCustomTheme.applyBgToApp = it
                            },
                        )
                        Spacer(Modifier.height(8.dp))
                        CustomizerSwitchRow(
                            label = "Áp dụng cho trình phát nhạc",
                            checked = applyBgPlayer,
                            onCheckedChange = {
                                applyBgPlayer = it
                                UiCustomTheme.applyBgToPlayer = it
                            },
                        )
                        Spacer(Modifier.height(10.dp))
                        CustomizerSliderRow(
                            label = "Làm mờ ảnh nền (Blur)",
                            valueText = "${bgBlur.roundToInt()} dp",
                            value = bgBlur,
                            range = 0f..24f,
                            accent = accent,
                            onChange = {
                                bgBlur = it
                                UiCustomTheme.bgBlurDp = it
                            },
                        )
                        CustomizerSliderRow(
                            label = "Độ tối phủ nền (dễ đọc chữ)",
                            valueText = "${(bgDim * 100).roundToInt()}%",
                            value = bgDim,
                            range = 0.15f..0.85f,
                            accent = accent,
                            onChange = {
                                bgDim = it
                                UiCustomTheme.bgDimAlpha = it
                            },
                        )
                    }
                }
            }

            // 2. Player Styles Grid
            item(key = "player_styles") {
                CustomizerSectionCard(title = "Giao diện Trình phát (${allStyles.size} kiểu)") {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        allStyles.forEach { (style, label) ->
                            val selected = playerStyle == style
                            Text(
                                text = label,
                                fontFamily = InterFontFamily,
                                fontSize = 13.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) Color.White else Color.White.copy(alpha = 0.74f),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(if (selected) accent else Color.White.copy(alpha = 0.08f))
                                    .clickable { playerStyle = style }
                                    .padding(horizontal = 14.dp, vertical = 9.dp),
                            )
                        }
                    }
                }
            }

            // 3. Accent Color Swatches
            item(key = "accent_colors") {
                CustomizerSectionCard(title = "Màu nhấn toàn ứng dụng") {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        AccentSwatches.forEach { swatch ->
                            val isPicked = accentArgb == swatch.argb
                            val c = Color(swatch.argb)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(if (isPicked) c.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.06f))
                                    .border(
                                        width = if (isPicked) 1.5.dp else 0.7.dp,
                                        color = if (isPicked) c else Color.White.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(50),
                                    )
                                    .clickable {
                                        accentArgb = swatch.argb
                                        UiCustomTheme.accentColor = c
                                    }
                                    .padding(horizontal = 11.dp, vertical = 7.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(c),
                                )
                                Spacer(Modifier.width(7.dp))
                                Text(
                                    text = swatch.name,
                                    fontFamily = InterFontFamily,
                                    fontSize = 12.sp,
                                    fontWeight = if (isPicked) FontWeight.Bold else FontWeight.Medium,
                                    color = Color.White,
                                )
                            }
                        }
                    }
                }
            }

            // 4. Free Geometry & Effects Sliders
            item(key = "sliders") {
                CustomizerSectionCard(title = "Tùy chỉnh tự do Dock, Kính mờ & Lyrics") {
                    CustomizerSliderRow(
                        label = "Bo tròn Dock & Mini-Player",
                        valueText = "${dockCorner.roundToInt()} dp",
                        value = dockCorner,
                        range = 10f..32f,
                        accent = accent,
                        onChange = {
                            dockCorner = it
                            UiCustomTheme.dockCornerDp = it
                        },
                    )
                    CustomizerSliderRow(
                        label = "Độ đậm kính mờ Glass",
                        valueText = "${(glassOpacity * 100).roundToInt()}%",
                        value = glassOpacity,
                        range = 0.35f..0.88f,
                        accent = accent,
                        onChange = {
                            glassOpacity = it
                            UiCustomTheme.glassOpacity = it
                        },
                    )
                    CustomizerSliderRow(
                        label = "Cường độ Glow phát sáng Lyrics",
                        valueText = "${(lyricsGlow * 100).roundToInt()}%",
                        value = lyricsGlow,
                        range = 0f..1.6f,
                        accent = accent,
                        onChange = {
                            lyricsGlow = it
                            UiCustomTheme.lyricsGlowStrength = it
                        },
                    )
                    CustomizerSliderRow(
                        label = "Bo tròn ảnh bìa Player",
                        valueText = "${coverCorner.roundToInt()} dp",
                        value = coverCorner,
                        range = 8f..44f,
                        accent = accent,
                        onChange = {
                            coverCorner = it
                            UiCustomTheme.playerCoverCornerDp = it
                        },
                    )
                }
            }

            // 5. Reset to Default Button
            item(key = "reset") {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable {
                            accentArgb = UiCustomTheme.DefaultAccentArgb
                            dockCorner = UiCustomTheme.DefaultDockCornerDp
                            glassOpacity = UiCustomTheme.DefaultGlassOpacity
                            lyricsGlow = UiCustomTheme.DefaultLyricsGlow
                            coverCorner = UiCustomTheme.DefaultPlayerCoverCornerDp
                            bgBlur = UiCustomTheme.DefaultBgBlurDp
                            bgDim = UiCustomTheme.DefaultBgDimAlpha
                            applyBgApp = true
                            applyBgPlayer = true
                            if (bgPath.isNotBlank()) {
                                val old = File(bgPath)
                                bgPath = ""
                                scope.launch(Dispatchers.IO) { runCatching { old.delete() } }
                            }
                            UiCustomTheme.accentColor = Color(UiCustomTheme.DefaultAccentArgb)
                            UiCustomTheme.dockCornerDp = UiCustomTheme.DefaultDockCornerDp
                            UiCustomTheme.glassOpacity = UiCustomTheme.DefaultGlassOpacity
                            UiCustomTheme.lyricsGlowStrength = UiCustomTheme.DefaultLyricsGlow
                            UiCustomTheme.playerCoverCornerDp = UiCustomTheme.DefaultPlayerCoverCornerDp
                            UiCustomTheme.customBgPath = null
                            UiCustomTheme.bgBlurDp = UiCustomTheme.DefaultBgBlurDp
                            UiCustomTheme.bgDimAlpha = UiCustomTheme.DefaultBgDimAlpha
                            UiCustomTheme.applyBgToApp = true
                            UiCustomTheme.applyBgToPlayer = true
                            Toast.makeText(context, "Đã khôi phục giao diện mặc định", Toast.LENGTH_SHORT).show()
                        }
                        .padding(vertical = 13.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.restore),
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Khôi phục mặc định",
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomizerSectionCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(CardBg)
            .border(0.75.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(22.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            text = title,
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 14.5.sp,
            color = Color.White,
        )
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun CustomizerSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 2.dp),
    ) {
        Text(
            text = label,
            fontFamily = InterFontFamily,
            fontSize = 13.5.sp,
            color = Color.White.copy(alpha = 0.88f),
            modifier = Modifier.weight(1f),
        )
        AppSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun CustomizerSliderRow(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    accent: Color,
    onChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = label,
                fontFamily = InterFontFamily,
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.86f),
            )
            Text(
                text = valueText,
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = accent,
            )
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = accent,
                inactiveTrackColor = Color.White.copy(alpha = 0.18f),
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
