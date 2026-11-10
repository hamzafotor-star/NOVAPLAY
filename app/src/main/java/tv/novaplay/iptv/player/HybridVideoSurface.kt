package tv.novaplay.iptv.player

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import org.videolan.libvlc.util.VLCVideoLayout
import tv.novaplay.iptv.data.model.VideoScaleMode
import tv.novaplay.iptv.ui.theme.ElectricCyan
import tv.novaplay.iptv.ui.theme.EmeraldLive
import tv.novaplay.iptv.ui.theme.LiveRed

/**
 * Unified Compose video player surface that dynamically mounts either:
 * - AndroidX Media3 `PlayerView` (ExoPlayer)
 * - VideoLAN `VLCVideoLayout` (LibVLC native surface)
 * Supports dynamic screen aspect-ratio scaling (16:9, Zoom 20:9, Stretch 100%, 4:3),
 * direct in-player channel zapping (CH- / CH+), stream reload, and Fullscreen Channel Drawer.
 */
@OptIn(UnstableApi::class)
@Composable
fun HybridVideoSurface(
    controller: HybridPlayerController,
    modifier: Modifier = Modifier,
    showControlsOverlay: Boolean = true,
    isFullscreen: Boolean = false,
    onRequestPip: (() -> Unit)? = null,
    onToggleFullscreen: (() -> Unit)? = null,
    onZapChannel: ((Int) -> Unit)? = null,
    onToggleChannelDrawer: (() -> Unit)? = null
) {
    val state by controller.uiState.collectAsState()
    var controlsVisible by remember { mutableStateOf(true) }

    // Auto-hide controls after 4.5s when playing smoothly
    LaunchedEffect(controlsVisible, state.isPlaying, state.isBuffering) {
        if (controlsVisible && state.isPlaying && !state.isBuffering) {
            delay(4_500L)
            controlsVisible = false
        }
    }

    Box(
        modifier = modifier
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                controlsVisible = !controlsVisible
            }
    ) {
        if (state.usingLibVlc) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    VLCVideoLayout(ctx).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = { vlcLayout ->
                    val mp = controller.vlcMediaPlayer
                    if (mp != null) {
                        if (!mp.vlcVout.areViewsAttached()) {
                            mp.attachViews(vlcLayout, null, true, false)
                        }
                        when (state.scaleMode) {
                            VideoScaleMode.FIT -> {
                                mp.scale = 0f
                                mp.aspectRatio = null
                            }
                            VideoScaleMode.ZOOM_CROP -> {
                                mp.aspectRatio = null
                                mp.scale = 1.18f
                            }
                            VideoScaleMode.FILL_STRETCH -> {
                                mp.scale = 0f
                                val w = vlcLayout.width
                                val h = vlcLayout.height
                                mp.aspectRatio = if (w > 0 && h > 0) "$w:$h" else "20:9"
                            }
                            VideoScaleMode.RATIO_4_3 -> {
                                mp.scale = 0f
                                mp.aspectRatio = "4:3"
                            }
                        }
                    }
                }
            )
            DisposableEffect(state.usingLibVlc) {
                onDispose {
                    runCatching { controller.vlcMediaPlayer?.detachViews() }
                }
            }
        } else {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = controller.exoPlayer
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = { playerView ->
                    if (playerView.player !== controller.exoPlayer) {
                        playerView.player = controller.exoPlayer
                    }
                    playerView.resizeMode = when (state.scaleMode) {
                        VideoScaleMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                        VideoScaleMode.ZOOM_CROP -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        VideoScaleMode.FILL_STRETCH -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                        VideoScaleMode.RATIO_4_3 -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                }
            )
        }

        // Center Buffering Indicator
        if (state.isBuffering) {
            Surface(
                color = Color.Black.copy(alpha = 0.65f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(26.dp),
                        color = ElectricCyan,
                        strokeWidth = 2.8.dp
                    )
                    Text(
                        text = "Chargement du flux HD...",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        if (showControlsOverlay && (controlsVisible || !state.isPlaying || state.statusBanner != null)) {
            // Top Bar: Channel title + Aspect Ratio button + Engine badge
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.86f), Color.Transparent)
                        )
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = state.title.ifBlank { "Sélectionnez une chaîne" },
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (state.subtitle.isNotBlank()) {
                        Text(
                            text = state.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFCFD8DC),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Screen Format / Aspect Ratio pill button (solves 16:9 vs 20:9 screen adaptation)
                    Surface(
                        color = Color.White.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .border(1.dp, ElectricCyan.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                            .clickable { controller.cycleScaleMode() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AspectRatio,
                                contentDescription = "Format écran",
                                tint = ElectricCyan,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = state.scaleMode.shortBadge,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Active Engine Badge (tap to switch Media3 <-> LibVLC)
                    Surface(
                        color = if (state.usingLibVlc) Color(0xFFFF6D00).copy(alpha = 0.32f) else ElectricCyan.copy(alpha = 0.22f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.clickable { controller.toggleEngineManually() }
                    ) {
                        Text(
                            text = if (state.usingLibVlc) "LIBVLC" else "MEDIA3",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (state.usingLibVlc) Color(0xFFFFAB40) else ElectricCyan,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Bottom Controls: Play/Pause, Direct Zapping (CH-/CH+), VOD Seek, Reload, Engine Switch, Drawer, PiP, Fullscreen
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f))
                        )
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .align(Alignment.BottomCenter)
            ) {
                state.statusBanner?.let { banner ->
                    Text(
                        text = banner,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF80D8FF),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = 2.dp, start = 4.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left group: Play/Pause + Live/VOD status + In-Player Zapping or VOD Seek
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        IconButton(
                            onClick = { controller.togglePlayPause() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Lecture / Pause",
                                tint = Color.White
                            )
                        }

                        Surface(
                            color = if (state.isLive) LiveRed else Color(0xFF3949AB),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(if (state.isPlaying) EmeraldLive else Color.White, RoundedCornerShape(3.dp))
                                )
                                Text(
                                    text = if (state.isLive) "DIRECT" else "VOD",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (state.isLive && onZapChannel != null) {
                            Spacer(Modifier.width(4.dp))
                            IconButton(
                                onClick = { onZapChannel(-1) },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipPrevious,
                                    contentDescription = "Chaîne précédente",
                                    tint = ElectricCyan
                                )
                            }
                            IconButton(
                                onClick = { onZapChannel(1) },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipNext,
                                    contentDescription = "Chaîne suivante",
                                    tint = ElectricCyan
                                )
                            }
                        } else if (!state.isLive) {
                            Spacer(Modifier.width(4.dp))
                            IconButton(
                                onClick = { controller.seekBy(-10_000L) },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FastRewind,
                                    contentDescription = "-10 secondes",
                                    tint = ElectricCyan
                                )
                            }
                            IconButton(
                                onClick = { controller.seekBy(10_000L) },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FastForward,
                                    contentDescription = "+10 secondes",
                                    tint = ElectricCyan
                                )
                            }
                        }
                    }

                    // Right group: Fullscreen Channel Drawer + Reload Stream + Engine Switch + PiP + Fullscreen
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        if (isFullscreen && onToggleChannelDrawer != null) {
                            Surface(
                                color = ElectricCyan.copy(alpha = 0.22f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.clickable { onToggleChannelDrawer() }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.List,
                                        contentDescription = "Liste des chaînes",
                                        tint = ElectricCyan,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Chaînes",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ElectricCyan,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        IconButton(
                            onClick = { controller.retryCurrentStream() },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Relancer le flux",
                                tint = Color.White
                            )
                        }

                        IconButton(
                            onClick = { controller.toggleEngineManually() },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SyncAlt,
                                contentDescription = "Basculer Moteur ExoPlayer / LibVLC",
                                tint = ElectricCyan
                            )
                        }

                        if (onRequestPip != null) {
                            IconButton(
                                onClick = onRequestPip,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PictureInPictureAlt,
                                    contentDescription = "Picture-in-Picture",
                                    tint = Color.White
                                )
                            }
                        }

                        if (onToggleFullscreen != null) {
                            IconButton(
                                onClick = onToggleFullscreen,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                    contentDescription = "Plein écran",
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
