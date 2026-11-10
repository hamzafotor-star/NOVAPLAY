package tv.novaplay.iptv.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tv.novaplay.iptv.data.model.PlayerEngineMode
import tv.novaplay.iptv.data.model.SourceType
import tv.novaplay.iptv.player.HybridPlayerController
import tv.novaplay.iptv.ui.AppUiState
import tv.novaplay.iptv.ui.theme.ElectricCyan
import tv.novaplay.iptv.ui.theme.LiveRed
import tv.novaplay.iptv.ui.theme.NeonViolet
import tv.novaplay.iptv.ui.theme.SurfaceCard
import tv.novaplay.iptv.ui.theme.SurfaceDark

/**
 * Playlist Sources (M3U / Xtream Codes + 1-Tap Public Free IPTV Bouquets),
 * Hybrid Engine Preferences, and Hidden Categories manager.
 */
@Composable
fun SettingsScreen(
    uiState: AppUiState,
    isCompact: Boolean,
    playerController: HybridPlayerController,
    onAddSource: (String, SourceType, String, String, String, Boolean) -> Unit,
    onRestoreBuiltin: () -> Unit,
    onDeleteSource: (String) -> Unit,
    onSelectEngineMode: (PlayerEngineMode) -> Unit,
    onToggleCategoryHidden: (String) -> Unit
) {
    val playerState by playerController.uiState.collectAsState()
    val clipboard = LocalClipboardManager.current

    var sourceType by remember { mutableStateOf(SourceType.M3U_URL) }
    var sourceName by remember { mutableStateOf("") }
    var serverUrl by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    val presetBouquets = listOf(
        Triple(
            "🇫🇷 Bouquet Francophone Complet",
            "Plus de 180 chaînes TV francophones en direct (France, Belgique, Suisse, Afrique)",
            "https://iptv-org.github.io/iptv/languages/fra.m3u"
        ),
        Triple(
            "🇫🇷 Bouquet France TNT & Régions",
            "Chaînes nationales et régionales françaises en direct",
            "https://iptv-org.github.io/iptv/countries/fr.m3u"
        ),
        Triple(
            "⚽ Bouquet Sports Live Monde",
            "Chaînes sportives internationales en direct 24/7",
            "https://iptv-org.github.io/iptv/categories/sports.m3u"
        ),
        Triple(
            "🎬 Bouquet Cinéma & Séries",
            "Chaînes cinéma, films et divertissement 24/7",
            "https://iptv-org.github.io/iptv/categories/movies.m3u"
        ),
        Triple(
            "🌍 Bouquet Actualités 24/7",
            "Chaînes d'information continue mondiales",
            "https://iptv-org.github.io/iptv/categories/news.m3u"
        ),
        Triple(
            "🎵 Bouquet Musique & Concerts",
            "Chaînes musicales et clips non-stop",
            "https://iptv-org.github.io/iptv/categories/music.m3u"
        )
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(if (isCompact) 12.dp else 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Status banner if syncing or completed
        uiState.syncStatusMessage?.let { msg ->
            item {
                Surface(
                    color = Color(0xFF102A3B),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, ElectricCyan, RoundedCornerShape(12.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (uiState.isSyncingSource) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = ElectricCyan,
                                strokeWidth = 2.dp
                            )
                        }
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodyMedium,
                            color = ElectricCyan,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // 1. 1-Click Free Ready-to-Use IPTV Bouquets
        item {
            Surface(
                color = SurfaceCard,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Bouquets IPTV Prêts à l'Emploi (1 Clic)",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Touchez un bouquet ci-dessous pour télécharger et activer immédiatement ses chaînes en direct :",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF90A4AE)
                            )
                        }
                    }

                    presetBouquets.forEach { (title, desc, url) ->
                        Surface(
                            color = SurfaceDark,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, ElectricCyan.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                .clickable(enabled = !uiState.isSyncingSource) {
                                    onAddSource(title, SourceType.M3U_URL, url, "", "", true)
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = desc,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF90A4AE)
                                    )
                                }
                                Surface(
                                    color = ElectricCyan,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CloudDownload,
                                            contentDescription = null,
                                            tint = Color(0xFF001F26),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = "Activer",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF001F26),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = onRestoreBuiltin,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = ElectricCyan)
                        Spacer(Modifier.width(8.dp))
                        Text("Restaurer le Bouquet Rapide par Défaut (12 chaînes HD)", color = ElectricCyan)
                    }
                }
            }
        }

        // 2. Custom M3U / Xtream Codes Importer (Smartphone friendly)
        item {
            Surface(
                color = SurfaceCard,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Ajouter votre Playlist M3U ou Compte Xtream Codes",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = sourceType == SourceType.M3U_URL,
                            onClick = { sourceType = SourceType.M3U_URL },
                            label = { Text("Lien M3U / M3U8") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ElectricCyan,
                                selectedLabelColor = Color(0xFF001F26)
                            )
                        )
                        FilterChip(
                            selected = sourceType == SourceType.XTREAM_CODES,
                            onClick = { sourceType = SourceType.XTREAM_CODES },
                            label = { Text("Compte Xtream Codes") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ElectricCyan,
                                selectedLabelColor = Color(0xFF001F26)
                            )
                        )
                    }

                    OutlinedTextField(
                        value = sourceName,
                        onValueChange = { sourceName = it },
                        label = { Text("Nom du bouquet (ex: Mon Abonnement IPTV)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = { serverUrl = it },
                        label = {
                            Text(
                                if (sourceType == SourceType.M3U_URL) {
                                    "URL M3U / M3U8 (ou contenu #EXTM3U)"
                                } else {
                                    "URL Serveur Xtream (ex: http://serveur.tv:8080)"
                                }
                            )
                        },
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    val clip = clipboard.getText()?.text
                                    if (!clip.isNullOrBlank()) {
                                        serverUrl = clip.trim()
                                    }
                                }
                            ) {
                                Icon(Icons.Default.ContentPaste, contentDescription = "Coller", tint = ElectricCyan)
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (sourceType == SourceType.XTREAM_CODES) {
                        if (isCompact) {
                            OutlinedTextField(
                                value = username,
                                onValueChange = { username = it },
                                label = { Text("Nom d'utilisateur Xtream") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it },
                                label = { Text("Mot de passe Xtream") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(
                                    value = username,
                                    onValueChange = { username = it },
                                    label = { Text("Utilisateur") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = password,
                                    onValueChange = { password = it },
                                    label = { Text("Mot de passe") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    Button(
                        onClick = {
                            if (serverUrl.isNotBlank()) {
                                onAddSource(sourceName, sourceType, serverUrl, username, password, true)
                            }
                        },
                        enabled = !uiState.isSyncingSource && serverUrl.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan)
                    ) {
                        Text(
                            text = if (uiState.isSyncingSource) "Synchronisation en cours..." else "Importer et Lancer le Direct",
                            color = Color(0xFF001F26),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // 3. Hybrid Player Engine Mode
        item {
            Surface(
                color = SurfaceCard,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Moteur Vidéo Hybride (Media3 ExoPlayer + LibVLC)",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Le mode Hybride Auto utilise Media3 en priorité et bascule automatiquement sur LibVLC pour les flux MPEG-TS (.ts), RTSP ou codecs AC3/DTS :",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF90A4AE)
                    )

                    PlayerEngineMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectEngineMode(mode) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = playerState.engineMode == mode,
                                onClick = { onSelectEngineMode(mode) }
                            )
                            Text(
                                text = mode.label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

        // 4. Hidden Categories Management
        item {
            Surface(
                color = SurfaceCard,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Catégories Visibles / Masquées",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Touchez une catégorie pour la masquer ou la réafficher dans le guide :",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF90A4AE)
                    )

                    val allCats = (uiState.categories + uiState.hiddenCategories)
                        .distinct()
                        .filter { it != "Toutes" && it != "Favoris ⭐" }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(allCats) { cat ->
                            val isHidden = uiState.hiddenCategories.contains(cat)
                            FilterChip(
                                selected = isHidden,
                                onClick = { onToggleCategoryHidden(cat) },
                                label = { Text(if (isHidden) "Masqué : $cat" else cat) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NeonViolet,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }
            }
        }

        // 5. Saved Playlist Sources
        item {
            Text(
                text = "Sources IPTV enregistrées (${uiState.playlistSources.size})",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                uiState.playlistSources.forEach { src ->
                    Surface(
                        color = SurfaceCard,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    text = src.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${src.channelCount} chaînes • ${src.serverUrl}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF90A4AE),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        onAddSource(src.name, src.type, src.serverUrl, src.username, src.password, true)
                                    },
                                    enabled = !uiState.isSyncingSource
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Activer et regarder",
                                        tint = ElectricCyan
                                    )
                                }
                                IconButton(
                                    onClick = { onDeleteSource(src.id) }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Supprimer",
                                        tint = LiveRed
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
