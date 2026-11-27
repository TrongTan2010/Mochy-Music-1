// AppNavigation.kt

package com.mochyy.mochymusic.ui.component

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.sp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.mochyy.mochymusic.ui.screens.Screens
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.size
import com.mochyy.mochymusic.LocalGlassState

@Immutable
private data class NavItemState(
    val isSelected: Boolean,
    val iconRes: Int
)

/**
 * Navigates to a top-level tab exactly the way the navigation bar does.
 *
 * Anything that jumps to a tab from inside another tab MUST use this rather than a plain
 * `navigate(route)`, and the reason is subtle enough to be worth spelling out.
 *
 * A plain `navigate("settings")` from the profile menu pushes Settings on top of the *Home* tab's
 * stack, because that is where you were standing. Tapping Home in the bar then runs
 * `popUpTo(startDestination) { saveState = true }`, which sweeps that pushed Settings entry into
 * the state saved *for Home* — and `restoreState = true` on the very same call immediately puts it
 * back. The result is that tapping Home lands you on Settings, which is precisely the bug this
 * fixes, and it looks like the bar is ignoring you when in fact it is faithfully restoring a stack
 * that Settings should never have been part of.
 *
 * Going through here instead means a tab is always entered *as* a tab, so it never becomes a child
 * of whichever tab happened to be on screen when you left.
 */
fun NavController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * a tap on a tab. another tab: switch to it, back on the page you left it on. the tab you're in:
 * from one of its pages, back to the tab's own page (as every tab bar does); already there, the
 * page scrolls to its top.
 */
fun NavController.onTabTapped(screen: Screens, isSelectedTab: Boolean, scrollToTop: () -> Unit) {
    if (!isSelectedTab) {
        navigateToTab(screen.route)
        return
    }
    if (currentDestination?.route == screen.route) {
        currentBackStackEntry?.savedStateHandle?.set("scrollToTop", true)
        scrollToTop()
        return
    }
    if (!popBackStack(screen.route, inclusive = false)) navigateToTab(screen.route)
}

@Stable
private fun isRouteSelected(currentRoute: String?, screenRoute: String, navigationItems: List<Screens>): Boolean {
    if (currentRoute == null) return false
    if (currentRoute == screenRoute) return true
    return navigationItems.any { it.route == screenRoute } &&
           currentRoute.startsWith("$screenRoute/")
}

private val RouteOwners: List<Pair<String, String>> = listOf(
    "search/" to "search_input",
    "genre/" to "search_input",
    "youtube_browse/" to "search_input",
    "browse/" to "search_input",
    "library/" to "library",
    "cipher_settings" to "settings",
    "playback_diagnostics" to "settings",
    "mochy_settings" to "settings",
    "general_settings" to "settings",
    "playback_settings" to "settings",
    "now_playing_settings" to "settings",
    "experimental_settings" to "settings",
    "developer_space" to "settings",
    "developer_playground" to "settings",
    "storage_settings" to "settings",
    "import_sync" to "settings",
    "import_providers" to "settings",
    "import_tunemymusic" to "settings",
    "import_progress" to "settings",
    "youtube_sync" to "settings",
    "youtube_login" to "settings",
    "account_import/" to "settings",
    "equalizer" to "settings",
)

private fun owningTabRoute(currentRoute: String?, navigationItems: List<Screens>): String? {
    if (currentRoute == null) return null
    navigationItems.firstOrNull { isRouteSelected(currentRoute, it.route, navigationItems) }
        ?.let { return it.route }
    RouteOwners.firstOrNull { (prefix, _) ->
        currentRoute == prefix || currentRoute.startsWith(prefix)
    }?.let { return it.second }

    // Catch-all so RouteOwners does not have to stay exhaustive: every settings sub-screen is
    // owned by the settings tab without anyone having to list it. Only a fallback now; the back
    // stack decides the tab whenever it has a tab page in it (see owningTab).
    if (currentRoute.endsWith("_settings") || currentRoute.startsWith("settings")) return "settings"
    return null
}

/**
 * the tab the page on screen belongs to: the nearest tab page under it in the back stack.
 *
 * the bar used to remember the last tab it could name from the current route alone, and keep it
 * for routes no tab claims (albums, playlists, artists). that went wrong two ways: switching back
 * to a tab restores the page you left it on, so coming back to the Library's album kept Home lit;
 * and the remembered value was dropped whenever the bar left the screen (the player's full view,
 * the recap), so it came back as Home. the back stack always knows: a tab is entered with
 * [navigateToTab], so every page sits above the tab page it was opened from.
 */
fun owningTab(backStack: List<NavBackStackEntry>, navigationItems: List<Screens>): String {
    val tabRoutes = navigationItems.mapTo(HashSet()) { it.route }
    backStack.asReversed().firstOrNull { it.destination.route in tabRoutes }
        ?.destination?.route
        ?.let { return it }
    // a stack without a tab page under it (a deep link): go by the route, else the first tab
    return owningTabRoute(backStack.lastOrNull()?.destination?.route, navigationItems)
        ?: navigationItems.firstOrNull()?.route.orEmpty()
}

/**
 * which tab each page belongs to, kept by entry so a page that has already left the stack (the
 * one a tab switch saved away) can still be placed. the tab slide reads this: an album open in
 * the Library slides like the Library, not like a page no tab owns.
 */
class TabOwnership(private val navigationItems: List<Screens>) {
    private val tabRoutes = navigationItems.map { it.route }
    private val owners = HashMap<String, String>()

    fun update(backStack: List<NavBackStackEntry>) {
        var owner: String? = null
        backStack.forEach { entry ->
            val route = entry.destination.route
            if (route != null && route in tabRoutes) owner = route
            owner?.let { owners[entry.id] = it }
        }
        // saved tab stacks keep their entries' ids, so only trim when it has clearly grown stale
        if (owners.size > 400) {
            val live = backStack.mapTo(HashSet()) { it.id }
            owners.keys.retainAll(live)
        }
    }

    /** the tab's position in the bar, or -1 when nothing places it */
    fun indexOf(entry: NavBackStackEntry, backStack: List<NavBackStackEntry>): Int {
        update(backStack)
        val owner = owners[entry.id] ?: entry.destination.route?.takeIf { it in tabRoutes }
            ?: owningTabRoute(entry.destination.route, navigationItems)
        return tabRoutes.indexOf(owner)
    }
}

/** The Apple Music iOS 18 floating pill dock height and capsule shape. */
internal val NavBarHeight = 64.dp
internal val NavBarShape = RoundedCornerShape(50)

/** How bright an unlit tab's icon and label are. */
private const val UnlitIconAlpha = 0.64f

@Composable
fun AppNavigationBar(
    navigationItems: List<Screens>,
    currentRoute: String?,
    /** from [owningTab]: the tab the page on screen belongs to */
    selectedTabRoute: String,
    onItemClick: (Screens, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    pureBlack: Boolean = false,
    slimNav: Boolean = false,
    /** a second quick tap on Search: straight into typing */
    onSearchDoubleTap: (() -> Unit)? = null,
    /** "module/page" while one of a module's pages is on screen, so a look's bar can light its tab */
    currentPage: String? = null,
    /** opens a look's own tab page */
    onOpenRoute: (String) -> Unit = {},
    /** the player is over the app, so a look's moving bar can rest */
    obscured: () -> Boolean = { false },
    /** ColorOS 17 / Apple Music iOS 26 scroll morph progress: 0f = full 4-tab capsule, 1f = collapsed orbs */
    collapseProgress: () -> Float = { 0f },
    /** Tapping the collapsed left Home/Dock orb expands the 4-tab dock back open */
    onExpandDock: () -> Unit = {},
    /** When true, scrolling down shows [Home Orb] [Music Pill] [Search Orb]; when false, [Dock Orb] [Music Pill] */
    showCollapsedSearch: Boolean = true,
    /** Tapping the collapsed right Search orb jumps directly into Search */
    onSearchOrbClick: () -> Unit = {},
) {
    // a look with a nav bar of its own draws it in this one's place
    val moduleBar = com.mochyy.mochymusic.modules.skin.ActiveSkin.bar
    if (moduleBar != null) {
        ModuleNavBar(
            active = moduleBar,
            navigationItems = navigationItems,
            selectedTabRoute = selectedTabRoute,
            currentPage = currentPage,
            onItemClick = onItemClick,
            onOpenRoute = onOpenRoute,
            onSearchDoubleTap = onSearchDoubleTap,
            pureBlack = pureBlack,
            obscured = obscured,
            modifier = modifier,
        )
        return
    }
    val glassState = LocalGlassState.current ?: remember { GlassState() }
    val accentWash = rememberChromeAccentWash()
    val selectedIndex = navigationItems.indexOfFirst { it.route == selectedTabRoute }
    val tabCount = navigationItems.size
    val pill = rememberNavPill(selectedIndex)
    // a look's glass can draw its own edge and its own pill, and then mochy leaves its hairline, shadow or highlight out
    val moduleGlass = rememberChromeModuleGlass()
    val density = LocalDensity.current
    val glassPill = remember(pill, tabCount, density) {
        { bar: Size -> pill.forGlass(bar, with(density) { NavRowPadding.toPx() }, tabCount, density) }
    }
    val leftOrbScreen = if (showCollapsedSearch) {
        Screens.Home
    } else {
        navigationItems.getOrNull(selectedIndex.coerceAtLeast(0)) ?: Screens.Home
    }
    val orbIconPainter = coil3.compose.rememberAsyncImagePainter(model = leftOrbScreen.iconIdActive)
    val searchOrbPainter = coil3.compose.rememberAsyncImagePainter(model = Screens.Search.iconIdActive)
    val orbClickable by remember(collapseProgress) {
        androidx.compose.runtime.derivedStateOf { collapseProgress() >= 0.45f }
    }
    val searchOrbMounted by remember(collapseProgress, showCollapsedSearch) {
        androidx.compose.runtime.derivedStateOf { showCollapsedSearch && collapseProgress() > 0.25f }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .align(Alignment.TopStart)
                // Morphs layout bounds smoothly between full-width 64dp dock and 54dp circular Home orb on the left.
                // Using exact morphed width ensures touches to the right reach the adjacent compact Mini-Player!
                .layout { measurable, constraints ->
                    val c = collapseProgress().coerceIn(0f, 1f)
                    val fullW = constraints.maxWidth
                    val fullH = NavBarHeight.roundToPx()
                    val orbPx = DockOrbSize.roundToPx()
                    val targetW = (fullW + (orbPx - fullW) * c).toInt().coerceIn(orbPx, fullW.coerceAtLeast(orbPx))
                    val targetH = (fullH + (orbPx - fullH) * c).toInt().coerceIn(orbPx, fullH.coerceAtLeast(orbPx))
                    val placeable = measurable.measure(Constraints.fixed(targetW, targetH))
                    val topOffset = ((fullH - targetH) / 2f).toInt()
                    layout(targetW, fullH) {
                        placeable.place(0, topOffset)
                    }
                }
                .press3D(maxTilt = 4f, pressedScale = 0.985f)
                .then(if (moduleGlass?.shadow == false) Modifier else Modifier.dropShadow(NavBarShape, ChromeShadow))
                .clip(NavBarShape)
                .drawWithContent {
                    drawContent()
                    if (moduleGlass?.outline != false) drawChromeOutline(Offset.Zero, size, cornerRadius = size.height / 2f)
                }
        ) {
            FrostedChromeGlass(
                state = glassState,
                pureBlack = pureBlack,
                accentWash = accentWash,
                modifier = Modifier.fillMaxSize(),
                pill = glassPill,
            )

            // Full 4-tab row: smoothly fades & contracts out as the dock morphs into the left Orb
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val c = collapseProgress().coerceIn(0f, 1f)
                        alpha = (1f - c * 2.4f).coerceIn(0f, 1f)
                        val s = 1f - 0.14f * c
                        scaleX = s
                        scaleY = s
                    }
                    .padding(horizontal = NavRowPadding)
                    .navHighlight(pill, tabCount) { moduleGlass?.drawsPill != true && collapseProgress() < 0.35f }
                    .navPillDrag(pill, tabCount, selectedIndex) { index ->
                        if (collapseProgress() < 0.45f) {
                            navigationItems.getOrNull(index)?.let { onItemClick(it, index == selectedIndex) }
                        }
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                navigationItems.forEach { screen ->
                    // lit = the tab this page belongs to. what a tap on it does (scroll to the top
                    // on the tab's own page, back to that page from deeper in) is the caller's call
                    NavTab(
                        screen = screen,
                        isSelected = screen.route == selectedTabRoute,
                        onItemClick = { s, selected ->
                            if (collapseProgress() < 0.45f) onItemClick(s, selected)
                        },
                        onDoubleTap = onSearchDoubleTap.takeIf { screen == Screens.Search },
                        showLabel = !slimNav,
                    )
                }
            }

            // Collapsed floating left Home/Dock Orb content with soft Luminous Coral glow
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val c = collapseProgress().coerceIn(0f, 1f)
                        val vis = ((c - 0.35f) / 0.65f).coerceIn(0f, 1f)
                        alpha = vis
                        val s = 0.65f + 0.35f * vis
                        scaleX = s
                        scaleY = s
                    }
                    .drawBehind {
                        val c = collapseProgress().coerceIn(0f, 1f)
                        if (c > 0.35f) {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        AppleMusicAccent.copy(alpha = 0.28f * c),
                                        Color.Transparent,
                                    ),
                                    center = center,
                                    radius = size.minDimension * 0.52f,
                                )
                            )
                        }
                    }
                    .then(
                        if (orbClickable) {
                            Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onExpandDock,
                            )
                        } else Modifier
                    ),
            ) {
                Icon(
                    painter = orbIconPainter,
                    contentDescription = "Trang chủ",
                    tint = AppleMusicAccent,
                    modifier = Modifier.size(23.dp),
                )
            }
        }

        // Optional right-side circular Search Orb (Apple Music iOS 26 3-slot collapsed bar: Home • Music • Search)
        if (searchOrbMounted) {
            val isOnSearch = selectedTabRoute == Screens.Search.route
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))
                    .align(Alignment.TopEnd)
                    .padding(end = 20.dp, top = 8.dp + (NavBarHeight - DockOrbSize) / 2)
                    .size(DockOrbSize)
                    .graphicsLayer {
                        val c = collapseProgress().coerceIn(0f, 1f)
                        val vis = ((c - 0.25f) / 0.75f).coerceIn(0f, 1f)
                        alpha = vis
                        val s = 0.55f + 0.45f * vis
                        scaleX = s
                        scaleY = s
                    }
                    .press3D(maxTilt = 4f, pressedScale = 0.94f)
                    .then(if (moduleGlass?.shadow == false) Modifier else Modifier.dropShadow(NavBarShape, ChromeShadow))
                    .clip(NavBarShape)
                    .drawWithContent {
                        drawContent()
                        if (moduleGlass?.outline != false) drawChromeOutline(Offset.Zero, size, cornerRadius = size.height / 2f)
                    }
                    .then(
                        if (orbClickable) {
                            Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onSearchOrbClick,
                            )
                        } else Modifier
                    ),
            ) {
                FrostedChromeGlass(
                    state = glassState,
                    pureBlack = pureBlack,
                    accentWash = accentWash,
                    modifier = Modifier.fillMaxSize(),
                )
                Icon(
                    painter = searchOrbPainter,
                    contentDescription = "Tìm kiếm",
                    tint = if (isOnSearch) AppleMusicAccent else Color.White.copy(alpha = 0.92f),
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

/**
 * A tab's tap, or with [onDoubleTap] a second tap close behind the first: the first still goes to
 * the tab at once, the second does [onDoubleTap] instead. [lastTap] holds the time of the one before.
 */
internal fun tabTapped(lastTap: LongArray, doubleTapTimeoutMillis: Long, onDoubleTap: (() -> Unit)?, onTap: () -> Unit) {
    val now = android.os.SystemClock.uptimeMillis()
    if (onDoubleTap != null && now - lastTap[0] < doubleTapTimeoutMillis) {
        lastTap[0] = 0L
        onDoubleTap()
    } else {
        lastTap[0] = now
        onTap()
    }
}

/**
 * One tab of the Apple Music Liquid Glass floating dock. Its icon swaps to the filled one,
 * scales up softly, and glows in Apple Music crimson-pink alongside its label when active.
 */
@Composable
internal fun RowScope.NavTab(
    screen: Screens,
    isSelected: Boolean,
    onItemClick: (Screens, Boolean) -> Unit,
    onDoubleTap: (() -> Unit)?,
    showLabel: Boolean = true,
) {
    val viewConfiguration = LocalViewConfiguration.current
    val interactionSource = remember { MutableInteractionSource() }
    val lastTap = remember { longArrayOf(0L) }
    val lit = animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
        label = "nav_tab_lit"
    )

    // Both icons stay loaded, so lighting a tab never shows an empty frame while its filled icon loads.
    val unlitIcon = coil3.compose.rememberAsyncImagePainter(model = screen.iconIdInactive)
    val litIcon = coil3.compose.rememberAsyncImagePainter(model = screen.iconIdActive)
    val iconSize = if (showLabel) {
        if (screen == Screens.Home || screen == Screens.Search) 20.dp else 21.dp
    } else {
        if (screen == Screens.Home || screen == Screens.Search) 22.dp else 24.dp
    }
    val labelText = when (screen) {
        Screens.Home -> "Trang chủ"
        Screens.Search -> "Tìm kiếm"
        Screens.Library -> "Thư viện"
        Screens.Settings -> "Cài đặt"
    }

    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .pressBounce(interactionSource, pressedScale = 0.86f)
            .clickable(
                interactionSource = interactionSource,
                indication = RemoteFocus,
                onClick = {
                    tabTapped(lastTap, viewConfiguration.doubleTapTimeoutMillis, onDoubleTap) {
                        onItemClick(screen, isSelected)
                    }
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .padding(horizontal = 4.dp)
                .graphicsLayer {
                    val t = lit.value.coerceIn(0f, 1.15f)
                    val s = 1f + 0.06f * t
                    scaleX = s
                    scaleY = s
                    translationY = -1.dp.toPx() * t
                }
        ) {
            if (screen == Screens.Library) {
                LibraryDownloadIcon { TabIcons(screen, unlitIcon, litIcon, iconSize) { lit.value } }
            } else {
                TabIcons(screen, unlitIcon, litIcon, iconSize) { lit.value }
            }
            if (showLabel) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = labelText,
                    fontSize = 10.sp,
                    lineHeight = 11.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = lerp(
                        Color.White.copy(alpha = UnlitIconAlpha),
                        AppleMusicAccent,
                        lit.value.coerceIn(0f, 1f),
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** a tab's two icons, the filled Apple Music crimson-pink one fading in over the outline as the tab lights up */
@Composable
private fun TabIcons(
    screen: Screens,
    unlitIcon: androidx.compose.ui.graphics.painter.Painter,
    litIcon: androidx.compose.ui.graphics.painter.Painter,
    iconSize: androidx.compose.ui.unit.Dp,
    lit: () -> Float,
) {
    Box(contentAlignment = Alignment.Center) {
        Icon(
            painter = unlitIcon,
            contentDescription = stringResource(screen.titleId),
            tint = Color.White,
            modifier = Modifier
                .size(iconSize)
                .graphicsLayer {
                    alpha = UnlitIconAlpha * (1f - lit().coerceIn(0f, 1f))
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                }
        )
        Icon(
            painter = litIcon,
            contentDescription = null,
            tint = AppleMusicAccent,
            modifier = Modifier
                .size(iconSize)
                .graphicsLayer {
                    alpha = lit().coerceIn(0f, 1f)
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                }
        )
    }
}
