package tv.novaplay.iptv.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import tv.novaplay.iptv.data.model.SportEvent
import tv.novaplay.iptv.ui.AppUiState
import tv.novaplay.iptv.ui.theme.ElectricCyan
import tv.novaplay.iptv.ui.theme.GoldRating
import tv.novaplay.iptv.ui.theme.LiveRed
import tv.novaplay.iptv.ui.theme.SurfaceCard

/**
 * Responsive Live Sports & PPV Schedule screen adapted for Smartphone and TV.
 */
@Composable
fun SportsScreen(
    uiState: AppUiState,
    isCompact: Boolean,
    onSelectSportFilter: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onWatchEvent: (SportEvent) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(if (isCompact) 12.dp else 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Sports en Direct & Événements PPV",
            style = if (isCompact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )

        val sports = listOf("Tous", "Football", "MMA / Boxe", "Formule 1", "Basketball")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(sports) { sport ->
                FilterChip(
                    selected = uiState.selectedSportFilter == sport,
                    onClick = { onSelectSportFilter(sport) },
                    label = { Text(sport) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ElectricCyan,
                        selectedLabelColor = Color(0xFF001F26)
                    )
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(uiState.sportsEvents, key = { it.id }) { event ->
                Surface(
                    color = SurfaceCard,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onWatchEvent(event) }
                ) {
                    if (isCompact) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
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
                                    if (event.isPpv) {
                                        Surface(
                                            color = ElectricCyan.copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = "PPV HD",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = ElectricCyan,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = event.sport,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF90A4AE)
                                    )
                                }

                                IconButton(onClick = { onToggleFavorite(event.id) }) {
                                    Icon(
                                        imageVector = if (event.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                        contentDescription = "Sport Favori",
                                        tint = if (event.isFavorite) GoldRating else Color(0xFF78909C)
                                    )
                                }
                            }

                            Text(
                                text = "${event.homeTeam}  vs  ${event.awayTeam}",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                text = "${event.competition} • ${event.kickoffLabel}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFB0BEC5)
                            )

                            Spacer(Modifier.height(2.dp))

                            Button(
                                onClick = { onWatchEvent(event) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFF001F26))
                                Spacer(Modifier.width(6.dp))
                                Text("Regarder la chaîne en Direct", color = Color(0xFF001F26), fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
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
                                    if (event.isPpv) {
                                        Surface(
                                            color = ElectricCyan.copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = "PPV HD",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = ElectricCyan,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = event.sport,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF90A4AE)
                                    )
                                }

                                Text(
                                    text = "${event.homeTeam}  vs  ${event.awayTeam}",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = Color.White
                                )

                                Text(
                                    text = "${event.competition} • ${event.kickoffLabel}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFFB0BEC5)
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { onToggleFavorite(event.id) }) {
                                    Icon(
                                        imageVector = if (event.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                        contentDescription = "Sport Favori",
                                        tint = if (event.isFavorite) GoldRating else Color(0xFF78909C)
                                    )
                                }
                                Button(
                                    onClick = { onWatchEvent(event) },
                                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFF001F26))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Direct", color = Color(0xFF001F26), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
