package tv.novaplay.iptv.data.model

enum class SourceType {
    M3U_URL,
    XTREAM_CODES
}

data class PlaylistSource(
    val id: String,
    val name: String,
    val type: SourceType,
    val serverUrl: String,
    val username: String = "",
    val password: String = "",
    val epgUrl: String = "",
    val lastSyncTimeMs: Long = 0L,
    val channelCount: Int = 0
)

/**
 * Video aspect ratio / screen adaptation modes so the video fills or fits any smartphone/TV screen.
 */
enum class VideoScaleMode(val label: String, val shortBadge: String) {
    FIT("Ajuster (16:9 Original)", "16:9"),
    ZOOM_CROP("Zoom Plein Écran (Sans Bordures)", "ZOOM"),
    FILL_STRETCH("Étirer Plein Écran (100%)", "PLEIN"),
    RATIO_4_3("Format 4:3 Classique", "4:3")
}

/**
 * Channel sorting modes for structured organization.
 */
enum class ChannelSortMode(val label: String) {
    BY_GROUP("Par Bouquet"),
    BY_NUMBER("Par Numéro"),
    BY_NAME("Nom A → Z"),
    BY_QUALITY("Qualité HD")
}

data class LiveChannel(
    val key: String,
    val generation: Int = 1,
    val ordinal: Int,
    val name: String,
    val group: String,
    val streamUrl: String,
    val backupStreamUrl: String? = null,
    val logoUrl: String = "",
    val country: String = "FR",
    val qualityLabel: String = "HD",
    val isPpv: Boolean = false,
    val priority: Int = 0,
    val epgChannelId: String = "",
    val isFavorite: Boolean = false,
    val isHidden: Boolean = false,
    val currentProgram: EpgProgram? = null,
    val nextProgram: EpgProgram? = null
) {
    val countryFlag: String
        get() = when (country.uppercase()) {
            "FR" -> "🇫🇷"
            "BE" -> "🇧🇪"
            "CH" -> "🇨🇭"
            "CA" -> "🇨🇦"
            "MA" -> "🇲🇦"
            "DZ" -> "🇩🇿"
            "TN" -> "🇹🇳"
            "SN" -> "🇸🇳"
            "CI" -> "🇨🇮"
            "CM" -> "🇨🇲"
            "BJ" -> "🇧🇯"
            "UK", "GB" -> "🇬🇧"
            "US" -> "🇺🇸"
            "ES" -> "🇪🇸"
            "IT" -> "🇮🇹"
            "DE" -> "🇩🇪"
            "AR" -> "🌍"
            else -> "📺"
        }
}

data class EpgProgram(
    val id: String,
    val channelId: String,
    val title: String,
    val description: String,
    val category: String = "Direct",
    val startTimeMs: Long,
    val endTimeMs: Long
) {
    fun progressFraction(nowMs: Long = System.currentTimeMillis()): Float {
        val total = (endTimeMs - startTimeMs).coerceAtLeast(1L)
        return ((nowMs - startTimeMs).toFloat() / total.toFloat()).coerceIn(0f, 1f)
    }
}

enum class VodType {
    MOVIE,
    SERIES
}

data class SubtitleTrackInfo(
    val id: String,
    val languageCode: String,
    val label: String,
    val url: String? = null
)

data class AudioTrackInfo(
    val id: String,
    val languageCode: String,
    val label: String,
    val codec: String = "AAC"
)

data class EpisodeItem(
    val id: String,
    val seriesId: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val title: String,
    val overview: String,
    val durationMinutes: Int,
    val streamUrl: String,
    val thumbnailUrl: String = ""
)

data class VodItem(
    val id: String,
    val title: String,
    val type: VodType,
    val category: String,
    val releaseYear: Int,
    val rating: String,
    val durationLabel: String,
    val overview: String,
    val posterUrl: String = "",
    val backdropUrl: String = "",
    val streamUrl: String,
    val containerExtension: String = "m3u8",
    val availableLanguages: List<String> = listOf("FR", "VOSTFR", "Multi"),
    val subtitles: List<SubtitleTrackInfo> = emptyList(),
    val audioTracks: List<AudioTrackInfo> = emptyList(),
    val episodes: List<EpisodeItem> = emptyList(),
    val isFavorite: Boolean = false
)

data class WatchProgress(
    val progressKey: String,
    val contentId: String,
    val episodeId: String? = null,
    val title: String,
    val subtitle: String = "",
    val positionMs: Long,
    val durationMs: Long,
    val updatedAtMs: Long = System.currentTimeMillis()
) {
    val progressFraction: Float
        get() = if (durationMs <= 0L) 0f else (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
}

data class SportEvent(
    val id: String,
    val sport: String,
    val competition: String,
    val homeTeam: String,
    val awayTeam: String,
    val kickoffLabel: String,
    val isLive: Boolean,
    val isPpv: Boolean = false,
    val scoreOrStatus: String = "EN DIRECT",
    val channelKeys: List<String> = emptyList(),
    val isFavorite: Boolean = false
)

enum class PlayerEngineMode(val label: String) {
    AUTO_HYBRID("Auto Hybride (Media3 ExoPlayer → LibVLC)"),
    EXOPLAYER_ONLY("AndroidX Media3 (ExoPlayer HLS/DASH)"),
    LIBVLC_COMPATIBILITY("Compatibilité LibVLC (MPEG-TS / AC3 / RTSP)")
}

enum class TransportFallbackStage(val label: String) {
    PRIMARY("Flux Principal"),
    ADAPTIVE_HLS("Relais Adaptatif HLS (.m3u8)"),
    MANAGED_TS("Flux MPEG-TS Direct (.ts)"),
    LIBVLC_SURFACE("Décodeur Natif LibVLC")
}
