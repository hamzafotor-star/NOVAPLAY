package tv.novaplay.iptv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tv.novaplay.iptv.data.model.LiveChannel
import tv.novaplay.iptv.data.model.SportEvent
import tv.novaplay.iptv.data.model.VodItem
import tv.novaplay.iptv.data.model.WatchProgress
import tv.novaplay.iptv.ui.AppUiState
import tv.novaplay.iptv.ui.NavTab
import tv.novaplay.iptv.ui.theme.ElectricCyan
import tv.novaplay.iptv.ui.theme.GoldRating
import tv.novaplay.iptv.ui.theme.LiveRed
import tv.novaplay.iptv.ui.theme.NeonViolet
import tv.novaplay.iptv.ui.theme.SurfaceCard
import tv.novaplay.iptv.ui.theme.SurfaceDark

/**
 * Responsive Dashboard screen adapted for Smartphones, Tablets, and Android TV.
 * Includes 1-tap IPTV Bouquet loader right on the Home screen.
 */
@Composable
fun HomeScreen(
    uiState: AppUiState,
    isCompact: Boolean,
    onNavigateTab: (NavTab) -> Unit,
    onSelectChannel: (LiveChannel) -> Unit,
    onSelectVod: (VodItem) -> Unit,
    onResumeProgress: (WatchProgress) -> Unit,
    onSelectSport: (SportEvent) -> Unit,
    onQuickLoadBouquet: (String, String) -> Unit
) {
    val cardWidth = if (isCompact) 185.dp else 230.dp

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(if (isCompact) 12.dp else 16.dp),
        verticalArrangement = Arrangement.spacedBy(if (isCompact) 16.dp else 22.dp)
    ) {
        // 1. Hero Banner (Responsive for Smartphone & TV)
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xFF141E30),
                                Color(0xFF243B55),
                                NeonViolet.copy(alpha = 0.55f)
                            )
                        )
                    )
                    .border(1.dp, ElectricCyan.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
                    .padding(if (isCompact) 14.dp else 20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = ElectricCyan.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "MEDIA3 + LIBVLC",
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricCyan,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Surface(
                            color = LiveRed,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "${uiState.channels.size} CHAÎNES PRÊTES",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Text(
                        text = if (uiState.isAdultMode) {
                            "🔞 Mode Adulte • Olé Olé 18+ (Noir & Rose)"
                        } else if (isCompact) {
                            "NovaPlay IPTV • Direct TV & VOD"
                        } else {
                            "NovaPlay IPTV • Direct TV, Cinéma 4K & Sports"
                        },
                        style = if (isCompact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = if (uiState.isAdultMode) {
                            "Espace 18+ exclusif : affiche uniquement les chaînes et films Olé Olé, Charme, Séduction & Club Privé 24/7."
                        } else {
                            "Chaînes TV en direct fonctionnelles dès l'ouverture (TNT, Télétoon+, Discovery Science, Sport) + chargement en 1 clic des bouquets M3U / Xtream."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFCFD8DC)
                    )

                    if (isCompact) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onNavigateTab(NavTab.LIVE_TV) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan)
                            ) {
                                Icon(Icons.Default.LiveTv, contentDescription = null, tint = Color(0xFF001F26))
                                Spacer(Modifier.width(8.dp))
                                Text("Regarder la TV en Direct", color = Color(0xFF001F26), fontWeight = FontWeight.Bold)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { onNavigateTab(NavTab.VOD) },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceCard)
                                ) {
                                    Icon(Icons.Default.Movie, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Films & Séries", color = Color.White, maxLines = 1)
                                }
                                Button(
                                    onClick = { onNavigateTab(NavTab.SETTINGS) },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonViolet)
                                ) {
                                    Icon(Icons.Default.CloudDownload, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Bouquets IPTV", color = Color.White, maxLines = 1)
                                }
                            }
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = { onNavigateTab(NavTab.LIVE_TV) },
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan)
                            ) {
                                Icon(Icons.Default.LiveTv, contentDescription = null, tint = Color(0xFF001F26))
                                Spacer(Modifier.width(6.dp))
                                Text("Regarder le Direct", color = Color(0xFF001F26), fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { onNavigateTab(NavTab.VOD) },
                                colors = ButtonDefaults.buttonColors(containerColor = SurfaceCard)
                            ) {
                                Icon(Icons.Default.Movie, contentDescription = null, tint = Color.White)
                                Spacer(Modifier.width(6.dp))
                                Text("Catalogue VOD", color = Color.White)
                            }
                            Button(
                                onClick = { onNavigateTab(NavTab.SETTINGS) },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonViolet)
                            ) {
                                Icon(Icons.Default.CloudDownload, contentDescription = null, tint = Color.White)
                                Spacer(Modifier.width(6.dp))
                                Text("Ajouter Playlist IPTV", color = Color.White)
                            }
                        }
                    }
                }
            }
        }

        // 1-Click Quick IPTV Bouquets Rail
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Charger un Bouquet IPTV en 1 Clic",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    if (uiState.isSyncingSource) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = ElectricCyan,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Chargement...", style = MaterialTheme.typography.labelSmall, color = ElectricCyan)
                        }
                    }
                }

                val quickBouquets = if (uiState.isAdultMode) {
                    listOf(
                        "🔞 Bouquet International 18+ (XXX M3U)" to "https://iptv-org.github.io/iptv/categories/xxx.m3u"
                    )
                } else {
                    listOf(
                        "🇫🇷 Bouquet Francophone (+180 chaînes)" to "https://iptv-org.github.io/iptv/languages/fra.m3u",
                        "🇫🇷 Bouquet France TNT & Régions" to "https://iptv-org.github.io/iptv/countries/fr.m3u",
                        "🧒 Bouquet Jeunesse & Animation" to "https://iptv-org.github.io/iptv/categories/kids.m3u",
                        "📚 Bouquet Science & Documentaires" to "https://iptv-org.github.io/iptv/categories/documentary.m3u",
                        "⚽ Bouquet Sports Live Monde" to "https://iptv-org.github.io/iptv/categories/sports.m3u",
                        "🎬 Bouquet Cinéma & Films" to "https://iptv-org.github.io/iptv/categories/movies.m3u"
                    )
                }

                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(quickBouquets) { (title, url) ->
                        Surface(
                            color = SurfaceCard,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .border(1.dp, ElectricCyan.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                                .clickable(enabled = !uiState.isSyncingSource) {
                                    onQuickLoadBouquet(title, url)
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudDownload,
                                    contentDescription = null,
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Continue Watching Rail
        if (uiState.continueWatching.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Continuer la lecture",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(uiState.continueWatching, key = { it.progressKey }) { progress ->
                            Card(
                                modifier = Modifier
                                    .width(if (isCompact) 210.dp else 250.dp)
                                    .clickable { onResumeProgress(progress) },
                                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = progress.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            tint = ElectricCyan
                                        )
                                    }
                                    Text(
                                        text = progress.subtitle,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color(0xFF90A4AE),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(Modifier.height(10.dp))
                                    LinearProgressIndicator(
                                        progress = { progress.progressFraction.coerceAtLeast(0.08f) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(5.dp)
                                            .clip(RoundedCornerShape(3.dp)),
                                        color = ElectricCyan,
                                        trackColor = SurfaceDark
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Live TV & 24/7 Priority Rail
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Chaînes en Direct (${uiState.channels.size})",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                    Text(
                        text = "Tout voir →",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ElectricCyan,
                        modifier = Modifier.clickable { onNavigateTab(NavTab.LIVE_TV) }
                    )
                }

                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(uiState.channels.take(25), key = { it.key }) { channel ->
                        Card(
                            modifier = Modifier
                                .width(cardWidth)
                                .clickable {
                                    onSelectChannel(channel)
                                    onNavigateTab(NavTab.LIVE_TV)
                                },
                            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        color = if (channel.isPpv) LiveRed else ElectricCyan.copy(alpha = 0.18f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "${channel.countryFlag} ${channel.qualityLabel}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (channel.isPpv) Color.White else ElectricCyan,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                        )
                                    }
                                    Text(
                                        text = "Ch. ${channel.ordinal}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF90A4AE)
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = channel.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${channel.group} • ${channel.currentProgram?.title ?: "En Direct"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFB0BEC5),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { channel.currentProgram?.progressFraction() ?: 0.5f },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp)),
                                    color = LiveRed,
                                    trackColor = SurfaceDark
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. VOD Movies & Series Rail
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Films & Séries à l'affiche",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                    Text(
                        text = "Catalogue →",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ElectricCyan,
                        modifier = Modifier.clickable { onNavigateTab(NavTab.VOD) }
                    )
                }

                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(uiState.vodItems, key = { it.id }) { vod ->
                        Card(
                            modifier = Modifier
                                .width(cardWidth)
                                .clickable { onSelectVod(vod) },
                            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Surface(
                                        color = NeonViolet.copy(alpha = 0.25f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = vod.type.name,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFFD1C4E9),
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                        )
                                    }
                                    Text(
                                        text = vod.rating,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = GoldRating
                                    )
                                }
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    text = vod.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${vod.category} • ${vod.durationLabel}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF90A4AE),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        // 5. Live Sports Events Preview (Only shown in Normal Mode)
        if (!uiState.isAdultMode && uiState.sportsEvents.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Sports Live & PPV",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                    Text(
                        text = "Calendrier →",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ElectricCyan,
                        modifier = Modifier.clickable { onNavigateTab(NavTab.SPORTS) }
                    )
                }

                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(uiState.sportsEvents, key = { it.id }) { event ->
                        Card(
                            modifier = Modifier
                                .width(if (isCompact) 225.dp else 260.dp)
                                .clickable { onSelectSport(event) },
                            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.SportsSoccer,
                                            contentDescription = null,
                                            tint = ElectricCyan,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = event.sport,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = ElectricCyan
                                        )
                                    }
                                    Surface(
                                        color = if (event.isLive) LiveRed else Color(0xFF37474F),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = event.scoreOrStatus,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "${event.homeTeam} vs ${event.awayTeam}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${event.competition} • ${event.kickoffLabel}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF90A4AE),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
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
