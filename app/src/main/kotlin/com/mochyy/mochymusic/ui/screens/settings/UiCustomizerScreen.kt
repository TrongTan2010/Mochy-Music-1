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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
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
import com.mochyy.mochymusic.ui.component.DefaultPlayerSeekBarSlider
import com.mochyy.mochymusic.ui.component.SettingsItem
import com.mochyy.mochymusic.ui.component.SubSettingsScaffold
import com.mochyy.mochymusic.ui.component.UiCustomTheme
import com.mochyy.mochymusic.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

const val UiCustomizerRoute = "ui_customizer"

private data class AccentSwatch(val name: String, val argb: Int)

private val AccentSwatches = listOf(
    AccentSwatch("Apple Coral", 0xFFFA2D48.toInt()),
    AccentSwatch("Spotify Green", 0xFF1DB954.toInt()),
    AccentSwatch("SoundCloud", 0xFFFF5500.toInt()),
    AccentSwatch("Electric Blue", 0xFF0A84FF.toInt()),
    AccentSwatch("Violet", 0xFFAF52DE.toInt()),
    AccentSwatch("Cyan", 0xFF32D5FF.toInt()),
    AccentSwatch("Amber", 0xFFFFB300.toInt()),
    AccentSwatch("Rose", 0xFFFF375F.toInt()),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UiCustomizerScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

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

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val savedPath = withContext(Dispatchers.IO) {
                runCatching {
                    val outFile = File(context.filesDir, "custom_ui_bg_${System.currentTimeMillis()}.jpg")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        outFile.outputStream().use { output -> input.copyTo(output) }
                    } ?: return@runCatching null
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

    val hasWallpaper = bgPath.isNotBlank()
    val pickedSwatchName = AccentSwatches.firstOrNull { it.argb == accentArgb }?.name ?: "Mặc định"

    SubSettingsScaffold(
        title = "Tùy biến tự do",
        backLabel = stringResource(R.string.customise_title),
        onBack = { navController.navigateUp() },
    ) {
        // 1. Wallpaper
        SettingsSection(
            title = "Ảnh nền",
            items = listOf(
                SettingsItem(
                    title = { Text(if (hasWallpaper) "Đổi ảnh nền" else "Chọn ảnh nền từ máy") },
                    descriptionText = if (hasWallpaper) "Đang dùng ảnh nền từ thư viện" else "Chưa chọn ảnh nền tùy chỉnh",
                    icon = painterResource(R.drawable.image),
                    iconShape = CircleShape,
                    onClick = { imagePicker.launch("image/*") },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (hasWallpaper) {
                                AsyncImage(
                                    model = File(bgPath),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(RoundedCornerShape(10.dp)),
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Icon(
                                painter = painterResource(R.drawable.navigate_next),
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    },
                ),
                switchItem(
                    key = "Custom wallpaper app",
                    title = "Áp dụng cho toàn ứng dụng",
                    description = "Hiển thị ảnh nền phía sau các màn hình chính",
                    icon = painterResource(R.drawable.palette),
                    checked = applyBgApp,
                    onCheckedChange = {
                        applyBgApp = it
                        UiCustomTheme.applyBgToApp = it
                    },
                    subOptionOf = hasWallpaper,
                ),
                switchItem(
                    key = "Custom wallpaper player",
                    title = "Áp dụng cho trình phát nhạc",
                    description = "Hiển thị ảnh nền trong màn hình phát nhạc",
                    icon = painterResource(R.drawable.play),
                    checked = applyBgPlayer,
                    onCheckedChange = {
                        applyBgPlayer = it
                        UiCustomTheme.applyBgToPlayer = it
                    },
                    subOptionOf = hasWallpaper,
                ),
                SettingsItem(
                    title = { ValueTitle("Độ mờ ảnh nền", "${bgBlur.roundToInt()} dp") },
                    description = {
                        DefaultPlayerSeekBarSlider(
                            value = bgBlur.coerceIn(0f, 24f),
                            onValueChange = {
                                bgBlur = it
                                UiCustomTheme.bgBlurDp = it
                            },
                            valueRange = 0f..24f,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    },
                    icon = painterResource(R.drawable.tune),
                    iconShape = CircleShape,
                    isVisible = hasWallpaper,
                    isSubOption = true,
                ),
                SettingsItem(
                    title = { ValueTitle("Độ tối lớp phủ", "${(bgDim * 100).roundToInt()}%") },
                    description = {
                        DefaultPlayerSeekBarSlider(
                            value = bgDim.coerceIn(0.15f, 0.85f),
                            onValueChange = {
                                bgDim = it
                                UiCustomTheme.bgDimAlpha = it
                            },
                            valueRange = 0.15f..0.85f,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    },
                    icon = painterResource(R.drawable.tune),
                    iconShape = CircleShape,
                    isVisible = hasWallpaper,
                    isSubOption = true,
                ),
                SettingsItem(
                    title = { Text("Xóa ảnh nền") },
                    descriptionText = "Quay lại nền đen mặc định",
                    icon = painterResource(R.drawable.delete),
                    iconShape = CircleShape,
                    isVisible = hasWallpaper,
                    isSubOption = true,
                    onClick = {
                        val prev = File(bgPath)
                        bgPath = ""
                        UiCustomTheme.customBgPath = null
                        scope.launch(Dispatchers.IO) { runCatching { prev.delete() } }
                        Toast.makeText(context, "Đã xóa ảnh nền", Toast.LENGTH_SHORT).show()
                    },
                ),
            ),
        )

        // 2. Accent color
        SettingsSection(
            title = "Màu nhấn",
            items = listOf(
                SettingsItem(
                    title = { ValueTitle("Màu nhấn giao diện", pickedSwatchName) },
                    icon = painterResource(R.drawable.palette),
                    iconShape = CircleShape,
                    description = {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp, bottom = 2.dp),
                        ) {
                            AccentSwatches.forEach { swatch ->
                                val selected = accentArgb == swatch.argb
                                val color = Color(swatch.argb)
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .then(
                                            if (selected) {
                                                Modifier.border(2.dp, Color.White, CircleShape)
                                            } else {
                                                Modifier
                                            }
                                        )
                                        .clickable {
                                            accentArgb = swatch.argb
                                            UiCustomTheme.accentColor = color
                                        }
                                        .padding(if (selected) 4.dp else 0.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape)
                                            .background(color),
                                    )
                                }
                            }
                        }
                    },
                ),
            ),
        )

        // 3. Geometry & glass effects
        SettingsSection(
            title = "Giao diện & Hiệu ứng",
            items = listOf(
                SettingsItem(
                    title = { ValueTitle("Bo tròn Dock & Mini-Player", "${dockCorner.roundToInt()} dp") },
                    description = {
                        DefaultPlayerSeekBarSlider(
                            value = dockCorner.coerceIn(10f, 32f),
                            onValueChange = {
                                dockCorner = it
                                UiCustomTheme.dockCornerDp = it
                            },
                            valueRange = 10f..32f,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    },
                    icon = painterResource(R.drawable.tune),
                    iconShape = CircleShape,
                ),
                SettingsItem(
                    title = { ValueTitle("Độ đậm kính mờ", "${(glassOpacity * 100).roundToInt()}%") },
                    description = {
                        DefaultPlayerSeekBarSlider(
                            value = glassOpacity.coerceIn(0.35f, 0.88f),
                            onValueChange = {
                                glassOpacity = it
                                UiCustomTheme.glassOpacity = it
                            },
                            valueRange = 0.35f..0.88f,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    },
                    icon = painterResource(R.drawable.tune),
                    iconShape = CircleShape,
                ),
                SettingsItem(
                    title = { ValueTitle("Độ sáng lời bài hát", "${(lyricsGlow * 100).roundToInt()}%") },
                    description = {
                        DefaultPlayerSeekBarSlider(
                            value = lyricsGlow.coerceIn(0f, 1.6f),
                            onValueChange = {
                                lyricsGlow = it
                                UiCustomTheme.lyricsGlowStrength = it
                            },
                            valueRange = 0f..1.6f,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    },
                    icon = painterResource(R.drawable.lyrics),
                    iconShape = CircleShape,
                ),
                SettingsItem(
                    title = { ValueTitle("Bo tròn ảnh bìa trình phát", "${coverCorner.roundToInt()} dp") },
                    description = {
                        DefaultPlayerSeekBarSlider(
                            value = coverCorner.coerceIn(8f, 44f),
                            onValueChange = {
                                coverCorner = it
                                UiCustomTheme.playerCoverCornerDp = it
                            },
                            valueRange = 8f..44f,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    },
                    icon = painterResource(R.drawable.album),
                    iconShape = CircleShape,
                ),
            ),
        )

        // 4. Reset
        SettingsSection(
            title = null,
            items = listOf(
                SettingsItem(
                    title = { Text("Khôi phục mặc định") },
                    descriptionText = "Đưa ảnh nền, màu nhấn và các thông số về ban đầu",
                    icon = painterResource(R.drawable.restore),
                    iconShape = CircleShape,
                    onClick = {
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
                    },
                ),
            ),
        )

        Spacer(Modifier.height(140.dp))
    }
}
