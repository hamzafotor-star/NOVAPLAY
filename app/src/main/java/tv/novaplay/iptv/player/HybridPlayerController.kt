package tv.novaplay.iptv.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import tv.novaplay.iptv.data.model.PlayerEngineMode
import tv.novaplay.iptv.data.model.TransportFallbackStage
import tv.novaplay.iptv.data.model.VideoScaleMode
import tv.novaplay.iptv.data.network.XtreamCodesClient
import tv.novaplay.iptv.data.parser.M3uParser

data class PlaybackUiState(
    val title: String = "",
    val subtitle: String = "",
    val primaryUrl: String = "",
    val backupUrl: String? = null,
    val activeUrl: String = "",
    val isLive: Boolean = true,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val usingLibVlc: Boolean = false,
    val engineMode: PlayerEngineMode = PlayerEngineMode.AUTO_HYBRID,
    val fallbackStage: TransportFallbackStage = TransportFallbackStage.PRIMARY,
    val scaleMode: VideoScaleMode = VideoScaleMode.FIT,
    val statusBanner: String? = null,
    val selectedAudioLanguage: String = "FR",
    val selectedSubtitleLanguage: String = "Désactivés",
    val progressKey: String? = null
)

/**
 * Hybrid Video Player Engine combining AndroidX Media3 (ExoPlayer with OkHttp IPTV transport)
 * and VideoLAN LibVLC with fast zapping buffers, screen aspect-ratio scaling, and automatic
 * multi-stage recovery + 7.5s buffering watchdog.
 */
@OptIn(UnstableApi::class)
class HybridPlayerController(context: Context) {

    private val appContext = context.applicationContext
    private val playerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var watchdogJob: Job? = null

    private val _uiState = MutableStateFlow(PlaybackUiState())
    val uiState: StateFlow<PlaybackUiState> = _uiState.asStateFlow()

    private val okHttpDataSourceFactory = OkHttpDataSource.Factory(XtreamCodesClient.buildIptvHttpClient())
        .setUserAgent("VLC/3.0.20 LibVLC/3.0.20")

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(appContext)
        .setMediaSourceFactory(DefaultMediaSourceFactory(appContext).setDataSourceFactory(okHttpDataSourceFactory))
        .setLoadControl(
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    /* minBufferMs = */ 8_000,
                    /* maxBufferMs = */ 35_000,
                    /* bufferForPlaybackMs = */ 800,
                    /* bufferForPlaybackAfterRebufferMs = */ 1_500
                )
                .build()
        )
        .build()

    private var libVlc: LibVLC? = null
    var vlcMediaPlayer: MediaPlayer? = null
        private set

    init {
        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (!_uiState.value.usingLibVlc) {
                    val buffering = playbackState == Player.STATE_BUFFERING
                    if (!buffering) {
                        watchdogJob?.cancel()
                    }
                    _uiState.value = _uiState.value.copy(
                        isBuffering = buffering,
                        isPlaying = exoPlayer.isPlaying,
                        statusBanner = if (playbackState == Player.STATE_READY) null else _uiState.value.statusBanner
                    )
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!_uiState.value.usingLibVlc) {
                    if (isPlaying) {
                        watchdogJob?.cancel()
                    }
                    _uiState.value = _uiState.value.copy(
                        isPlaying = isPlaying,
                        isBuffering = if (isPlaying) false else _uiState.value.isBuffering
                    )
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                watchdogJob?.cancel()
                recoverPlayback("Media3 (${error.errorCodeName})")
            }
        })
    }

    private fun startBufferingWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = playerScope.launch {
            delay(7_500L)
            val current = _uiState.value
            if (current.isBuffering && !current.isPlaying) {
                recoverPlayback("Délai réseau dépassé")
            }
        }
    }

    private fun ensureLibVlcInitialized(): MediaPlayer {
        val existing = vlcMediaPlayer
        if (existing != null) return existing

        val options = arrayListOf(
            "--network-caching=1800",
            "--live-caching=1800",
            "--http-reconnect",
            "--clock-jitter=0",
            "--clock-synchro=0",
            "--drop-late-frames",
            "--skip-frames"
        )
        val vlc = LibVLC(appContext, options)
        val mp = MediaPlayer(vlc)
        mp.setEventListener { event ->
            when (event.type) {
                MediaPlayer.Event.Opening, MediaPlayer.Event.Buffering -> {
                    if (_uiState.value.usingLibVlc) {
                        val stillBuffering = event.buffering < 100f
                        if (!stillBuffering) watchdogJob?.cancel()
                        _uiState.value = _uiState.value.copy(isBuffering = stillBuffering)
                    }
                }
                MediaPlayer.Event.Playing -> {
                    if (_uiState.value.usingLibVlc) {
                        watchdogJob?.cancel()
                        _uiState.value = _uiState.value.copy(
                            isPlaying = true,
                            isBuffering = false,
                            statusBanner = "Décodeur LibVLC actif"
                        )
                    }
                }
                MediaPlayer.Event.Paused, MediaPlayer.Event.Stopped -> {
                    if (_uiState.value.usingLibVlc) {
                        _uiState.value = _uiState.value.copy(isPlaying = false, isBuffering = false)
                    }
                }
                MediaPlayer.Event.EncounteredError -> {
                    watchdogJob?.cancel()
                    val state = _uiState.value
                    val altUrl = state.backupUrl
                    if (!altUrl.isNullOrBlank() && altUrl != state.activeUrl) {
                        _uiState.value = state.copy(
                            activeUrl = altUrl,
                            statusBanner = "Bascule vers le flux de secours..."
                        )
                        playWithLibVlc(altUrl, 0L)
                    } else {
                        _uiState.value = state.copy(
                            isBuffering = false,
                            statusBanner = "Flux momentanément hors-ligne (Appuyez sur Relancer)"
                        )
                    }
                }
            }
        }
        libVlc = vlc
        vlcMediaPlayer = mp
        return mp
    }

    fun startStream(
        title: String,
        subtitle: String,
        primaryUrl: String,
        backupUrl: String? = null,
        isLive: Boolean = true,
        resumePositionMs: Long = 0L,
        progressKey: String? = null
    ) {
        val cleanUrl = primaryUrl.trim()
        if (cleanUrl.isBlank()) return

        val mode = _uiState.value.engineMode
        val forceVlc = mode == PlayerEngineMode.LIBVLC_COMPATIBILITY ||
            cleanUrl.startsWith("rtsp://", ignoreCase = true) ||
            cleanUrl.startsWith("rtmp://", ignoreCase = true) ||
            cleanUrl.startsWith("mms://", ignoreCase = true)

        val resolvedBackup = backupUrl ?: M3uParser.deriveAlternateTransportUrl(cleanUrl)

        _uiState.value = _uiState.value.copy(
            title = title,
            subtitle = subtitle,
            primaryUrl = cleanUrl,
            backupUrl = resolvedBackup,
            activeUrl = cleanUrl,
            isLive = isLive,
            usingLibVlc = forceVlc,
            fallbackStage = if (forceVlc) TransportFallbackStage.LIBVLC_SURFACE else TransportFallbackStage.PRIMARY,
            statusBanner = null,
            progressKey = progressKey
        )

        if (forceVlc) {
            playWithLibVlc(cleanUrl, resumePositionMs)
        } else {
            playWithExoPlayer(cleanUrl, resumePositionMs)
        }
    }

    fun retryCurrentStream() {
        val state = _uiState.value
        if (state.primaryUrl.isBlank()) return
        _uiState.value = state.copy(
            statusBanner = "Reconnexion au flux en cours...",
            fallbackStage = TransportFallbackStage.PRIMARY
        )
        startStream(
            title = state.title,
            subtitle = state.subtitle,
            primaryUrl = state.primaryUrl,
            backupUrl = state.backupUrl,
            isLive = state.isLive,
            resumePositionMs = if (state.isLive) 0L else currentPositionMs(),
            progressKey = state.progressKey
        )
    }

    fun cycleScaleMode() {
        val modes = VideoScaleMode.entries
        val next = modes[(_uiState.value.scaleMode.ordinal + 1) % modes.size]
        _uiState.value = _uiState.value.copy(
            scaleMode = next,
            statusBanner = "Format d'écran : ${next.label}"
        )
    }

    private fun playWithExoPlayer(url: String, resumePositionMs: Long = 0L) {
        stopLibVlc()
        _uiState.value = _uiState.value.copy(usingLibVlc = false, activeUrl = url, isBuffering = true)
        startBufferingWatchdog()

        val lower = url.substringBefore("?").lowercase()
        val itemBuilder = MediaItem.Builder().setUri(url)
        when {
            lower.endsWith(".m3u8") || lower.contains("/hls/") || lower.contains("playlist.m3u8") -> {
                itemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
            }
            lower.endsWith(".mpd") -> {
                itemBuilder.setMimeType(MimeTypes.APPLICATION_MPD)
            }
        }
        exoPlayer.setMediaItem(itemBuilder.build())
        exoPlayer.prepare()
        if (resumePositionMs > 0L && !_uiState.value.isLive) {
            exoPlayer.seekTo(resumePositionMs)
        }
        exoPlayer.playWhenReady = true
    }

    private fun playWithLibVlc(url: String, resumePositionMs: Long = 0L) {
        exoPlayer.stop()
        val mp = ensureLibVlcInitialized()
        _uiState.value = _uiState.value.copy(
            usingLibVlc = true,
            activeUrl = url,
            isBuffering = true,
            fallbackStage = TransportFallbackStage.LIBVLC_SURFACE
        )
        startBufferingWatchdog()

        val media = Media(libVlc, Uri.parse(url)).apply {
            setHWDecoderEnabled(true, false)
            addOption(":network-caching=1800")
            addOption(":http-user-agent=VLC/3.0.20 LibVLC/3.0.20")
        }
        mp.media = media
        media.release()
        mp.play()
        if (resumePositionMs > 0L && !_uiState.value.isLive) {
            mp.time = resumePositionMs
        }
    }

    fun recoverPlayback(reason: String) {
        val state = _uiState.value
        val currentPos = currentPositionMs()

        when (state.fallbackStage) {
            TransportFallbackStage.PRIMARY -> {
                val altUrl = state.backupUrl
                if (!altUrl.isNullOrBlank() && altUrl != state.activeUrl) {
                    val nextStage = if (altUrl.endsWith(".ts", true)) {
                        TransportFallbackStage.MANAGED_TS
                    } else {
                        TransportFallbackStage.ADAPTIVE_HLS
                    }
                    _uiState.value = state.copy(
                        fallbackStage = nextStage,
                        statusBanner = "Relais automatique (${nextStage.label})..."
                    )
                    playWithExoPlayer(altUrl, currentPos)
                } else if (state.engineMode != PlayerEngineMode.EXOPLAYER_ONLY) {
                    switchToCompatibility("Bascule automatique vers LibVLC")
                } else {
                    _uiState.value = state.copy(
                        isBuffering = false,
                        statusBanner = "Flux indisponible ($reason)"
                    )
                }
            }

            TransportFallbackStage.ADAPTIVE_HLS,
            TransportFallbackStage.MANAGED_TS -> {
                if (state.engineMode != PlayerEngineMode.EXOPLAYER_ONLY) {
                    switchToCompatibility("Bascule vers le décodeur LibVLC")
                } else {
                    _uiState.value = state.copy(
                        isBuffering = false,
                        statusBanner = "Impossible de décoder le flux avec Media3"
                    )
                }
            }

            TransportFallbackStage.LIBVLC_SURFACE -> {
                val altUrl = state.backupUrl
                if (!altUrl.isNullOrBlank() && altUrl != state.activeUrl) {
                    _uiState.value = state.copy(
                        activeUrl = altUrl,
                        statusBanner = "Essai du flux de secours sur LibVLC..."
                    )
                    playWithLibVlc(altUrl, currentPos)
                } else {
                    _uiState.value = state.copy(
                        isBuffering = false,
                        statusBanner = "Flux indisponible actuellement."
                    )
                }
            }
        }
    }

    fun switchToCompatibility(bannerMessage: String = "Mode Compatibilité LibVLC activé") {
        val state = _uiState.value
        val pos = currentPositionMs()
        _uiState.value = state.copy(
            usingLibVlc = true,
            fallbackStage = TransportFallbackStage.LIBVLC_SURFACE,
            statusBanner = bannerMessage
        )
        playWithLibVlc(state.activeUrl.ifBlank { state.primaryUrl }, pos)
    }

    fun toggleEngineManually() {
        val state = _uiState.value
        val pos = currentPositionMs()
        if (state.usingLibVlc) {
            _uiState.value = state.copy(
                usingLibVlc = false,
                fallbackStage = TransportFallbackStage.PRIMARY,
                statusBanner = "Moteur AndroidX Media3 activé"
            )
            playWithExoPlayer(state.primaryUrl, pos)
        } else {
            switchToCompatibility("Moteur LibVLC activé manuellement")
        }
    }

    fun setEngineMode(mode: PlayerEngineMode) {
        _uiState.value = _uiState.value.copy(
            engineMode = mode,
            statusBanner = "Préférence moteur : ${mode.label}"
        )
    }

    fun togglePlayPause() {
        val state = _uiState.value
        if (state.usingLibVlc) {
            val mp = vlcMediaPlayer ?: return
            if (mp.isPlaying) mp.pause() else mp.play()
        } else {
            if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
        }
    }

    fun seekBy(deltaMs: Long) {
        if (_uiState.value.isLive) return
        val dur = durationMs()
        val target = if (dur > 0L) {
            (currentPositionMs() + deltaMs).coerceIn(0L, dur)
        } else {
            (currentPositionMs() + deltaMs).coerceAtLeast(0L)
        }
        seekTo(target)
    }

    fun seekTo(positionMs: Long) {
        if (_uiState.value.isLive) return
        if (_uiState.value.usingLibVlc) {
            vlcMediaPlayer?.time = positionMs
        } else {
            exoPlayer.seekTo(positionMs)
        }
    }

    fun selectAudioLanguage(languageLabel: String) {
        val code = languageLabel.take(2).lowercase()
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
            .buildUpon()
            .setPreferredAudioLanguage(code)
            .build()
        _uiState.value = _uiState.value.copy(
            selectedAudioLanguage = languageLabel,
            statusBanner = "Piste audio : $languageLabel"
        )
    }

    fun selectSubtitleLanguage(languageLabel: String) {
        if (languageLabel.equals("Désactivés", ignoreCase = true)) {
            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
        } else {
            val code = languageLabel.take(2).lowercase()
            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setPreferredTextLanguage(code)
                .build()
        }
        _uiState.value = _uiState.value.copy(
            selectedSubtitleLanguage = languageLabel,
            statusBanner = "Sous-titres : $languageLabel"
        )
    }

    fun currentPositionMs(): Long {
        return if (_uiState.value.usingLibVlc) {
            vlcMediaPlayer?.time?.coerceAtLeast(0L) ?: 0L
        } else {
            exoPlayer.currentPosition.coerceAtLeast(0L)
        }
    }

    fun durationMs(): Long {
        return if (_uiState.value.usingLibVlc) {
            vlcMediaPlayer?.length?.coerceAtLeast(0L) ?: 0L
        } else {
            exoPlayer.duration.coerceAtLeast(0L)
        }
    }

    private fun stopLibVlc() {
        runCatching {
            if (vlcMediaPlayer?.isPlaying == true) {
                vlcMediaPlayer?.stop()
            }
        }
    }

    fun release() {
        watchdogJob?.cancel()
        playerScope.cancel()
        exoPlayer.release()
        runCatching {
            vlcMediaPlayer?.release()
            libVlc?.release()
        }
    }
}
