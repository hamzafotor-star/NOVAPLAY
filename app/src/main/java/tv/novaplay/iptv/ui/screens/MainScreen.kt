package tv.novaplay.iptv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import tv.novaplay.iptv.player.HybridVideoSurface
import tv.novaplay.iptv.ui.MainViewModel
import tv.novaplay.iptv.ui.NavTab
import tv.novaplay.iptv.ui.theme.ElectricCyan
import tv.novaplay.iptv.ui.theme.EmeraldLive
import tv.novaplay.iptv.ui.theme.LiveRed
import tv.novaplay.iptv.ui.theme.NeonViolet
import tv.novaplay.iptv.ui.theme.ObsidianBackground
import tv.novaplay.iptv.ui.theme.SurfaceCard
import tv.novaplay.iptv.ui.theme.SurfaceDark

/**
 * Root adaptive shell with 1-tap "🔞 Mode Adulte / Olé Olé" (Black & Pink/Rose theme) switcher.
 * When Adult Mode is ON:
 * - Entire UI switches to Pitch-Black & Neon Rose
 * - Normal family/sports tabs & channels are hidden, showing ONLY "Olé Olé / 18+" channels & VOD!
 */
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    isInPipMode: Boolean,
    onRequestPip: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val configuration = LocalConfiguration.current
    val isCompactWidth = configuration.screenWidthDp < 600
    val isPhoneLandscape = configuration.screenWidthDp >= 600 && configuration.screenHeightDp < 480

    val visibleTabs = if (uiState.isAdultMode) {
        listOf(NavTab.LIVE_TV, NavTab.VOD, NavTab.HOME, NavTab.SETTINGS)
    } else {
        NavTab.entries
    }

    // In Picture-in-Picture mode, render only the clean video surface
    if (isInPipMode) {
        HybridVideoSurface(
            controller = viewModel.playerController,
            modifier = Modifier.fillMaxSize(),
            showControlsOverlay = false
        )
        return
    }

    // In Fullscreen Player mode, render edge-to-edge without system bar padding or navigation bars
    if (uiState.isPlayerFullscreen) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            ActiveScreenContent(
                viewModel = viewModel,
                isCompact = isCompactWidth,
                isPhoneLandscape = isPhoneLandscape,
                onRequestPip = onRequestPip
            )
        }
        return
    }

    if (isCompactWidth) {
        // Smartphone Portrait layout: Sleek Top Header + Full-width Content + Bottom NavigationBar
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .background(ObsidianBackground)
                .statusBarsPadding()
                .navigationBarsPadding(),
            containerColor = ObsidianBackground,
            topBar = {
                Surface(
                    color = SurfaceDark,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(
                                        Brush.linearGradient(listOf(ElectricCyan, NeonViolet)),
                                        RoundedCornerShape(9.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (uiState.isAdultMode) "18+" else "NP",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                            Column {
                                Text(
                                    text = if (uiState.isAdultMode) "NovaPlay • Olé Olé 🔞" else "NovaPlay IPTV",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (uiState.isAdultMode) Color(0xFFFF80AB) else Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (uiState.isAdultMode) {
                                        "${uiState.channels.size} chaînes 18+ • Mode Noir & Rose"
                                    } else {
                                        "${uiState.channels.size} chaînes • ${maxOf(1, uiState.categories.size - 2)} bouquets"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (uiState.isAdultMode) Color(0xFFFF4081) else Color(0xFF90A4AE)
                                )
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 🔞 Mode Adulte / Olé Olé Toggle Pill
                            Surface(
                                color = if (uiState.isAdultMode) Color(0xFFFF1493) else Color(0xFF280B1B),
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier
                                    .border(
                                        width = 1.dp,
                                        color = Color(0xFFFF2A85),
                                        shape = RoundedCornerShape(20.dp)
                                    )
                                    .clickable { viewModel.toggleAdultMode() }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Favorite,
                                        contentDescription = "Mode Adulte Olé Olé",
                                        tint = if (uiState.isAdultMode) Color.White else Color(0xFFFF4081),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = if (uiState.isAdultMode) "🔞 QUITTER 18+" else "🔞 OLÉ OLÉ",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (uiState.isAdultMode) Color.White else Color(0xFFFF80AB),
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }

                            Surface(
                                color = SurfaceCard,
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier
                                    .border(1.dp, ElectricCyan.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                                    .clickable { viewModel.selectTab(NavTab.SETTINGS) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AddCircleOutline,
                                        contentDescription = "Bouquets IPTV",
                                        tint = ElectricCyan,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(Modifier.width(3.dp))
                                    Text(
                                        text = "IPTV",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ElectricCyan,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            },
            bottomBar = {
                NavigationBar(
                    containerColor = SurfaceDark,
                    contentColor = Color.White,
                    tonalElevation = 8.dp
                ) {
                    visibleTabs.forEach { tab ->
                        val icon = when (tab) {
                            NavTab.HOME -> Icons.Default.Home
                            NavTab.LIVE_TV -> Icons.Default.LiveTv
                            NavTab.VOD -> Icons.Default.Movie
                            NavTab.SPORTS -> Icons.Default.SportsSoccer
                            NavTab.SETTINGS -> Icons.Default.Settings
                        }
                        NavigationBarItem(
                            selected = uiState.currentTab == tab,
                            onClick = { viewModel.selectTab(tab) },
                            icon = { Icon(icon, contentDescription = tab.shortLabel) },
                            label = {
                                Text(
                                    text = if (uiState.isAdultMode && tab == NavTab.LIVE_TV) "Direct 18+" else tab.shortLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = if (uiState.isAdultMode) Color.White else Color(0xFF001F26),
                                selectedTextColor = ElectricCyan,
                                indicatorColor = ElectricCyan,
                                unselectedIconColor = Color(0xFF90A4AE),
                                unselectedTextColor = Color(0xFF90A4AE)
                            )
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                ActiveScreenContent(
                    viewModel = viewModel,
                    isCompact = true,
                    isPhoneLandscape = false,
                    onRequestPip = onRequestPip
                )
            }
        }
    } else {
        // Smartphone Landscape, Tablet, or Android TV layout: Left NavigationRail + Main Content
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(ObsidianBackground)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            NavigationRail(
                containerColor = SurfaceDark,
                contentColor = Color.White,
                modifier = Modifier.fillMaxHeight(),
                header = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(
                                    Brush.linearGradient(listOf(ElectricCyan, NeonViolet)),
                                    RoundedCornerShape(10.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (uiState.isAdultMode) "18+" else "NP",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        // 🔞 Adult / Olé Olé Toggle button in NavigationRail
                        Surface(
                            color = if (uiState.isAdultMode) Color(0xFFFF1493) else Color(0xFF280B1B),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .border(1.dp, Color(0xFFFF2A85), RoundedCornerShape(12.dp))
                                .clickable { viewModel.toggleAdultMode() }
                        ) {
                            Text(
                                text = if (uiState.isAdultMode) "🔞 ON" else "🔞 18+",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            ) {
                Spacer(Modifier.height(4.dp))
                visibleTabs.forEach { tab ->
                    val icon = when (tab) {
                        NavTab.HOME -> Icons.Default.Home
                        NavTab.LIVE_TV -> Icons.Default.LiveTv
                        NavTab.VOD -> Icons.Default.Movie
                        NavTab.SPORTS -> Icons.Default.SportsSoccer
                        NavTab.SETTINGS -> Icons.Default.Settings
                    }
                    NavigationRailItem(
                        selected = uiState.currentTab == tab,
                        onClick = { viewModel.selectTab(tab) },
                        icon = { Icon(icon, contentDescription = tab.label) },
                        label = {
                            if (!isPhoneLandscape) {
                                Text(tab.shortLabel, style = MaterialTheme.typography.labelSmall)
                            }
                        },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = if (uiState.isAdultMode) Color.White else Color(0xFF001F26),
                            selectedTextColor = ElectricCyan,
                            indicatorColor = ElectricCyan,
                            unselectedIconColor = Color(0xFF90A4AE),
                            unselectedTextColor = Color(0xFF90A4AE)
                        )
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                ActiveScreenContent(
                    viewModel = viewModel,
                    isCompact = isPhoneLandscape,
                    isPhoneLandscape = isPhoneLandscape,
                    onRequestPip = onRequestPip
                )
            }
        }
    }
}

@Composable
private fun ActiveScreenContent(
    viewModel: MainViewModel,
    isCompact: Boolean,
    isPhoneLandscape: Boolean,
    onRequestPip: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    when (uiState.currentTab) {
        NavTab.HOME -> HomeScreen(
            uiState = uiState,
            isCompact = isCompact,
            onNavigateTab = { viewModel.selectTab(it) },
            onSelectChannel = { viewModel.selectLiveChannel(it) },
            onSelectVod = {
                viewModel.selectTab(NavTab.VOD)
                viewModel.openVodDetail(it)
            },
            onResumeProgress = { viewModel.resumeFromWatchProgress(it) },
            onSelectSport = { viewModel.watchSportEvent(it) },
            onQuickLoadBouquet = { name, url ->
                viewModel.addPlaylistSource(
                    name = name,
                    type = tv.novaplay.iptv.data.model.SourceType.M3U_URL,
                    serverUrl = url,
                    navigateToLiveOnSuccess = true
                )
            }
        )

        NavTab.LIVE_TV -> LiveTvScreen(
            uiState = uiState,
            isCompact = isCompact,
            isPhoneLandscape = isPhoneLandscape,
            playerController = viewModel.playerController,
            onSelectChannel = { viewModel.selectLiveChannel(it, uiState.isPlayerFullscreen) },
            onZapChannel = { viewModel.zapChannel(it) },
            onSelectCountry = { viewModel.setLiveCountryFilter(it) },
            onSelectCategory = { viewModel.setLiveCategoryFilter(it) },
            onCycleSortMode = { viewModel.cycleSortMode() },
            onToggleGridView = { viewModel.toggleGridView() },
            onSearchQueryChange = { viewModel.setLiveSearchQuery(it) },
            onToggleFavorite = { viewModel.toggleChannelFavorite(it) },
            onRequestPip = onRequestPip,
            onToggleFullscreen = { viewModel.toggleFullscreen() },
            onOpenPlaylists = { viewModel.selectTab(NavTab.SETTINGS) }
        )

        NavTab.VOD -> VodScreen(
            uiState = uiState,
            isCompact = isCompact,
            onSelectCategory = { viewModel.setVodCategory(it) },
            onSearchQueryChange = { viewModel.setVodSearchQuery(it) },
            onOpenDetail = { viewModel.openVodDetail(it) },
            onPlayVod = { vod, ep -> viewModel.playVodItem(vod, ep) },
            onSelectAudioTrack = { viewModel.playerController.selectAudioLanguage(it) },
            onSelectSubtitleTrack = { viewModel.playerController.selectSubtitleLanguage(it) }
        )

        NavTab.SPORTS -> SportsScreen(
            uiState = uiState,
            isCompact = isCompact,
            onSelectSportFilter = { viewModel.setSportFilter(it) },
            onToggleFavorite = { viewModel.toggleSportFavorite(it) },
            onWatchEvent = { viewModel.watchSportEvent(it) }
        )

        NavTab.SETTINGS -> SettingsScreen(
            uiState = uiState,
            isCompact = isCompact,
            playerController = viewModel.playerController,
            onAddSource = { name, type, url, user, pass, goLive ->
                viewModel.addPlaylistSource(name, type, url, user, pass, goLive)
            },
            onRestoreBuiltin = { viewModel.restoreBuiltinCatalog() },
            onDeleteSource = { viewModel.deletePlaylistSource(it) },
            onSelectEngineMode = { viewModel.setEnginePreference(it) },
            onToggleCategoryHidden = { viewModel.toggleCategoryHidden(it) }
        )
    }
}
