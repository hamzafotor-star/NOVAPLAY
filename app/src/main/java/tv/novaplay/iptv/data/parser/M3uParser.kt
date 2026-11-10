package tv.novaplay.iptv.data.parser

import tv.novaplay.iptv.data.model.LiveChannel
import java.io.BufferedReader
import java.io.StringReader

/**
 * Intelligent M3U / M3U8 parser with automatic title cleaning, resolution badge extraction
 * (`4K`, `FHD`, `HD`, `SD`), smart bouquet classification, and logical channel ordering.
 */
object M3uParser {

    private val ATTR_REGEX = Regex("""([a-zA-Z0-9_-]+)="([^"]*)"""")
    private val LIVE_TS_HLS_REGEX = Regex("""(/live/[^/]+/[^/]+/[0-9]+)\.(m3u8|ts)$""")
    private val QUALITY_TAG_REGEX = Regex("""\((2160p|4K|1080p|720p|576p|540p|504p|480p|477p|360p|240p)\)""", RegexOption.IGNORE_CASE)
    private val BRACKET_NOISE_REGEX = Regex("""\[(Not 24/7|Geo-blocked|Geo-Blocked|720p|1080p|HD|SD)\]""", RegexOption.IGNORE_CASE)

    private val BOUQUET_ORDER = listOf(
        "🇫🇷 TNT & Généraliste",
        "📰 Actualités & Info",
        "⚽ Sports Live",
        "🎬 Cinéma & Séries",
        "🎭 Divertissement",
        "🧒 Jeunesse & Manga",
        "🎵 Musique & Clips",
        "📚 Culture & Découverte",
        "🌍 Monde & Afrique",
        "📺 Autres Chaînes"
    )

    fun bouquetOrderIndex(group: String): Int {
        val idx = BOUQUET_ORDER.indexOf(group)
        return if (idx >= 0) idx else BOUQUET_ORDER.size
    }

    fun parse(m3uContent: String, generation: Int = 1): List<LiveChannel> {
        val rawChannels = mutableListOf<LiveChannel>()
        val reader = BufferedReader(StringReader(m3uContent))
        var line: String?
        var currentAttrs = emptyMap<String, String>()
        var rawTitle = ""
        var currentGroupFromExtGrp: String? = null
        var seq = 1

        while (reader.readLine().also { line = it } != null) {
            val trimmed = line?.trim()?.removePrefix("\uFEFF") ?: continue
            if (trimmed.isEmpty()) continue

            when {
                trimmed.startsWith("#EXTINF:", ignoreCase = true) -> {
                    val attrs = mutableMapOf<String, String>()
                    ATTR_REGEX.findAll(trimmed).forEach { match ->
                        attrs[match.groupValues[1].lowercase()] = match.groupValues[2]
                    }
                    currentAttrs = attrs
                    rawTitle = trimmed.substringAfterLast(",").trim()
                        .ifEmpty { attrs["tvg-name"] ?: "Chaîne #$seq" }
                }

                trimmed.startsWith("#EXTGRP:", ignoreCase = true) -> {
                    currentGroupFromExtGrp = trimmed.substringAfter(":").trim()
                }

                !trimmed.startsWith("#") && (
                    trimmed.startsWith("http://", ignoreCase = true) ||
                        trimmed.startsWith("https://", ignoreCase = true) ||
                        trimmed.startsWith("rtsp://", ignoreCase = true) ||
                        trimmed.startsWith("rtmp://", ignoreCase = true) ||
                        trimmed.startsWith("mms://", ignoreCase = true)
                    ) -> {
                    // Skip explicitly geo-blocked entries when importing huge public lists
                    if (rawTitle.contains("[Geo-blocked]", ignoreCase = true)) {
                        currentAttrs = emptyMap()
                        rawTitle = ""
                        currentGroupFromExtGrp = null
                        continue
                    }

                    val quality = extractQualityBadge(rawTitle, currentAttrs["tvg-id"].orEmpty())
                    val cleanName = cleanChannelName(rawTitle).ifEmpty { "Chaîne #$seq" }
                    val rawGroup = currentAttrs["group-title"]
                        ?.takeIf { it.isNotBlank() }
                        ?: currentGroupFromExtGrp
                        ?: "General"

                    val country = extractCountry(currentAttrs["tvg-country"], currentAttrs["tvg-id"], cleanName, rawGroup)
                    val classifiedBouquet = classifyBouquet(rawGroup, cleanName, country)
                    val isPpv = classifiedBouquet.contains("Sport", true) ||
                        rawGroup.contains("PPV", ignoreCase = true) ||
                        cleanName.contains("PPV", ignoreCase = true)
                    val priority = when (quality) {
                        "4K" -> 3
                        "FHD" -> 2
                        "HD" -> 1
                        else -> 0
                    }
                    val baseId = currentAttrs["tvg-id"]?.takeIf { it.isNotBlank() } ?: "ch_$seq"
                    val key = "${baseId}_$seq"

                    rawChannels.add(
                        LiveChannel(
                            key = key,
                            generation = generation,
                            ordinal = seq,
                            name = cleanName,
                            group = classifiedBouquet,
                            streamUrl = trimmed,
                            backupStreamUrl = deriveAlternateTransportUrl(trimmed),
                            logoUrl = currentAttrs["tvg-logo"] ?: "",
                            country = country,
                            qualityLabel = quality,
                            isPpv = isPpv,
                            priority = priority,
                            epgChannelId = currentAttrs["tvg-id"] ?: key
                        )
                    )
                    seq++
                    currentAttrs = emptyMap()
                    rawTitle = ""
                    currentGroupFromExtGrp = null
                }
            }
        }

        // Organize channels by Bouquet order first, then by HD quality priority, then alphabetically,
        // and re-assign clean sequential channel numbers (1..N)
        return rawChannels
            .sortedWith(
                compareBy<LiveChannel> { bouquetOrderIndex(it.group) }
                    .thenByDescending { it.priority }
                    .thenBy { it.name.lowercase() }
            )
            .mapIndexed { idx, ch -> ch.copy(ordinal = idx + 1) }
    }

    fun cleanChannelName(raw: String): String {
        return raw
            .replace(QUALITY_TAG_REGEX, "")
            .replace(BRACKET_NOISE_REGEX, "")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    fun extractQualityBadge(rawTitle: String, tvgId: String): String {
        val combined = "$rawTitle $tvgId".uppercase()
        return when {
            combined.contains("2160P") || combined.contains("4K") || combined.contains("UHD") -> "4K"
            combined.contains("1080P") || combined.contains("FHD") -> "FHD"
            combined.contains("720P") || combined.contains("@HD") || combined.contains(" HD") -> "HD"
            else -> "HD"
        }
    }

    fun classifyBouquet(rawGroup: String, cleanName: String, country: String): String {
        val g = rawGroup.split(";").firstOrNull()?.trim()?.lowercase().orEmpty()
        val n = cleanName.lowercase()

        return when {
            g.contains("sport") || n.contains("sport") || n.contains("equipe") || n.contains("red bull") || n.contains("foot") ->
                "⚽ Sports Live"
            g.contains("news") || g.contains("info") || g.contains("business") ||
                n.contains("info") || n.contains("news") || n.contains("bfm") || n.contains("cnews") ||
                n.contains("france 24") || n.contains("euronews") || n.contains("africanews") ||
                n.contains("africa 24") || n.contains("al24") || n.contains("20 minutes") || n.contains("canal z") ->
                "📰 Actualités & Info"
            g.contains("movie") || g.contains("cinema") || g.contains("film") || g.contains("series") ||
                n.contains("ciné") || n.contains("cinema") || n.contains("film") || n.contains("serie") || n.contains("action") ->
                "🎬 Cinéma & Séries"
            g.contains("kids") || g.contains("animation") || g.contains("anime") ||
                n.contains("adn") || n.contains("manga") || n.contains("junior") || n.contains("baby") ||
                n.contains("caillou") || n.contains("gulli") || n.contains("toon") || n.contains("kids") ->
                "🧒 Jeunesse & Manga"
            g.contains("music") || n.contains("music") || n.contains("nrj") || n.contains("trace") ||
                n.contains("c star") || n.contains("cstar") || n.contains("radio") || n.contains("hit") ->
                "🎵 Musique & Clips"
            g.contains("documentary") || g.contains("culture") || g.contains("education") || g.contains("science") ||
                n.contains("doc") || n.contains("ARTE") || n.contains("histoire") || n.contains("découverte") ->
                "📚 Culture & Découverte"
            g.contains("entertainment") || n.contains("6ter") || n.contains("carac") || n.contains("w9") || n.contains("tmc") ->
                "🎭 Divertissement"
            country in setOf("MA", "DZ", "TN", "SN", "CI", "CM", "BJ") && (g == "general" || g == "undefined" || g == "religious") ->
                "🌍 Monde & Afrique"
            g == "general" || g == "undefined" || country in setOf("FR", "BE", "CH", "CA") ->
                "🇫🇷 TNT & Généraliste"
            else -> "📺 Autres Chaînes"
        }
    }

    fun deriveAlternateTransportUrl(url: String): String? {
        val match = LIVE_TS_HLS_REGEX.find(url)
        if (match != null) {
            val prefix = url.substring(0, match.range.first) + match.groupValues[1]
            val ext = match.groupValues[2].lowercase()
            return if (ext == "m3u8") "$prefix.ts" else "$prefix.m3u8"
        }
        return when {
            url.endsWith(".m3u8", ignoreCase = true) -> url.removeSuffix(".m3u8") + ".ts"
            url.endsWith(".ts", ignoreCase = true) -> url.removeSuffix(".ts") + ".m3u8"
            else -> null
        }
    }

    private fun extractCountry(attrCountry: String?, tvgId: String?, title: String, group: String): String {
        if (!attrCountry.isNullOrBlank()) return attrCountry.uppercase().take(2)
        if (!tvgId.isNullOrBlank() && tvgId.contains(".")) {
            val suffix = tvgId.substringAfterLast(".").substringBefore("@").uppercase()
            if (suffix.length == 2) return suffix
        }
        val combined = "$group $title".uppercase()
        return when {
            combined.contains("FR|") || combined.contains("[FR]") || combined.contains("FRANCE") -> "FR"
            combined.contains("BE|") || combined.contains("BELGI") || combined.contains("RTBF") || combined.contains("BX1") -> "BE"
            combined.contains("CH|") || combined.contains("SUISSE") || combined.contains("CARAC") -> "CH"
            combined.contains("MA|") || combined.contains("MAROC") || combined.contains("2M") -> "MA"
            combined.contains("DZ|") || combined.contains("ALGER") || combined.contains("AL24") -> "DZ"
            combined.contains("SN|") || combined.contains("SENEGAL") || combined.contains("A2I") -> "SN"
            combined.contains("CI|") || combined.contains("IVOIRE") || combined.contains("7 INFO") -> "CI"
            combined.contains("CM|") || combined.contains("CAMEROUN") || combined.contains("CAM 10") -> "CM"
            combined.contains("BJ|") || combined.contains("BENIN") -> "BJ"
            combined.contains("UK|") || combined.contains("[UK]") || combined.contains("ENGLISH") -> "UK"
            combined.contains("US|") || combined.contains("[US]") || combined.contains("USA") -> "US"
            else -> "FR"
        }
    }
}
