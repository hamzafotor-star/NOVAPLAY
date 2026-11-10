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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tv.novaplay.iptv.data.model.EpisodeItem
import tv.novaplay.iptv.data.model.VodItem
import tv.novaplay.iptv.data.model.VodType
import tv.novaplay.iptv.ui.AppUiState
import tv.novaplay.iptv.ui.theme.ElectricCyan
import tv.novaplay.iptv.ui.theme.GoldRating
import tv.novaplay.iptv.ui.theme.NeonViolet
import tv.novaplay.iptv.ui.theme.SurfaceCard
import tv.novaplay.iptv.ui.theme.SurfaceDark

/**
 * Responsive VOD Catalog & Movie/Series Preview Screen adapted for 2-column Smartphone grid
 * as well as Tablet and Android TV layouts.
 */
@Composable
fun VodScreen(
    uiState: AppUiState,
    isCompact: Boolean,
    onSelectCategory: (String) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onOpenDetail: (VodItem?) -> Unit,
    onPlayVod: (VodItem, EpisodeItem?) -> Unit,
    onSelectAudioTrack: (String) -> Unit,
    onSelectSubtitleTrack: (String) -> Unit
) {
    val detailItem = uiState.selectedVodDetail
    if (detailItem != null) {
        VodDetailPreview(
            item = detailItem,
            isCompact = isCompact,
            onClose = { onOpenDetail(null) },
            onPlay = { ep -> onPlayVod(detailItem, ep) },
            onSelectAudioTrack = onSelectAudioTrack,
            onSelectSubtitleTrack = onSelectSubtitleTrack
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(if (isCompact) 10.dp else 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedTextField(
            value = uiState.vodSearchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Rechercher un film, une série, un genre...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            shape = RoundedCornerShape(12.dp)
        )

        val categories = listOf("Tous", "Films", "Séries", "Science-Fiction", "Action & Thriller", "Animation & Aventure")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(categories) { cat ->
                FilterChip(
                    selected = uiState.selectedVodCategory == cat,
                    onClick = { onSelectCategory(cat) },
                    label = { Text(cat) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ElectricCyan,
                        selectedLabelColor = Color(0xFF001F26)
                    )
                )
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = if (isCompact) 145.dp else 210.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(uiState.vodItems, key = { it.id }) { item ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenDetail(item) },
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(if (isCompact) 115.dp else 130.dp)
                                .background(
                                    Brush.linearGradient(
                                        if (item.type == VodType.SERIES) {
                                            listOf(Color(0xFF281B4E), Color(0xFF132238))
                                        } else {
                                            listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364))
                                        }
                                    )
                                )
                                .padding(10.dp)
                        ) {
                            Surface(
                                color = if (item.type == VodType.SERIES) NeonViolet else ElectricCyan.copy(alpha = 0.25f),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.align(Alignment.TopStart)
                            ) {
                                Text(
                                    text = if (item.type == VodType.SERIES) "SÉRIE" else "FILM 4K",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (item.type == VodType.SERIES) Color.White else ElectricCyan,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }

                            Text(
                                text = item.rating,
                                style = MaterialTheme.typography.labelSmall,
                                color = GoldRating,
                                modifier = Modifier.align(Alignment.TopEnd)
                            )

                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.align(Alignment.BottomStart)
                            )
                        }

                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "${item.category} • ${item.releaseYear}",
                                style = MaterialTheme.typography.bodySmall,
                                color = ElectricCyan,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = item.durationLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF90A4AE)
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = item.overview,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFB0BEC5),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VodDetailPreview(
    item: VodItem,
    isCompact: Boolean,
    onClose: () -> Unit,
    onPlay: (EpisodeItem?) -> Unit,
    onSelectAudioTrack: (String) -> Unit,
    onSelectSubtitleTrack: (String) -> Unit
) {
    var selectedAudio by remember { mutableStateOf(item.audioTracks.firstOrNull()?.label ?: "Français Audio HD") }
    var selectedSubtitle by remember { mutableStateOf("Désactivés") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(if (isCompact) 12.dp else 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF151E32), Color(0xFF281D46))
                        )
                    )
                    .border(1.dp, ElectricCyan.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
                    .padding(if (isCompact) 14.dp else 20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(color = NeonViolet, shape = RoundedCornerShape(6.dp)) {
                                Text(
                                    text = "${item.type.name} • ${item.releaseYear}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            Surface(color = SurfaceDark, shape = RoundedCornerShape(6.dp)) {
                                Text(
                                    text = item.rating,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = GoldRating,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        IconButton(onClick = onClose) {
                            Icon(Icons.Default.Close, contentDescription = "Fermer", tint = Color.White)
                        }
                    }

                    Text(
                        text = item.title,
                        style = if (isCompact) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineLarge,
                        color = Color.White
                    )

                    Text(
                        text = "${item.category} • ${item.durationLabel} • Langues : ${item.availableLanguages.joinToString(", ")}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ElectricCyan
                    )

                    Text(
                        text = item.overview,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFCFD8DC)
                    )

                    Spacer(Modifier.height(4.dp))

                    Button(
                        onClick = { onPlay(item.episodes.firstOrNull()) },
                        modifier = if (isCompact) Modifier.fillMaxWidth() else Modifier,
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFF001F26))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (item.episodes.isNotEmpty()) "Lancer l'Épisode 1" else "Lancer le Film",
                            color = Color(0xFF001F26),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        item {
            Surface(
                color = SurfaceCard,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Subtitles, contentDescription = null, tint = ElectricCyan)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Pistes Audio & Sous-titres",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                    }

                    Text("Audio :", style = MaterialTheme.typography.labelSmall, color = Color(0xFF90A4AE))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(item.audioTracks) { track ->
                            FilterChip(
                                selected = selectedAudio == track.label,
                                onClick = {
                                    selectedAudio = track.label
                                    onSelectAudioTrack(track.label)
                                },
                                label = { Text("${track.label} (${track.codec})") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ElectricCyan,
                                    selectedLabelColor = Color(0xFF001F26)
                                )
                            )
                        }
                    }

                    Text("Sous-titres :", style = MaterialTheme.typography.labelSmall, color = Color(0xFF90A4AE))
                    val subOptions = listOf("Désactivés") + item.subtitles.map { it.label }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(subOptions) { subLabel ->
                            FilterChip(
                                selected = selectedSubtitle == subLabel,
                                onClick = {
                                    selectedSubtitle = subLabel
                                    onSelectSubtitleTrack(subLabel)
                                },
                                label = { Text(subLabel) },
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

        if (item.episodes.isNotEmpty()) {
            item {
                Text(
                    text = "Épisodes disponibles",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )
            }

            items(item.episodes, key = { it.id }) { ep ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onPlay(ep) },
                    color = SurfaceCard
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "S${ep.seasonNumber}:E${ep.episodeNumber} • ${ep.title}",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                            Text(
                                text = "${ep.overview} (${ep.durationMinutes} min)",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF90A4AE)
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Lire l'épisode",
                            tint = ElectricCyan
                        )
                    }
                }
            }
        }
    }
}
