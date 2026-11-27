package com.mochyy.mochymusic.ui.screens.soundcloud

import android.webkit.CookieManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.mochyy.mochymusic.LocalDatabase
import com.mochyy.mochymusic.LocalPlayerAwareWindowInsets
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.extensions.toMediaItem
import com.mochyy.mochymusic.models.MediaMetadata
import com.mochyy.mochymusic.module.AccountState
import com.mochyy.mochymusic.modules.soundcloud.SoundCloudModule
import com.mochyy.mochymusic.modules.soundcloud.SoundCloudTrackItem
import com.mochyy.mochymusic.modules.soundcloud.SoundCloudUser
import com.mochyy.mochymusic.playback.queues.ListQueue
import com.mochyy.mochymusic.ui.component.LocalZoomOutOverlayState
import com.mochyy.mochymusic.ui.screens.settings.modules.showModuleSignInSheet
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import com.mochyy.mochymusic.viewmodels.ModulesViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val SoundCloudRoute = "soundcloud"

private val SoundCloudOrange = Color(0xFFFF5500)
private val SoundCloudOrangeSoft = Color(0xFFFF7733)
private val CardSurface = Color(0xFF1A1A1E)

@Composable
fun SoundCloudScreen(
    navController: NavController,
    modulesViewModel: ModulesViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val overlay = LocalZoomOutOverlayState.current
    val playerConnection = LocalPlayerConnection.current
    val database = LocalDatabase.current
    val scope = rememberCoroutineScope()

    val scModule = remember(modulesViewModel) {
        modulesViewModel.moduleInstance(SoundCloudModule.Id) as? SoundCloudModule
    }

    val currentPlayingMeta by (playerConnection?.mediaMetadata?.collectAsState() ?: remember { mutableStateOf(null) })

    var isSignedIn by remember { mutableStateOf(scModule?.isUserSignedIn() == true) }
    var user by remember { mutableStateOf<SoundCloudUser?>(scModule?.currentUser()) }
    var authVersion by remember { mutableIntStateOf(0) }

    // 0 = Thịnh hành (Trending), 1 = Đã thích (Likes)
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }

    var trendingTracks by remember { mutableStateOf<List<SoundCloudTrackItem>>(emptyList()) }
    var likedTracks by remember { mutableStateOf<List<SoundCloudTrackItem>>(emptyList()) }
    var searchTracks by remember { mutableStateOf<List<SoundCloudTrackItem>>(emptyList()) }

    var loadingTrending by remember { mutableStateOf(false) }
    var loadingLikes by remember { mutableStateOf(false) }
    var loadingSearch by remember { mutableStateOf(false) }

    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding()

    // Refresh user profile on entry and after login/logout
    LaunchedEffect(scModule, authVersion) {
        val mod = scModule ?: return@LaunchedEffect
        isSignedIn = mod.isUserSignedIn()
        if (isSignedIn) {
            user = mod.currentUser() ?: withContext(Dispatchers.IO) { runCatching { mod.fetchMe() }.getOrNull() }
        } else {
            user = null
            likedTracks = emptyList()
        }
    }

    // Load trending charts
    LaunchedEffect(scModule) {
        val mod = scModule ?: return@LaunchedEffect
        if (trendingTracks.isEmpty()) {
            loadingTrending = true
            trendingTracks = withContext(Dispatchers.IO) {
                runCatching { mod.fetchTrendingTracks(limit = 25) }.getOrDefault(emptyList())
            }
            loadingTrending = false
        }
    }

    // Load user likes when Likes tab selected & signed in
    LaunchedEffect(scModule, selectedTab, isSignedIn, authVersion) {
        val mod = scModule ?: return@LaunchedEffect
        if (selectedTab == 1 && isSignedIn) {
            loadingLikes = true
            likedTracks = withContext(Dispatchers.IO) {
                runCatching { mod.fetchLikedTracks(limit = 40) }.getOrDefault(emptyList())
            }
            loadingLikes = false
        }
    }

    // Debounced SoundCloud catalog search
    LaunchedEffect(scModule, query) {
        val mod = scModule ?: return@LaunchedEffect
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            searchTracks = emptyList()
            loadingSearch = false
            return@LaunchedEffect
        }
        loadingSearch = true
        delay(380)
        searchTracks = withContext(Dispatchers.IO) {
            runCatching { mod.searchCatalog(trimmed, limit = 25) }.getOrDefault(emptyList())
        }
        loadingSearch = false
    }

    fun openSignIn() {
        val mod = scModule ?: return
        overlay.showModuleSignInSheet(mod, modulesViewModel) { state ->
            when (state) {
                is AccountState.SignedIn -> {
                    isSignedIn = mod.isUserSignedIn()
                    user = mod.currentUser()
                    authVersion++
                    Toast.makeText(context, "Đã đăng nhập SoundCloud", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    Toast.makeText(context, "Chưa hoàn tất đăng nhập SoundCloud", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun signOut() {
        val mod = scModule ?: return
        mod.signOutAccount()
        runCatching {
            val cm = CookieManager.getInstance()
            cm.setCookie("https://soundcloud.com", "oauth_token=; Max-Age=0; path=/; domain=.soundcloud.com")
            cm.flush()
        }
        modulesViewModel.refresh()
        isSignedIn = false
        user = null
        likedTracks = emptyList()
        authVersion++
        Toast.makeText(context, "Đã đăng xuất tài khoản SoundCloud", Toast.LENGTH_SHORT).show()
    }

    fun playTrackList(tracks: List<SoundCloudTrackItem>, startIndex: Int, queueTitle: String) {
        val conn = playerConnection ?: return
        if (tracks.isEmpty()) return
        val metadataList = tracks.map { item ->
            MediaMetadata(
                id = "SC_${item.id}",
                title = item.title,
                artists = listOf(MediaMetadata.Artist(id = null, name = item.artist)),
                duration = (item.durationMs / 1000L).toInt().coerceAtLeast(0),
                thumbnailUrl = item.artworkUrl,
            )
        }
        metadataList.getOrNull(startIndex)?.let { chosen ->
            scope.launch(Dispatchers.IO) {
                runCatching { database.query { insert(chosen) } }
            }
        }
        conn.playQueue(
            ListQueue(
                title = queueTitle,
                items = metadataList.map { it.toMediaItem() },
                startIndex = startIndex,
            )
        )
    }

    val isSearching = query.trim().isNotEmpty()
    val displayedTracks = when {
        isSearching -> searchTracks
        selectedTab == 1 -> likedTracks
        else -> trendingTracks
    }
    val isLoading = when {
        isSearching -> loadingSearch
        selectedTab == 1 -> loadingLikes
        else -> loadingTrending
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF281208),
                        Color(0xFF100B09),
                        Color.Black,
                    ),
                )
            ),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = statusTop + 12.dp,
                bottom = bottomInset + 28.dp,
                start = 18.dp,
                end = 18.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
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
                            .background(Color.White.copy(alpha = 0.1f))
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
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                Brush.linearGradient(listOf(SoundCloudOrange, SoundCloudOrangeSoft))
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.radio),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "SoundCloud",
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            color = Color.White,
                        )
                        Text(
                            text = "Nguồn phát nhạc thứ 2 · Trực tiếp & Tài khoản",
                            fontFamily = InterFontFamily,
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            // Account card (Sign-in / Signed-in profile)
            item(key = "account") {
                SoundCloudAccountCard(
                    isSignedIn = isSignedIn,
                    user = user,
                    onSignIn = ::openSignIn,
                    onSignOut = ::signOut,
                )
            }

            // Search bar
            item(key = "search_bar") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.09f))
                        .border(0.75.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.search_outline),
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(19.dp),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text(
                                text = "Tìm bài hát, remix, set nhạc SoundCloud…",
                                fontFamily = InterFontFamily,
                                fontSize = 14.sp,
                                color = Color.White.copy(alpha = 0.45f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                fontFamily = InterFontFamily,
                                fontSize = 14.sp,
                                color = Color.White,
                            ),
                            cursorBrush = SolidColor(SoundCloudOrange),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (query.isNotEmpty()) {
                        Icon(
                            painter = painterResource(R.drawable.close),
                            contentDescription = "Xoá",
                            tint = Color.White.copy(alpha = 0.65f),
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .clickable { query = "" }
                                .padding(2.dp),
                        )
                    }
                }
            }

            // Tab pills when not searching
            if (!isSearching) {
                item(key = "tabs") {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        SoundCloudTabChip(
                            label = "Thịnh hành",
                            iconRes = R.drawable.trending_up,
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            modifier = Modifier.weight(1f),
                        )
                        SoundCloudTabChip(
                            label = "Đã thích",
                            iconRes = R.drawable.favorite,
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            modifier = Modifier.weight(1f),
                        )
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .clickable {
                                    val mod = scModule ?: return@clickable
                                    scope.launch {
                                        if (selectedTab == 0) {
                                            loadingTrending = true
                                            trendingTracks = withContext(Dispatchers.IO) {
                                                runCatching { mod.fetchTrendingTracks(25) }.getOrDefault(emptyList())
                                            }
                                            loadingTrending = false
                                        } else if (isSignedIn) {
                                            loadingLikes = true
                                            likedTracks = withContext(Dispatchers.IO) {
                                                runCatching { mod.fetchLikedTracks(40) }.getOrDefault(emptyList())
                                            }
                                            loadingLikes = false
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.refresh),
                                contentDescription = "Làm mới",
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }

            // Content body
            if (!isSearching && selectedTab == 1 && !isSignedIn) {
                item(key = "likes_signin_cta") {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(22.dp))
                            .background(CardSurface)
                            .padding(horizontal = 20.dp, vertical = 28.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(SoundCloudOrange.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.favorite),
                                contentDescription = null,
                                tint = SoundCloudOrange,
                                modifier = Modifier.size(26.dp),
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Đăng nhập để xem bài đã thích",
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            color = Color.White,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Kết nối tài khoản SoundCloud để đồng bộ danh sách bài hát bạn đã tim và phát trực tiếp ngay trong Mochy.",
                            fontFamily = InterFontFamily,
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.62f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(SoundCloudOrange)
                                .clickable { openSignIn() }
                                .padding(horizontal = 24.dp, vertical = 11.dp),
                        ) {
                            Text(
                                text = "Đăng nhập SoundCloud",
                                fontFamily = InterFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = Color.White,
                            )
                        }
                    }
                }
            } else if (isLoading) {
                item(key = "loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 44.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            color = SoundCloudOrange,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                }
            } else if (displayedTracks.isEmpty()) {
                item(key = "empty") {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.music_note),
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.35f),
                            modifier = Modifier.size(36.dp),
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = when {
                                isSearching -> "Không tìm thấy bài hát phù hợp trên SoundCloud"
                                selectedTab == 1 -> "Chưa có bài hát đã thích nào trên tài khoản SoundCloud"
                                else -> "Chưa tải được bảng xếp hạng SoundCloud"
                            },
                            fontFamily = InterFontFamily,
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.55f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
            } else {
                item(key = "list_header") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 2.dp),
                    ) {
                        Text(
                            text = when {
                                isSearching -> "Kết quả SoundCloud (${displayedTracks.size})"
                                selectedTab == 1 -> "Bài hát đã thích (${displayedTracks.size})"
                                else -> "Thịnh hành trên SoundCloud"
                            },
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.72f),
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "Phát tất cả",
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = SoundCloudOrange,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable {
                                    val title = when {
                                        isSearching -> "SoundCloud · ${query.trim()}"
                                        selectedTab == 1 -> "SoundCloud · Đã thích"
                                        else -> "SoundCloud · Thịnh hành"
                                    }
                                    playTrackList(displayedTracks, 0, title)
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }

                itemsIndexed(
                    items = displayedTracks,
                    key = { index, item -> "${item.id}_$index" },
                ) { index, track ->
                    val isActive = currentPlayingMeta?.id == "SC_${track.id}"
                    SoundCloudTrackRow(
                        track = track,
                        isActive = isActive,
                        onClick = {
                            val title = when {
                                isSearching -> "SoundCloud · ${query.trim()}"
                                selectedTab == 1 -> "SoundCloud · Đã thích"
                                else -> "SoundCloud · Thịnh hành"
                            }
                            playTrackList(displayedTracks, index, title)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SoundCloudAccountCard(
    isSignedIn: Boolean,
    user: SoundCloudUser?,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(CardSurface)
            .border(
                width = 0.8.dp,
                color = if (isSignedIn) SoundCloudOrange.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(20.dp),
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(SoundCloudOrange.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center,
        ) {
            if (isSignedIn && !user?.avatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = user?.avatarUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.person),
                    contentDescription = null,
                    tint = SoundCloudOrange,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (isSignedIn) {
                    user?.username?.let { "@$it" } ?: "Đã đăng nhập SoundCloud"
                } else {
                    "Tài khoản SoundCloud"
                },
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (isSignedIn) {
                    "Đã đồng bộ bài đã thích & phát trực tiếp"
                } else {
                    "Đăng nhập để đồng bộ thư viện đã thích"
                },
                fontFamily = InterFontFamily,
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        if (isSignedIn) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.1f))
                    .clickable(onClick = onSignOut)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.logout),
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(15.dp),
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "Đăng xuất",
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = Color.White,
                )
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(SoundCloudOrange)
                    .clickable(onClick = onSignIn)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.login),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(15.dp),
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "Đăng nhập",
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = Color.White,
                )
            }
        }
    }
}

@Composable
private fun SoundCloudTabChip(
    label: String,
    iconRes: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) SoundCloudOrange else Color.White.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = if (selected) Color.White else Color.White.copy(alpha = 0.72f),
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(7.dp))
        Text(
            text = label,
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            color = if (selected) Color.White else Color.White.copy(alpha = 0.78f),
            maxLines = 1,
        )
    }
}

@Composable
private fun SoundCloudTrackRow(
    track: SoundCloudTrackItem,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isActive) SoundCloudOrange.copy(alpha = 0.16f)
                else Color.White.copy(alpha = 0.045f)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            if (!track.artworkUrl.isNullOrBlank()) {
                AsyncImage(
                    model = track.artworkUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.music_note),
                    contentDescription = null,
                    tint = SoundCloudOrange.copy(alpha = 0.85f),
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.5.sp,
                color = if (isActive) SoundCloudOrangeSoft else Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = track.artist,
                fontFamily = InterFontFamily,
                fontSize = 12.5.sp,
                color = Color.White.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = formatDuration(track.durationMs),
            fontFamily = InterFontFamily,
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.5f),
        )
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}
