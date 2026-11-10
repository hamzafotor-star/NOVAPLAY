package tv.novaplay.iptv.ui.screens

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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import tv.novaplay.iptv.data.model.ChannelSortMode
import tv.novaplay.iptv.data.model.LiveChannel
import tv.novaplay.iptv.player.HybridPlayerController
import tv.novaplay.iptv.player.HybridVideoSurface
import tv.novaplay.iptv.ui.AppUiState
import tv.novaplay.iptv.ui.theme.ElectricCyan
import tv.novaplay.iptv.ui.theme.EmeraldLive
import tv.novaplay.iptv.ui.theme.GoldRating
import tv.novaplay.iptv.ui.theme.LiveRed
import tv.novaplay.iptv.ui.theme.NeonViolet
import tv.novaplay.iptv.ui.theme.SurfaceCard
import tv.novaplay.iptv.ui.theme.SurfaceDark
import tv.novaplay.iptv.ui.theme.SurfaceElevated

/**
 * Adaptive Live TV & Organized Bouquet Guide screen:
 * 1. Fullscreen Mode: Edge-to-edge player + Slide-over Bouquet & Channel Drawer
 * 2. Smartphone Landscape Mode: 2-Column split view (Left: Player + Zapping, Right: Bouquets + Channels)
 * 3. Smartphone Portrait Mode: Sticky 16:9 Player + 1-line EPG/Toolbar + Collapsible Search + Organized Bouquets
 * 4. Tablet / Android TV Mode: Cinema Player + EPG Detail Panel + Organized Bouquets (List or Grid)
 */
@Composable
fun LiveTvScreen(
    uiState: AppUiState,
    isCompact: Boolean,
    isPhoneLandscape: Boolean = false,
    playerController: HybridPlayerController,
    onSelectChannel: (LiveChannel) -> Unit,
    onZapChannel: (Int) -> Unit,
    onSelectCountry: (String) -> Unit,
    onSelectCategory: (String) -> Unit,
    onCycleSortMode: () -> Unit,
    onToggleGridView: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onRequestPip: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onOpenPlaylists: () -> Unit
) {
    var showFullscreenDrawer by remember { mutableStateOf(false) }
    var showSearchBar by remember { mutableStateOf(false) }

    // 1. IMMERSIVE FULLSCREEN MODE WITH SLIDE-OVER CHANNEL DRAWER
    if (uiState.isPlayerFullscreen) {
        Box(modifier = Modifier.fillMaxSize()) {
            HybridVideoSurface(
                controller = playerController,
                modifier = Modifier.fillMaxSize(),
                isFullscreen = true,
                onRequestPip = onRequestPip,
                onToggleFullscreen = onToggleFullscreen,
                onZapChannel = onZapChannel,
                onToggleChannelDrawer = { showFullscreenDrawer = !showFullscreenDrawer }
            )

            if (showFullscreenDrawer) {
                Surface(
                    color = SurfaceDark.copy(alpha = 0.94f),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(330.dp)
                        .fillMaxHeight()
                        .border(1.dp, ElectricCyan.copy(alpha = 0.35f), RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)),
                    shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Bouquets & Chaînes (${uiState.channels.size})",
                                style = MaterialTheme.typography.titleMedium,
                                color = ElectricCyan,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(
                                onClick = { showFullscreenDrawer = false },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Fermer", tint = Color.White)
                            }
                        }

                        BouquetTabsRow(
                            uiState = uiState,
                            onSelectCategory = onSelectCategory,
                            onSelectCountry = onSelectCountry,
                            showCountries = false
                        )

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(uiState.channels, key = { it.key }) { ch ->
                                ChannelListRowItem(
                                    channel = ch,
                                    isSelected = uiState.activeChannel?.key == ch.key,
                                    onSelect = { onSelectChannel(ch) },
                                    onToggleFavorite = { onToggleFavorite(ch.key) }
                                )
                            }
                        }
                    }
                }
            }
        }
        return
    }

    val active = uiState.activeChannel

    // 2. SMARTPHONE LANDSCAPE SPLIT-VIEW (Solves vertical overflow when phone is rotated!)
    if (isPhoneLandscape) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Left Column: Video Player + Compact Zapping Bar
            Column(
                modifier = Modifier
                    .weight(1.08f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                HybridVideoSurface(
                    controller = playerController,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, ElectricCyan.copy(alpha = 0.35f), RoundedCornerShape(14.dp)),
                    onRequestPip = onRequestPip,
                    onToggleFullscreen = onToggleFullscreen,
                    onZapChannel = onZapChannel
                )

                CompactZappingAndToolbar(
                    active = active,
                    uiState = uiState,
                    showSearchBar = showSearchBar,
                    onToggleSearch = { showSearchBar = !showSearchBar },
                    onCycleSortMode = onCycleSortMode,
                    onToggleGridView = onToggleGridView,
                    onZapChannel = onZapChannel
                )
            }

            // Right Column: Search + Bouquet Tabs + Full-height Channel List
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AnimatedVisibility(visible = showSearchBar || uiState.liveSearchQuery.isNotEmpty()) {
                    CompactSearchField(
                        query = uiState.liveSearchQuery,
                        totalCount = uiState.channels.size,
                        onQueryChange = onSearchQueryChange
                    )
                }

                BouquetTabsRow(
                    uiState = uiState,
                    onSelectCategory = onSelectCategory,
                    onSelectCountry = onSelectCountry,
                    showCountries = true
                )

                OrganizedChannelCollection(
                    uiState = uiState,
                    gridColumns = 2,
                    onSelectChannel = onSelectChannel,
                    onToggleFavorite = onToggleFavorite,
                    onSelectCountry = onSelectCountry,
                    onSelectCategory = onSelectCategory,
                    onSearchQueryChange = onSearchQueryChange,
                    onOpenPlaylists = onOpenPlaylists
                )
            }
        }
        return
    }

    // 3. SMARTPHONE PORTRAIT & TABLET / ANDROID TV LAYOUT
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = if (isCompact) 10.dp else 14.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        if (isCompact) {
            // SMARTPHONE PORTRAIT: 16:9 Sticky Player + 1-Line EPG/Toolbar + Collapsible Search
            HybridVideoSurface(
                controller = playerController,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, ElectricCyan.copy(alpha = 0.38f), RoundedCornerShape(14.dp)),
                onRequestPip = onRequestPip,
                onToggleFullscreen = onToggleFullscreen,
                onZapChannel = onZapChannel
            )

            CompactZappingAndToolbar(
                active = active,
                uiState = uiState,
                showSearchBar = showSearchBar,
                onToggleSearch = { showSearchBar = !showSearchBar },
                onCycleSortMode = onCycleSortMode,
                onToggleGridView = onToggleGridView,
                onZapChannel = onZapChannel
            )

            AnimatedVisibility(visible = showSearchBar || uiState.liveSearchQuery.isNotEmpty()) {
                CompactSearchField(
                    query = uiState.liveSearchQuery,
                    totalCount = uiState.channels.size,
                    onQueryChange = onSearchQueryChange
                )
            }
        } else {
            // TABLET / ANDROID TV LAYOUT: Side-by-side Player + EPG Panel
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(215.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HybridVideoSurface(
                    controller = playerController,
                    modifier = Modifier
                        .weight(1.6f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, ElectricCyan.copy(alpha = 0.35f), RoundedCornerShape(16.dp)),
                    onRequestPip = onRequestPip,
                    onToggleFullscreen = onToggleFullscreen,
                    onZapChannel = onZapChannel
                )

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    color = SurfaceCard,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "GUIDE EPG EN DIRECT",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ElectricCyan,
                                    fontWeight = FontWeight.Bold
                                )
                                Row {
                                    IconButton(
                                        onClick = { onZapChannel(-1) },
                                        modifier = Modifier.height(26.dp)
                                    ) {
                                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Chaîne Précédente", tint = Color.White)
                                    }
                                    IconButton(
                                        onClick = { onZapChannel(1) },
                                        modifier = Modifier.height(26.dp)
                                    ) {
                                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Chaîne Suivante", tint = Color.White)
                                    }
                                }
                            }

                            Text(
                                text = active?.let { "${it.countryFlag} ${it.name}" } ?: "Sélectionnez une chaîne",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Text(
                                text = active?.currentProgram?.title ?: "Programme en direct",
                                style = MaterialTheme.typography.titleMedium,
                                color = ElectricCyan,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Text(
                                text = active?.currentProgram?.description
                                    ?: "Flux HD classé dans ${active?.group ?: "Direct TV"}.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFB0BEC5),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            LinearProgressIndicator(
                                progress = { active?.currentProgram?.progressFraction() ?: 0.5f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = ElectricCyan,
                                trackColor = SurfaceDark
                            )
                            Text(
                                text = "À suivre : ${active?.nextProgram?.title ?: "Émission suivante"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF90A4AE),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Tablet/TV Search + Sort + Grid/List Toolbar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    CompactSearchField(
                        query = uiState.liveSearchQuery,
                        totalCount = uiState.channels.size,
                        onQueryChange = onSearchQueryChange
                    )
                }

                Surface(
                    color = SurfaceCard,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .border(1.dp, ElectricCyan.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                        .clickable { onCycleSortMode() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Sort, contentDescription = "Trier", tint = ElectricCyan, modifier = Modifier.size(18.dp))
                        Text(
                            text = "Tri : ${uiState.sortMode.label}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Surface(
                    color = SurfaceCard,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .border(1.dp, ElectricCyan.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                        .clickable { onToggleGridView() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (uiState.isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                            contentDescription = "Mode d'affichage",
                            tint = ElectricCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = if (uiState.isGridView) "Liste" else "Grille",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Scrollable Organized Bouquet Tabs + Country Filter Pills
        BouquetTabsRow(
            uiState = uiState,
            onSelectCategory = onSelectCategory,
            onSelectCountry = onSelectCountry,
            showCountries = true
        )

        // Organized Channel List or Grid with Bouquet Headers
        OrganizedChannelCollection(
            uiState = uiState,
            gridColumns = if (isCompact) 2 else 3,
            onSelectChannel = onSelectChannel,
            onToggleFavorite = onToggleFavorite,
            onSelectCountry = onSelectCountry,
            onSelectCategory = onSelectCategory,
            onSearchQueryChange = onSearchQueryChange,
            onOpenPlaylists = onOpenPlaylists
        )
    }
}

@Composable
private fun CompactZappingAndToolbar(
    active: LiveChannel?,
    uiState: AppUiState,
    showSearchBar: Boolean,
    onToggleSearch: () -> Unit,
    onCycleSortMode: () -> Unit,
    onToggleGridView: () -> Unit,
    onZapChannel: (Int) -> Unit
) {
    Surface(
        color = SurfaceCard,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Active Channel Info
            Column(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = active?.let { "${it.countryFlag} ${it.name}" } ?: "Sélectionnez une chaîne",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (active != null) {
                        QualityBadge(quality = active.qualityLabel)
                    }
                }
                Text(
                    text = "${active?.group ?: "Direct"} • ${active?.currentProgram?.title ?: "En Direct"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = ElectricCyan,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Quick Action Buttons: CH-, CH+, Sort, Grid/List, Search Toggle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Surface(
                    color = SurfaceDark,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.clickable { onZapChannel(-1) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowUp,
                            contentDescription = "Chaîne précédente",
                            tint = ElectricCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Text("CH-", style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }

                Surface(
                    color = SurfaceDark,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.clickable { onZapChannel(1) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = "Chaîne suivante",
                            tint = ElectricCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Text("CH+", style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }

                // Sort Mode Cycle Button
                Surface(
                    color = SurfaceDark,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.clickable { onCycleSortMode() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            Icons.Default.Sort,
                            contentDescription = "Trier",
                            tint = ElectricCyan,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = uiState.sortMode.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                }

                // Grid / List Toggle
                Surface(
                    color = SurfaceDark,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.clickable { onToggleGridView() }
                ) {
                    Box(modifier = Modifier.padding(6.dp)) {
                        Icon(
                            imageVector = if (uiState.isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                            contentDescription = "Grille / Liste",
                            tint = ElectricCyan,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Search Toggle
                Surface(
                    color = if (showSearchBar || uiState.liveSearchQuery.isNotEmpty()) ElectricCyan else SurfaceDark,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.clickable { onToggleSearch() }
                ) {
                    Box(modifier = Modifier.padding(6.dp)) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Rechercher",
                            tint = if (showSearchBar || uiState.liveSearchQuery.isNotEmpty()) Color(0xFF001F26) else ElectricCyan,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactSearchField(
    query: String,
    totalCount: Int,
    onQueryChange: (String) -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { Text("Rechercher une chaîne ou un bouquet ($totalCount chaînes)...", maxLines = 1) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = ElectricCyan) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Clear, contentDescription = "Effacer")
                }
            }
        },
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
private fun BouquetTabsRow(
    uiState: AppUiState,
    onSelectCategory: (String) -> Unit,
    onSelectCountry: (String) -> Unit,
    showCountries: Boolean
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Bouquet Tabs with Channel Counts first!
        items(uiState.categories) { category ->
            val count = uiState.categoryCounts[category]
            val labelText = if (count != null && count > 0) "$category ($count)" else category
            val selected = uiState.selectedCategory == category

            FilterChip(
                selected = selected,
                onClick = { onSelectCategory(category) },
                label = {
                    Text(
                        text = labelText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = ElectricCyan,
                    selectedLabelColor = Color(0xFF001F26),
                    containerColor = SurfaceCard,
                    labelColor = Color.White
                )
            )
        }

        // Country pills if multiple countries exist
        if (showCountries && uiState.availableCountries.size > 2) {
            items(uiState.availableCountries) { country ->
                val selected = uiState.selectedCountry == country
                FilterChip(
                    selected = selected,
                    onClick = { onSelectCountry(country) },
                    label = {
                        Text(
                            text = if (country == "TOUS") "🌍 Tous Pays" else country,
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = NeonViolet.copy(alpha = 0.35f),
                        selectedLabelColor = Color.White,
                        containerColor = SurfaceDark,
                        labelColor = Color(0xFFB0BEC5)
                    )
                )
            }
        }
    }
}

@Composable
private fun OrganizedChannelCollection(
    uiState: AppUiState,
    gridColumns: Int,
    onSelectChannel: (LiveChannel) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onSelectCountry: (String) -> Unit,
    onSelectCategory: (String) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onOpenPlaylists: () -> Unit
) {
    if (uiState.channels.isEmpty()) {
        Surface(
            color = SurfaceCard,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Aucune chaîne dans ce filtre",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            onSelectCountry("TOUS")
                            onSelectCategory("Toutes")
                            onSearchQueryChange("")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark)
                    ) {
                        Text("Tout afficher", color = Color.White)
                    }
                    Button(
                        onClick = onOpenPlaylists,
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan)
                    ) {
                        Icon(Icons.Default.AddCircleOutline, contentDescription = null, tint = Color(0xFF001F26))
                        Spacer(Modifier.width(6.dp))
                        Text("Ajouter Bouquet IPTV", color = Color(0xFF001F26), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        return
    }

    if (uiState.isGridView) {
        // MODERN COMPACT GRID VIEW
        LazyVerticalGrid(
            columns = GridCells.Fixed(gridColumns),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(uiState.channels, key = { it.key }) { channel ->
                val isSelected = uiState.activeChannel?.key == channel.key
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .border(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) ElectricCyan else SurfaceElevated,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable { onSelectChannel(channel) },
                    color = if (isSelected) SurfaceElevated else SurfaceCard
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    color = SurfaceDark,
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        if (channel.logoUrl.isNotBlank()) {
                                            AsyncImage(
                                                model = channel.logoUrl,
                                                contentDescription = channel.name,
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(3.dp),
                                                contentScale = ContentScale.Fit
                                            )
                                        } else {
                                            Text(
                                                text = "#${channel.ordinal}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = ElectricCyan,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                                QualityBadge(quality = channel.qualityLabel)
                            }

                            IconButton(
                                onClick = { onToggleFavorite(channel.key) },
                                modifier = Modifier.size(26.dp)
                            ) {
                                Icon(
                                    imageVector = if (channel.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                    contentDescription = "Favori",
                                    tint = if (channel.isFavorite) GoldRating else Color(0xFF78909C),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Text(
                            text = "${channel.countryFlag} ${channel.name}",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = channel.currentProgram?.title ?: channel.group,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF90A4AE),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    } else {
        // ORGANIZED LIST VIEW WITH BOUQUET SECTION HEADERS
        val showSectionHeaders = uiState.selectedCategory == "Toutes" &&
            uiState.sortMode == ChannelSortMode.BY_GROUP &&
            uiState.liveSearchQuery.isBlank()

        val groupedChannels = remember(uiState.channels, showSectionHeaders) {
            if (showSectionHeaders) {
                uiState.channels.groupBy { it.group }
            } else {
                mapOf("" to uiState.channels)
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            groupedChannels.forEach { (bouquetName, channelsInGroup) ->
                if (bouquetName.isNotBlank()) {
                    item(key = "header_$bouquetName") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp, bottom = 2.dp, start = 4.dp, end = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = bouquetName,
                                style = MaterialTheme.typography.titleMedium,
                                color = ElectricCyan,
                                fontWeight = FontWeight.Bold
                            )
                            Surface(
                                color = ElectricCyan.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = "${channelsInGroup.size} chaînes",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ElectricCyan,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                items(channelsInGroup, key = { it.key }) { channel ->
                    ChannelListRowItem(
                        channel = channel,
                        isSelected = uiState.activeChannel?.key == channel.key,
                        onSelect = { onSelectChannel(channel) },
                        onToggleFavorite = { onToggleFavorite(channel.key) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelListRowItem(
    channel: LiveChannel,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = if (isSelected) 1.5.dp else 0.5.dp,
                color = if (isSelected) ElectricCyan else SurfaceElevated,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onSelect() },
        color = if (isSelected) SurfaceElevated else SurfaceCard
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Channel Ordinal + Logo Box
                Surface(
                    color = SurfaceDark,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "${channel.ordinal}",
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricCyan,
                            fontWeight = FontWeight.Bold
                        )
                        if (channel.logoUrl.isNotBlank()) {
                            AsyncImage(
                                model = channel.logoUrl,
                                contentDescription = channel.name,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(4.dp),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "${channel.countryFlag} ${channel.name}",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        QualityBadge(quality = channel.qualityLabel)
                        if (channel.isPpv) {
                            Surface(
                                color = LiveRed,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "SPORT",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(EmeraldLive, RoundedCornerShape(3.dp))
                            )
                        }
                        Text(
                            text = "${channel.group} • ${channel.currentProgram?.title ?: "En Direct"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isSelected) ElectricCyan else Color(0xFF90A4AE),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = if (channel.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = "Favori",
                    tint = if (channel.isFavorite) GoldRating else Color(0xFF78909C)
                )
            }
        }
    }
}

@Composable
private fun QualityBadge(quality: String) {
    val bg = when (quality.uppercase()) {
        "4K", "UHD" -> Brush.horizontalGradient(listOf(Color(0xFFFF6D00), Color(0xFFFFD600)))
        "FHD" -> Brush.horizontalGradient(listOf(ElectricCyan.copy(alpha = 0.28f), NeonViolet.copy(alpha = 0.35f)))
        else -> Brush.horizontalGradient(listOf(SurfaceDark, SurfaceDark))
    }
    val textColor = when (quality.uppercase()) {
        "4K", "UHD" -> Color.Black
        "FHD" -> ElectricCyan
        else -> Color(0xFFB0BEC5)
    }
    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(4.dp))
            .border(0.5.dp, ElectricCyan.copy(alpha = 0.35f), RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp)
    ) {
        Text(
            text = quality,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            fontWeight = FontWeight.Bold
        )
    }
}
