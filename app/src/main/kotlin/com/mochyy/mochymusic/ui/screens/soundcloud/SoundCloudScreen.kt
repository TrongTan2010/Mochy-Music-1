package com.mochyy.mochymusic.ui.screens.soundcloud

import android.webkit.CookieManager
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.unit.em
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
import com.mochyy.mochymusic.ui.component.UiCustomTheme
import com.mochyy.mochymusic.ui.screens.settings.modules.showModuleSignInSheet
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import com.mochyy.mochymusic.viewmodels.ModulesViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val SoundCloudRoute = "soundcloud"

private val SoundCloudOrange = Color(0xFFFF5500)
private val SoundCloudOrangeSoft = Color(0xFFFF7A33)
private val CoverCardSize = 156.dp
private val CoverCardShape = RoundedCornerShape(22.dp)
private val HorizontalInset = 20.dp

private data class SoundCloudShelf(
    val id: String,
    val subtitle: String,
    val title: String,
    val tracks: List<SoundCloudTrackItem>,
)

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

    val currentPlayingMeta = playerConnection?.mediaMetadata?.collectAsState()?.value

    var isSignedIn by remember { mutableStateOf(scModule?.isUserSignedIn() == true) }
    var user by remember { mutableStateOf<SoundCloudUser?>(scModule?.currentUser()) }
    var authVersion by remember { mutableIntStateOf(0) }

    var showSearch by rememberSaveable { mutableStateOf(false) }
    var showAccountMenu by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }

    var shelves by remember { mutableStateOf<List<SoundCloudShelf>>(emptyList()) }
    var likedTracks by remember { mutableStateOf<List<SoundCloudTrackItem>>(emptyList()) }
    var searchTracks by remember { mutableStateOf<List<SoundCloudTrackItem>>(emptyList()) }

    var loadingShelves by remember { mutableStateOf(true) }
    var loadingSearch by remember { mutableStateOf(false) }

    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding()
    val hasCustomAppBg = UiCustomTheme.applyBgToApp && !UiCustomTheme.customBgPath.isNullOrBlank()

    // Refresh user profile + liked shelf on entry and after auth change
    LaunchedEffect(scModule, authVersion) {
        val mod = scModule ?: return@LaunchedEffect
        isSignedIn = mod.isUserSignedIn()
        if (isSignedIn) {
            val loadedUser = mod.currentUser() ?: withContext(Dispatchers.IO) { runCatching { mod.fetchMe() }.getOrNull() }
            user = loadedUser
            likedTracks = withContext(Dispatchers.IO) {
                runCatching { mod.fetchLikedTracks(limit = 20) }.getOrDefault(emptyList())
            }
        } else {
            user = null
            likedTracks = emptyList()
        }
    }

    // Load a few focused Home-style SoundCloud shelves in parallel
    LaunchedEffect(scModule) {
        val mod = scModule ?: return@LaunchedEffect
        if (shelves.isNotEmpty()) {
            loadingShelves = false
            return@LaunchedEffect
        }
        loadingShelves = true
        val loaded = withContext(Dispatchers.IO) {
            coroutineScope {
                val trendingDef = async { runCatching { mod.fetchTrendingTracks(limit = 14) }.getOrDefault(emptyList()) }
                val remixDef = async { runCatching { mod.fetchGenreTracks(null, "nhac tre remix viet", limit = 14) }.getOrDefault(emptyList()) }
                val hiphopDef = async { runCatching { mod.fetchGenreTracks("hiphoprap", "hip hop rap", limit = 14) }.getOrDefault(emptyList()) }
                val edmDef = async { runCatching { mod.fetchGenreTracks("danceedm", "electronic dance edm", limit = 14) }.getOrDefault(emptyList()) }

                listOfNotNull(
                    trendingDef.await().takeIf { it.isNotEmpty() }?.let {
                        SoundCloudShelf("trending", "Bảng xếp hạng", "Thịnh hành", it)
                    },
                    remixDef.await().takeIf { it.isNotEmpty() }?.let {
                        SoundCloudShelf("remix_vn", "Thử điều gì đó mới", "Remix & V-Pop", it)
                    },
                    hiphopDef.await().takeIf { it.isNotEmpty() }?.let {
                        SoundCloudShelf("hiphop", "Thử điều gì đó mới", "Hip-hop", it)
                    },
                    edmDef.await().takeIf { it.isNotEmpty() }?.let {
                        SoundCloudShelf("edm", "Thử điều gì đó mới", "Dance & Electronic", it)
                    },
                )
            }
        }
        shelves = loaded
        loadingShelves = false
    }

    // Debounced search
    LaunchedEffect(scModule, query) {
        val mod = scModule ?: return@LaunchedEffect
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            searchTracks = emptyList()
            loadingSearch = false
            return@LaunchedEffect
        }
        loadingSearch = true
        delay(360)
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
        showAccountMenu = false
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

    val isSearching = showSearch && query.trim().isNotEmpty()
    val allShelves = remember(shelves, likedTracks, isSignedIn) {
        buildList {
            if (isSignedIn && likedTracks.isNotEmpty()) {
                add(SoundCloudShelf("liked", "Thư viện của bạn", "Đã thích", likedTracks))
            }
            addAll(shelves)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(if (hasCustomAppBg) Modifier else Modifier.background(Color.Black)),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = statusTop + 12.dp,
                bottom = bottomInset + 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(26.dp),
        ) {
            // Home-style Top Header: Back + Orange Mark + Bold "SoundCloud" + Search & Account Orbs
            item(key = "sc_top_bar") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = HorizontalInset),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.09f))
                                    .clickable { navController.navigateUp() },
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.arrow_back),
                                    contentDescription = "Quay lại",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            Icon(
                                painter = painterResource(R.drawable.radio),
                                contentDescription = null,
                                tint = SoundCloudOrange,
                                modifier = Modifier.size(26.dp),
                            )
                            Text(
                                text = "SoundCloud",
                                style = TextStyle(
                                    fontFamily = InterFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 28.sp,
                                    letterSpacing = (-0.04).em,
                                ),
                                color = Color.White,
                                maxLines = 1,
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            // Search orb
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (showSearch) SoundCloudOrange.copy(alpha = 0.28f)
                                        else Color.White.copy(alpha = 0.11f)
                                    )
                                    .border(
                                        width = 0.8.dp,
                                        color = if (showSearch) SoundCloudOrange else Color.White.copy(alpha = 0.12f),
                                        shape = CircleShape,
                                    )
                                    .clickable {
                                        showSearch = !showSearch
                                        if (!showSearch) query = ""
                                    },
                            ) {
                                Icon(
                                    painter = painterResource(if (showSearch) R.drawable.close else R.drawable.search_outline),
                                    contentDescription = "Tìm kiếm SoundCloud",
                                    tint = if (showSearch) SoundCloudOrangeSoft else Color.White,
                                    modifier = Modifier.size(18.dp),
                                )
                            }

                            // Account / Sign-in orb
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSignedIn) SoundCloudOrange.copy(alpha = 0.25f)
                                        else Color.White.copy(alpha = 0.11f)
                                    )
                                    .border(
                                        width = 0.8.dp,
                                        color = if (isSignedIn) SoundCloudOrange else Color.White.copy(alpha = 0.14f),
                                        shape = CircleShape,
                                    )
                                    .clickable {
                                        if (isSignedIn) showAccountMenu = !showAccountMenu else openSignIn()
                                    },
                            ) {
                                if (isSignedIn && !user?.avatarUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = user?.avatarUrl,
                                        contentDescription = "Tài khoản SoundCloud",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                } else {
                                    Icon(
                                        painter = painterResource(R.drawable.person),
                                        contentDescription = "Đăng nhập SoundCloud",
                                        tint = if (isSignedIn) SoundCloudOrangeSoft else Color.White.copy(alpha = 0.85f),
                                        modifier = Modifier.size(19.dp),
                                    )
                                }
                            }
                        }
                    }

                    // Compact account pill when signed out (or sign-out toggle when avatar tapped)
                    AnimatedVisibility(visible = !isSignedIn || showAccountMenu) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 14.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF171413))
                                .border(0.75.dp, SoundCloudOrange.copy(alpha = 0.30f), RoundedCornerShape(16.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isSignedIn) {
                                        user?.username?.let { "@$it" } ?: "Đã đăng nhập SoundCloud"
                                    } else {
                                        "Đăng nhập SoundCloud"
                                    },
                                    fontFamily = InterFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.5.sp,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = if (isSignedIn) "Đồng bộ bài đã thích từ tài khoản của bạn" else "Đồng bộ mục Bài hát đã thích",
                                    fontFamily = InterFontFamily,
                                    fontSize = 11.5.sp,
                                    color = Color.White.copy(alpha = 0.56f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = if (isSignedIn) "Đăng xuất" else "Đăng nhập",
                                fontFamily = InterFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                color = Color.White,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(if (isSignedIn) Color.White.copy(alpha = 0.14f) else SoundCloudOrange)
                                    .clickable { if (isSignedIn) signOut() else openSignIn() }
                                    .padding(horizontal = 13.dp, vertical = 7.dp),
                            )
                        }
                    }

                    // Expandable search bar
                    AnimatedVisibility(visible = showSearch) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                                .height(46.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White.copy(alpha = 0.09f))
                                .border(0.75.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(16.dp))
                                .padding(horizontal = 14.dp),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.search_outline),
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Box(modifier = Modifier.weight(1f)) {
                                if (query.isEmpty()) {
                                    Text(
                                        text = "Tìm bài hát, remix trên SoundCloud…",
                                        fontFamily = InterFontFamily,
                                        fontSize = 14.sp,
                                        color = Color.White.copy(alpha = 0.45f),
                                        maxLines = 1,
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
                                        .size(18.dp)
                                        .clickable { query = "" },
                                )
                            }
                        }
                    }
                }
            }

            if (isSearching) {
                if (loadingSearch) {
                    item(key = "search_loading") {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                        ) {
                            CircularProgressIndicator(color = SoundCloudOrange, strokeWidth = 2.5.dp, modifier = Modifier.size(28.dp))
                        }
                    }
                } else if (searchTracks.isEmpty()) {
                    item(key = "search_empty") {
                        Text(
                            text = "Không tìm thấy kết quả trên SoundCloud",
                            fontFamily = InterFontFamily,
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.55f),
                            modifier = Modifier.padding(horizontal = HorizontalInset, vertical = 20.dp),
                        )
                    }
                } else {
                    itemsIndexed(
                        items = searchTracks,
                        key = { idx, item -> "search_${item.id}_$idx" },
                    ) { idx, track ->
                        val isActive = currentPlayingMeta?.id == "SC_${track.id}"
                        SoundCloudSearchRow(
                            track = track,
                            isActive = isActive,
                            onClick = { playTrackList(searchTracks, idx, "SoundCloud · ${query.trim()}") },
                            modifier = Modifier.padding(horizontal = HorizontalInset),
                        )
                    }
                }
            } else if (loadingShelves && allShelves.isEmpty()) {
                // Clean Home-style skeleton shelves while loading
                item(key = "shelves_loading") {
                    Column(verticalArrangement = Arrangement.spacedBy(26.dp)) {
                        repeat(3) {
                            Column {
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = HorizontalInset)
                                        .width(120.dp)
                                        .height(14.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color.White.copy(alpha = 0.08f)),
                                )
                                Spacer(Modifier.height(6.dp))
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = HorizontalInset)
                                        .width(170.dp)
                                        .height(22.dp)
                                        .clip(RoundedCornerShape(7.dp))
                                        .background(Color.White.copy(alpha = 0.12f)),
                                )
                                Spacer(Modifier.height(12.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    modifier = Modifier.padding(start = HorizontalInset),
                                ) {
                                    repeat(3) {
                                        Box(
                                            modifier = Modifier
                                                .size(CoverCardSize)
                                                .clip(CoverCardShape)
                                                .background(Color(0xFF1F1F22)),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Home-style horizontal rounded-cover shelves
                allShelves.forEach { shelf ->
                    item(key = "shelf_${shelf.id}", contentType = "sc_shelf") {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Subtitle + Bold Title + Chevron >
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { playTrackList(shelf.tracks, 0, "SoundCloud · ${shelf.title}") }
                                    .padding(horizontal = HorizontalInset),
                            ) {
                                Column {
                                    Text(
                                        text = shelf.subtitle,
                                        fontFamily = InterFontFamily,
                                        fontSize = 13.sp,
                                        color = Color.White.copy(alpha = 0.56f),
                                        maxLines = 1,
                                    )
                                    Spacer(Modifier.height(1.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = shelf.title,
                                            fontFamily = InterFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 20.sp,
                                            color = Color.White,
                                            maxLines = 1,
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Icon(
                                            painter = painterResource(R.drawable.navigate_next),
                                            contentDescription = null,
                                            tint = Color.White.copy(alpha = 0.65f),
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(12.dp))

                            LazyRow(
                                contentPadding = PaddingValues(horizontal = HorizontalInset),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                itemsIndexed(
                                    items = shelf.tracks,
                                    key = { idx, item -> "${shelf.id}_${item.id}_$idx" },
                                ) { idx, track ->
                                    val isActive = currentPlayingMeta?.id == "SC_${track.id}"
                                    SoundCloudHomeCoverCard(
                                        track = track,
                                        isActive = isActive,
                                        onClick = { playTrackList(shelf.tracks, idx, "SoundCloud · ${shelf.title}") },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SoundCloudHomeCoverCard(
    track: SoundCloudTrackItem,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(CoverCardSize)
            .clickable(onClick = onClick),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(CoverCardSize)
                .clip(CoverCardShape)
                .background(Color(0xFF262629))
                .then(
                    if (isActive) Modifier.border(2.dp, SoundCloudOrange, CoverCardShape)
                    else Modifier
                ),
        ) {
            if (!track.artworkUrl.isNullOrBlank()) {
                AsyncImage(
                    model = track.artworkUrl,
                    contentDescription = track.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.music_note),
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.35f),
                    modifier = Modifier.size(44.dp),
                )
            }
            if (isActive) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.6f to Color.Black.copy(alpha = 0.18f),
                                1f to Color.Black.copy(alpha = 0.55f),
                            )
                        ),
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp)
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(SoundCloudOrange),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.play_applemusic),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = track.title,
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.5.sp,
            color = if (isActive) SoundCloudOrangeSoft else Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = track.artist,
            fontFamily = InterFontFamily,
            fontSize = 12.5.sp,
            color = Color.White.copy(alpha = 0.54f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SoundCloudSearchRow(
    track: SoundCloudTrackItem,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isActive) SoundCloudOrange.copy(alpha = 0.16f)
                else Color.White.copy(alpha = 0.05f)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF262629)),
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
        Spacer(Modifier.width(12.dp))
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
            Spacer(Modifier.height(2.dp))
            Text(
                text = track.artist,
                fontFamily = InterFontFamily,
                fontSize = 12.5.sp,
                color = Color.White.copy(alpha = 0.56f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
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
