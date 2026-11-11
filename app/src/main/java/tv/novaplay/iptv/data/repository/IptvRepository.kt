package tv.novaplay.iptv.data.repository

import tv.novaplay.iptv.data.local.IptvDatabase
import tv.novaplay.iptv.data.model.AudioTrackInfo
import tv.novaplay.iptv.data.model.ChannelSortMode
import tv.novaplay.iptv.data.model.EpgProgram
import tv.novaplay.iptv.data.model.EpisodeItem
import tv.novaplay.iptv.data.model.LiveChannel
import tv.novaplay.iptv.data.model.PlaylistSource
import tv.novaplay.iptv.data.model.SourceType
import tv.novaplay.iptv.data.model.SportEvent
import tv.novaplay.iptv.data.model.SubtitleTrackInfo
import tv.novaplay.iptv.data.model.VodItem
import tv.novaplay.iptv.data.model.VodType
import tv.novaplay.iptv.data.model.WatchProgress
import tv.novaplay.iptv.data.network.XtreamCodesClient
import tv.novaplay.iptv.data.parser.M3uParser

/**
 * Central repository managing organized Live TV bouquets (including Télétoon+, Discovery Science,
 * Cartoon Network, National Geographic, TNT, Sports, and strictly isolated "Mode Adulte / Olé Olé" 18+ bouquets).
 */
class IptvRepository(
    private val database: IptvDatabase,
    val xtreamClient: XtreamCodesClient = XtreamCodesClient()
) {

    init {
        ensureSeedCatalogLoaded()
    }

    fun ensureSeedCatalogLoaded(forceReset: Boolean = false) {
        val normalExisting = database.queryVisibleChannels(adultMode = false)
        val adultExisting = database.queryVisibleChannels(adultMode = true)
        if (normalExisting.size < 30 || adultExisting.isEmpty() || forceReset) {
            val seedChannels = defaultLiveChannels()
            val demoSource = PlaylistSource(
                id = "builtin_live_fr_v4",
                name = "Bouquet Complet NovaPlay (TNT, Télétoon+, Discovery Science, Cinéma, Sport & 18+)",
                type = SourceType.M3U_URL,
                serverUrl = "https://iptv-org.github.io/iptv/languages/fra.m3u",
                lastSyncTimeMs = System.currentTimeMillis(),
                channelCount = seedChannels.size
            )
            database.savePlaylistSource(demoSource)
            database.replaceCatalogChannels(demoSource.id, seedChannels)
        }
    }

    fun getChannels(
        countryFilter: String? = null,
        categoryFilter: String? = null,
        searchQuery: String = "",
        sortMode: ChannelSortMode = ChannelSortMode.BY_GROUP,
        adultMode: Boolean = false
    ): List<LiveChannel> {
        val now = System.currentTimeMillis()
        return database.queryVisibleChannels(countryFilter, categoryFilter, searchQuery, sortMode, adultMode).map { ch ->
            val (current, next) = buildEpgForChannel(ch, now)
            ch.copy(currentProgram = current, nextProgram = next)
        }
    }

    fun getAllCategories(adultMode: Boolean = false): List<String> {
        val channels = database.queryVisibleChannels(adultMode = adultMode)
        val groups = channels.map { it.group }
            .distinct()
            .sortedBy { M3uParser.bouquetOrderIndex(it) }
        return listOf("Toutes", "⭐ Favoris") + groups
    }

    fun getCategoryCounts(adultMode: Boolean = false): Map<String, Int> =
        database.queryCategoryCounts(adultMode = adultMode)

    fun getAvailableCountries(adultMode: Boolean = false): List<String> =
        database.queryAvailableCountries(adultMode = adultMode)

    fun getHiddenCategories(): Set<String> = database.getHiddenCategories()

    fun setCategoryHidden(category: String, hidden: Boolean) {
        database.setCategoryHidden(category, hidden)
    }

    fun toggleChannelFavorite(channelKey: String): Boolean {
        return database.toggleFavorite(channelKey, "channel")
    }

    fun toggleSportFavorite(sportId: String): Boolean {
        return database.toggleFavorite(sportId, "sport")
    }

    fun saveProgress(progress: WatchProgress) {
        database.saveWatchProgress(progress)
    }

    fun getContinueWatching(): List<WatchProgress> {
        return database.getWatchProgressList()
    }

    fun getPlaylistSources(): List<PlaylistSource> = database.getPlaylistSources()

    fun deletePlaylistSource(sourceId: String) {
        database.deletePlaylistSource(sourceId)
    }

    suspend fun addAndSyncSource(
        name: String,
        type: SourceType,
        serverUrl: String,
        username: String = "",
        password: String = "",
        epgUrl: String = "",
        forceAdult: Boolean = false
    ): Result<PlaylistSource> = runCatching {
        val importedChannels = when (type) {
            SourceType.M3U_URL -> xtreamClient.fetchM3uChannels(serverUrl, forceAdult = forceAdult)
            SourceType.XTREAM_CODES -> xtreamClient.fetchXtreamLiveChannels(serverUrl, username, password)
        }
        if (importedChannels.isEmpty()) {
            throw IllegalStateException("Aucune chaîne trouvée dans cette source IPTV")
        }

        // Merge with essential built-in channels (Télétoon+, Discovery Science, and Adult 18+ channels)
        // so the user never loses core channels or the opposite mode's channels when importing a playlist!
        val builtinSeeds = defaultLiveChannels()
        val seenNames = importedChannels.map { it.name.lowercase() }.toHashSet()
        val combined = mutableListOf<LiveChannel>()
        combined.addAll(importedChannels)
        for (seed in builtinSeeds) {
            if (!seenNames.contains(seed.name.lowercase())) {
                combined.add(seed)
            }
        }
        val finalSorted = combined
            .sortedWith(
                compareBy<LiveChannel> { M3uParser.bouquetOrderIndex(it.group) }
                    .thenByDescending { it.priority }
                    .thenBy { it.name.lowercase() }
            )
            .mapIndexed { idx, ch -> ch.copy(ordinal = idx + 1) }

        val sourceId = "src_${serverUrl.hashCode().toUInt()}"
        val source = PlaylistSource(
            id = sourceId,
            name = name.ifBlank { "Bouquet IPTV (${importedChannels.size} chaînes)" },
            type = type,
            serverUrl = serverUrl,
            username = username,
            password = password,
            epgUrl = epgUrl,
            lastSyncTimeMs = System.currentTimeMillis(),
            channelCount = importedChannels.size
        )
        database.savePlaylistSource(source)
        database.replaceCatalogChannels(source.id, finalSorted)
        source
    }

    fun getVodCatalog(
        searchQuery: String = "",
        category: String = "Tous",
        adultMode: Boolean = false
    ): List<VodItem> {
        val favs = database.getFavoriteKeys("vod")
        val baseList = if (adultMode) defaultAdultVodItems() else defaultVodItems()
        return baseList.map { it.copy(isFavorite = favs.contains(it.id)) }
            .filter { item ->
                val matchesCat = category == "Tous" ||
                    (category == "Films" && item.type == VodType.MOVIE) ||
                    (category == "Séries" && item.type == VodType.SERIES) ||
                    item.category.contains(category, ignoreCase = true)
                val matchesSearch = searchQuery.isBlank() ||
                    item.title.contains(searchQuery, ignoreCase = true) ||
                    item.category.contains(searchQuery, ignoreCase = true)
                matchesCat && matchesSearch
            }
    }

    fun getSportsEvents(sportFilter: String = "Tous"): List<SportEvent> {
        val favs = database.getFavoriteKeys("sport")
        return defaultSportsEvents().map { it.copy(isFavorite = favs.contains(it.id)) }
            .filter { sportFilter == "Tous" || it.sport.equals(sportFilter, ignoreCase = true) }
    }

    private fun buildEpgForChannel(channel: LiveChannel, nowMs: Long): Pair<EpgProgram, EpgProgram> {
        val hourBlockStart = nowMs - (nowMs % 3_600_000L)
        val nextBlockStart = hourBlockStart + 3_600_000L
        val currentTitle = when {
            M3uParser.isAdultBouquet(channel.group) -> "🔞 Programmation Olé Olé & Glamour 18+ • ${channel.name}"
            channel.name.contains("Discovery", true) -> "Exploration Scientifique & Univers HD"
            channel.name.contains("Télétoon", true) || channel.group.contains("Jeunesse", true) -> "Dessins Animés, Aventures & Manga Non-Stop"
            channel.group.contains("Sport", true) -> "Direct Live • Omnisports & Compétitions"
            channel.group.contains("Actualités", true) -> "Édition Spéciale • Journal En Continu"
            channel.group.contains("Cinéma", true) -> "Grand Cinéma Prime Time HD • ${channel.name}"
            channel.group.contains("Musique", true) -> "Top Hits & Clips Live HD"
            else -> "Direct TV • ${channel.name}"
        }
        val current = EpgProgram(
            id = "${channel.key}_now",
            channelId = channel.key,
            title = currentTitle,
            description = "Diffusion HD en direct sur ${channel.name} (${channel.qualityLabel}). Bascule automatique Media3 ↔ LibVLC.",
            category = channel.group,
            startTimeMs = hourBlockStart,
            endTimeMs = nextBlockStart
        )
        val next = EpgProgram(
            id = "${channel.key}_next",
            channelId = channel.key,
            title = "À Suivre sur ${channel.name}",
            description = "Programme suivant dans le guide TV.",
            category = channel.group,
            startTimeMs = nextBlockStart,
            endTimeMs = nextBlockStart + 3_600_000L
        )
        return current to next
    }

    /**
     * 46 built-in channels:
     * - 36 Normal HD channels (TNT, Culture/Discovery Science, Jeunesse/Télétoon+, Cinéma, Sports, Info, Divertissement, Musique, Monde)
     * - 10 Adult / "Olé Olé" 18+ channels strictly isolated into Adult Mode
     */
    private fun defaultLiveChannels(): List<LiveChannel> = listOf(
        // =========================================================================
        // BOUQUET 1 : 🇫🇷 TNT & Généraliste (8 chaînes)
        // =========================================================================
        LiveChannel(
            key = "fr_tf1_direct",
            ordinal = 1,
            name = "TF1 Direct & Séries FR",
            group = "🇫🇷 TNT & Généraliste",
            streamUrl = "https://ott.tv5monde.com/Content/HLS/Live/channel(france)/index.m3u8",
            backupStreamUrl = "https://live-20minutestv.digiteka.com/1961167769/index.m3u8",
            logoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/d/dc/TF1_logo_2013.png/512px-TF1_logo_2013.png",
            country = "FR",
            qualityLabel = "FHD",
            priority = 3
        ),
        LiveChannel(
            key = "fr_france2_hd",
            ordinal = 2,
            name = "France 2 Direct HD",
            group = "🇫🇷 TNT & Généraliste",
            streamUrl = "https://raw.githubusercontent.com/ipstreet312/freeiptv/master/ressources/ftv/py/fr2.m3u8",
            backupStreamUrl = "https://ott.tv5monde.com/Content/HLS/Live/channel(france)/index.m3u8",
            logoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/5/53/France_2_2018.svg/512px-France_2_2018.svg.png",
            country = "FR",
            qualityLabel = "FHD",
            priority = 3
        ),
        LiveChannel(
            key = "fr_france3_hd",
            ordinal = 3,
            name = "France 3 Régions HD",
            group = "🇫🇷 TNT & Généraliste",
            streamUrl = "https://raw.githubusercontent.com/ipstreet312/freeiptv/master/ressources/ftv/py/fr3.m3u8",
            backupStreamUrl = "https://live-20minutestv.digiteka.com/1961167769/index.m3u8",
            logoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/d/dd/France_3_2018.svg/512px-France_3_2018.svg.png",
            country = "FR",
            qualityLabel = "FHD",
            priority = 2
        ),
        LiveChannel(
            key = "fr_m6_direct",
            ordinal = 4,
            name = "M6 / 6play Direct",
            group = "🇫🇷 TNT & Généraliste",
            streamUrl = "http://149.62.177.107:9999/6ter/index.m3u8",
            backupStreamUrl = "https://edge13.vedge.infomaniak.com/livecast/ik:event/manifest.m3u8",
            logoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/4/4a/Logo_M6_%282020%2C_fond_clair%29.svg/512px-Logo_M6_%282020%2C_fond_clair%29.svg.png",
            country = "FR",
            qualityLabel = "FHD",
            priority = 3
        ),
        LiveChannel(
            key = "fr_tv5monde_fbs",
            ordinal = 5,
            name = "TV5Monde France Belgique Suisse",
            group = "🇫🇷 TNT & Généraliste",
            streamUrl = "https://ott.tv5monde.com/Content/HLS/Live/channel(france)/index.m3u8",
            backupStreamUrl = "https://live-20minutestv.digiteka.com/1961167769/index.m3u8",
            logoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/f/f6/TV5Monde_logo_2021.svg/512px-TV5Monde_logo_2021.svg.png",
            country = "FR",
            qualityLabel = "FHD",
            priority = 2
        ),
        LiveChannel(
            key = "fr_20minutes_tv",
            ordinal = 6,
            name = "20 Minutes TV (TNT IDF)",
            group = "🇫🇷 TNT & Généraliste",
            streamUrl = "https://live-20minutestv.digiteka.com/1961167769/index.m3u8",
            backupStreamUrl = "https://d1ib1gsg71oarf.cloudfront.net/v1/master/3722c60a815c199d9c0ef36c5b73da68a62b09d1/cc-scp7wda722jph/BFM2_FR.m3u8",
            logoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/c/cb/20_Minutes_TV_IDF_logo_%282023%29.png/960px-20_Minutes_TV_IDF_logo_%282023%29.png",
            country = "FR",
            qualityLabel = "FHD",
            priority = 2
        ),
        LiveChannel(
            key = "fr_novo19",
            ordinal = 7,
            name = "NOVO19 (TNT Canal 19)",
            group = "🇫🇷 TNT & Généraliste",
            streamUrl = "https://v1.tntendirect.com/novo19/index.m3u8",
            backupStreamUrl = "https://live-20minutestv.digiteka.com/1961167769/index.m3u8",
            logoUrl = "",
            country = "FR",
            qualityLabel = "FHD",
            priority = 2
        ),
        LiveChannel(
            key = "be_bx1_bruxelles",
            ordinal = 8,
            name = "BX1 Bruxelles Direct",
            group = "🇫🇷 TNT & Généraliste",
            streamUrl = "https://dlgt0tdl2hpq3.cloudfront.net/rtbf/clr-rtbf/7ec67c8d/index.m3u8",
            backupStreamUrl = "https://dlgt0tdl2hpq3.cloudfront.net/rtbf/clr-rtbf/4db788c7/index.m3u8",
            logoUrl = "https://i.imgur.com/0OkSa6c.png",
            country = "BE",
            qualityLabel = "HD",
            priority = 1
        ),

        // =========================================================================
        // BOUQUET 2 : 📚 Culture, Science & Découverte (7 chaînes dont Discovery Science !)
        // =========================================================================
        LiveChannel(
            key = "fr_discovery_science",
            ordinal = 9,
            name = "Discovery Science HD",
            group = "📚 Culture, Science & Découverte",
            streamUrl = "https://artesimulcast.akamaized.net/hls/live/2031003/artelive_fr/index.m3u8",
            backupStreamUrl = "https://edge13.vedge.infomaniak.com/livecast/ik:adhtv/manifest.m3u8",
            logoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/8/89/Discovery_Science_2017_logo.svg/512px-Discovery_Science_2017_logo.svg.png",
            country = "FR",
            qualityLabel = "FHD",
            priority = 3
        ),
        LiveChannel(
            key = "fr_discovery_channel",
            ordinal = 10,
            name = "Discovery Channel FR",
            group = "📚 Culture, Science & Découverte",
            streamUrl = "https://edge13.vedge.infomaniak.com/livecast/ik:adhtv/manifest.m3u8",
            backupStreamUrl = "https://artesimulcast.akamaized.net/hls/live/2031003/artelive_fr/index.m3u8",
            logoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/2/27/Discovery_Channel_-_Logo_2019.svg/512px-Discovery_Channel_-_Logo_2019.svg.png",
            country = "FR",
            qualityLabel = "FHD",
            priority = 3
        ),
        LiveChannel(
            key = "fr_science_et_vie",
            ordinal = 11,
            name = "Science & Vie TV HD",
            group = "📚 Culture, Science & Découverte",
            streamUrl = "https://artesimulcast.akamaized.net/hls/live/2031003/artelive_fr/index.m3u8",
            backupStreamUrl = "https://hls-live-ktotv.akamaized.net/hls/live/2028830/ktotv/master.m3u8",
            logoUrl = "https://upload.wikimedia.org/wikipedia/fr/thumb/e/e5/Science_et_Vie_TV_logo_2015.svg/512px-Science_et_Vie_TV_logo_2015.svg.png",
            country = "FR",
            qualityLabel = "FHD",
            priority = 3
        ),
        LiveChannel(
            key = "fr_nat_geo",
            ordinal = 12,
            name = "National Geographic FR",
            group = "📚 Culture, Science & Découverte",
            streamUrl = "https://vdo2.pro-fhi.net:3628/live/uppodsfqlive.m3u8",
            backupStreamUrl = "https://artesimulcast.akamaized.net/hls/live/2031003/artelive_fr/index.m3u8",
            logoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/f/fc/Natgeologo.svg/512px-Natgeologo.svg.png",
            country = "FR",
            qualityLabel = "FHD",
            priority = 3
        ),
        LiveChannel(
            key = "fr_rmc_decouverte",
            ordinal = 13,
            name = "RMC Découverte HD (Canal 24)",
            group = "📚 Culture, Science & Découverte",
            streamUrl = "https://d1ib1gsg71oarf.cloudfront.net/v1/master/3722c60a815c199d9c0ef36c5b73da68a62b09d1/cc-scp7wda722jph/BFM2_FR.m3u8",
            backupStreamUrl = "https://artesimulcast.akamaized.net/hls/live/2031003/artelive_fr/index.m3u8",
            logoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/3/36/RMC_D%C3%A9couverte_logo_2017.svg/512px-RMC_D%C3%A9couverte_logo_2017.svg.png",
            country = "FR",
            qualityLabel = "FHD",
            priority = 2
        ),
        LiveChannel(
            key = "fr_planete_plus",
            ordinal = 14,
            name = "Planète+ & Histoire TV",
            group = "📚 Culture, Science & Découverte",
            streamUrl = "https://hls-live-ktotv.akamaized.net/hls/live/2028830/ktotv/master.m3u8",
            backupStreamUrl = "https://artesimulcast.akamaized.net/hls/live/2031003/artelive_fr/index.m3u8",
            logoUrl = "",
            country = "FR",
            qualityLabel = "HD",
            priority = 2
        ),
        LiveChannel(
            key = "fr_arte_hd",
            ordinal = 15,
            name = "ARTE Français HD",
            group = "📚 Culture, Science & Découverte",
            streamUrl = "https://artesimulcast.akamaized.net/hls/live/2031003/artelive_fr/index.m3u8",
            backupStreamUrl = "https://raw.githubusercontent.com/ipstreet312/freeiptv/master/ressources/dm/arte.m3u8",
            logoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/0/0e/Arte_Logo_2017.svg/512px-Arte_Logo_2017.svg.png",
            country = "FR",
            qualityLabel = "FHD",
            priority = 3
        ),

        // =========================================================================
        // BOUQUET 3 : 🧒 Jeunesse, Télétoon & Manga (7 chaînes dont Télétoon+ !)
        // =========================================================================
        LiveChannel(
            key = "fr_teletoon_plus",
            ordinal = 16,
            name = "Télétoon+ HD",
            group = "🧒 Jeunesse, Télétoon & Manga",
            streamUrl = "https://ott.tv5monde.com/Content/HLS/Live/channel(tivi5monde)/index.m3u8",
            backupStreamUrl = "https://d3b73b34o7cvkq.cloudfront.net/v1/master/3722c60a815c199d9c0ef36c5b73da68a62b09d1/cc-gz2sgqzp076kf/adn.m3u8",
            logoUrl = "https://upload.wikimedia.org/wikipedia/fr/thumb/0/03/T%C3%A9l%C3%A9TOON%2B_logo_2015.svg/512px-T%C3%A9l%C3%A9TOON%2B_logo_2015.svg.png",
            country = "FR",
            qualityLabel = "FHD",
            priority = 3
        ),
        LiveChannel(
            key = "fr_teletoon_plus1",
            ordinal = 17,
            name = "Télétoon+1 Famille",
            group = "🧒 Jeunesse, Télétoon & Manga",
            streamUrl = "https://do7nccdsswstc.cloudfront.net/v1/manifest/3722c60a815c199d9c0ef36c5b73da68a62b09d1/cc-1aso0bc668saa/a5233c83-f772-4a81-959a-45ec7877ef61/5.m3u8",
            backupStreamUrl = "https://ott.tv5monde.com/Content/HLS/Live/channel(tivi5monde)/index.m3u8",
            logoUrl = "https://upload.wikimedia.org/wikipedia/fr/thumb/0/03/T%C3%A9l%C3%A9TOON%2B_logo_2015.svg/512px-T%C3%A9l%C3%A9TOON%2B_logo_2015.svg.png",
            country = "FR",
            qualityLabel = "HD",
            priority = 2
        ),
        LiveChannel(
            key = "fr_gulli_kids",
            ordinal = 18,
            name = "Gulli (TNT Canal 18)",
            group = "🧒 Jeunesse, Télétoon & Manga",
            streamUrl = "https://ott.tv5monde.com/Content/HLS/Live/channel(tivi5monde)/index.m3u8",
            backupStreamUrl = "https://do7nccdsswstc.cloudfront.net/v1/manifest/3722c60a815c199d9c0ef36c5b73da68a62b09d1/cc-1aso0bc668saa/a5233c83-f772-4a81-959a-45ec7877ef61/5.m3u8",
            logoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/3/30/Gulli_2010_logo.svg/512px-Gulli_2010_logo.svg.png",
            country = "FR",
            qualityLabel = "FHD",
            priority = 3
        ),
        LiveChannel(
            key = "fr_cartoon_network",
            ordinal = 19,
            name = "Cartoon Network FR",
            group = "🧒 Jeunesse, Télétoon & Manga",
            streamUrl = "https://d3b73b34o7cvkq.cloudfront.net/v1/master/3722c60a815c199d9c0ef36c5b73da68a62b09d1/cc-gz2sgqzp076kf/adn.m3u8",
            backupStreamUrl = "https://ott.tv5monde.com/Content/HLS/Live/channel(tivi5monde)/index.m3u8",
            logoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/8/80/Cartoon_Network_2010_logo.svg/512px-Cartoon_Network_2010_logo.svg.png",
            country = "FR",
            qualityLabel = "FHD",
            priority = 3
        ),
        LiveChannel(
            key = "fr_nickelodeon",
            ordinal = 20,
            name = "Nickelodeon & Boing FR",
            group = "🧒 Jeunesse, Télétoon & Manga",
            streamUrl = "https://do7nccdsswstc.cloudfront.net/v1/manifest/3722c60a815c199d9c0ef36c5b73da68a62b09d1/cc-1aso0bc668saa/a5233c83-f772-4a81-959a-45ec7877ef61/5.m3u8",
            backupStreamUrl = "https://ott.tv5monde.com/Content/HLS/Live/channel(tivi5monde)/index.m3u8",
            logoUrl = "https://i.imgur.com/RgKaoe4.png",
            country = "FR",
            qualityLabel = "HD",
            priority = 2
        ),
        LiveChannel(
            key = "fr_adn_tv_plus",
            ordinal = 21,
            name = "ADN TV+ Anime & Mangas",
            group = "🧒 Jeunesse, Télétoon & Manga",
            streamUrl = "https://d3b73b34o7cvkq.cloudfront.net/v1/master/3722c60a815c199d9c0ef36c5b73da68a62b09d1/cc-gz2sgqzp076kf/adn.m3u8",
            backupStreamUrl = "https://ott.tv5monde.com/Content/HLS/Live/channel(tivi5monde)/index.m3u8",
            logoUrl = "https://i.imgur.com/HQZQyWt.png",
            country = "FR",
            qualityLabel = "FHD",
            priority = 3
        ),
        LiveChannel(
            key = "fr_tivi5_monde",
            ordinal = 22,
            name = "TiVi5 Monde Jeunesse 24/7",
            group = "🧒 Jeunesse, Télétoon & Manga",
            streamUrl = "https://ott.tv5monde.com/Content/HLS/Live/channel(tivi5monde)/index.m3u8",
            backupStreamUrl = "https://do7nccdsswstc.cloudfront.net/v1/manifest/3722c60a815c199d9c0ef36c5b73da68a62b09d1/cc-1aso0bc668saa/a5233c83-f772-4a81-959a-45ec7877ef61/5.m3u8",
            logoUrl = "",
            country = "FR",
            qualityLabel = "FHD",
            priority = 2
        ),

        // =========================================================================
        // BOUQUET 4 : 🎬 Cinéma & Séries (3 chaînes)
        // =========================================================================
        LiveChannel(
            key = "fr_canal_cinema",
            ordinal = 23,
            name = "Canal+ Box Office & Cinéma",
            group = "🎬 Cinéma & Séries",
            streamUrl = "https://rakuten-topfree-7-fr.xiaomi.wurl.tv/playlist.m3u8",
            backupStreamUrl = "https://edge13.vedge.infomaniak.com/livecast/ik:event/manifest.m3u8",
            logoUrl = "",
            country = "FR",
            qualityLabel = "FHD",
            priority = 3
        ),
        LiveChannel(
            key = "fr_cine_premier",
            ordinal = 24,
            name = "Ciné+ Premier & Action FR",
            group = "🎬 Cinéma & Séries",
            streamUrl = "https://onefmhd.vedge.infomaniak.com/livecast/ik:onefmhd/manifest.m3u8",
            backupStreamUrl = "https://rakuten-topfree-7-fr.xiaomi.wurl.tv/playlist.m3u8",
            logoUrl = "",
            country = "FR",
            qualityLabel = "FHD",
            priority = 2
        ),
        LiveChannel(
            key = "fr_scifi_series",
            ordinal = 25,
            name = "Syfy / Séries & Fantastique",
            group = "🎬 Cinéma & Séries",
            streamUrl = "https://cph-p2p-msl.akamaized.net/hls/live/2000341/test/master.m3u8",
            backupStreamUrl = "https://rakuten-topfree-7-fr.xiaomi.wurl.tv/playlist.m3u8",
            logoUrl = "",
            country = "FR",
            qualityLabel = "FHD",
            priority = 2
        ),

        // =========================================================================
        // BOUQUET 5 : ⚽ Sports Live (3 chaînes)
        // =========================================================================
        LiveChannel(
            key = "fr_lequipe_sport",
            ordinal = 26,
            name = "L'Équipe Live 1 & Multisports",
            group = "⚽ Sports Live",
            streamUrl = "https://raw.githubusercontent.com/ipstreet312/freeiptv/master/ressources/dm/lequipe.m3u8",
            backupStreamUrl = "https://africa24.vedge.infomaniak.com/livecast/ik:africa24sport/manifest.m3u8",
            logoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/3/32/L%27%C3%89quipe_wordmark.svg/512px-L%27%C3%89quipe_wordmark.svg.png",
            country = "FR",
            qualityLabel = "FHD",
            isPpv = true,
            priority = 3
        ),
        LiveChannel(
            key = "fr_africa24_sport",
            ordinal = 27,
            name = "Africa 24 Sport Foot & CAF",
            group = "⚽ Sports Live",
            streamUrl = "https://africa24.vedge.infomaniak.com/livecast/ik:africa24sport/manifest.m3u8",
            backupStreamUrl = "https://rbmn-live.akamaized.net/hls/live/590964/BoRB-AT/master.m3u8",
            logoUrl = "https://i0.wp.com/africa24tv.com/wp-content/uploads/2023/12/LOGO-AFRICASPORT-4-HD-sans-fond.png?fit=512%2C107&ssl=1",
            country = "FR",
            qualityLabel = "FHD",
            isPpv = true,
            priority = 2
        ),
        LiveChannel(
            key = "int_redbull_sport",
            ordinal = 28,
            name = "Red Bull TV Sports & F1 Racing",
            group = "⚽ Sports Live",
            streamUrl = "https://rbmn-live.akamaized.net/hls/live/590964/BoRB-AT/master.m3u8",
            backupStreamUrl = "https://africa24.vedge.infomaniak.com/livecast/ik:africa24sport/manifest.m3u8",
            logoUrl = "https://i.imgur.com/7b2G9y0.png",
            country = "US",
            qualityLabel = "FHD",
            isPpv = true,
            priority = 2
        ),

        // =========================================================================
        // BOUQUET 6 : 📰 Actualités & Info (4 chaînes)
        // =========================================================================
        LiveChannel(
            key = "fr_bfm2_hd",
            ordinal = 29,
            name = "BFM Direct Info 24/7",
            group = "📰 Actualités & Info",
            streamUrl = "https://d1ib1gsg71oarf.cloudfront.net/v1/master/3722c60a815c199d9c0ef36c5b73da68a62b09d1/cc-scp7wda722jph/BFM2_FR.m3u8",
            backupStreamUrl = "https://live-20minutestv.digiteka.com/1961167769/index.m3u8",
            logoUrl = "https://alloforfait.fr/wp-content/uploads/2024/03/logo-bfm2-300x200.jpg",
            country = "FR",
            qualityLabel = "FHD",
            priority = 3
        ),
        LiveChannel(
            key = "fr_france24",
            ordinal = 30,
            name = "France 24 Français",
            group = "📰 Actualités & Info",
            streamUrl = "https://f24hls-i.akamaihd.net/hls/live/221147/F24_FR_HI_HLS/master.m3u8",
            backupStreamUrl = "https://cdn-euronews.akamaized.net/live/eds/africanews-fr/25050/index.m3u8",
            logoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/8/8a/France24.png/512px-France24.png",
            country = "FR",
            qualityLabel = "FHD",
            priority = 3
        ),
        LiveChannel(
            key = "fr_africanews",
            ordinal = 31,
            name = "Euronews / Africanews FR",
            group = "📰 Actualités & Info",
            streamUrl = "https://cdn-euronews.akamaized.net/live/eds/africanews-fr/25050/index.m3u8",
            backupStreamUrl = "https://africa24.vedge.infomaniak.com/livecast/ik:africa24/manifest.m3u8",
            logoUrl = "https://i.imgur.com/5UxU4zc.png",
            country = "FR",
            qualityLabel = "HD",
            priority = 2
        ),
        LiveChannel(
            key = "be_canal_z",
            ordinal = 32,
            name = "Canal Z Économie & Info",
            group = "📰 Actualités & Info",
            streamUrl = "https://d1hz1p2gea2zy4.cloudfront.net/live/rmgtv/RMG-TRENDSZFR-LIVE/index.m3u8",
            backupStreamUrl = "https://d1ib1gsg71oarf.cloudfront.net/v1/master/3722c60a815c199d9c0ef36c5b73da68a62b09d1/cc-scp7wda722jph/BFM2_FR.m3u8",
            logoUrl = "https://i.imgur.com/qtiGLzZ.png",
            country = "BE",
            qualityLabel = "FHD",
            priority = 2
        ),

        // =========================================================================
        // BOUQUET 7 : 🎭 Divertissement, 🎵 Musique & 🌍 Monde (4 chaînes)
        // =========================================================================
        LiveChannel(
            key = "ch_carac_1",
            ordinal = 33,
            name = "Carac 1 Divertissement",
            group = "🎭 Divertissement",
            streamUrl = "https://edge13.vedge.infomaniak.com/livecast/ik:event/manifest.m3u8",
            backupStreamUrl = "https://onefmhd.vedge.infomaniak.com/livecast/ik:onefmhd/manifest.m3u8",
            logoUrl = "https://i.imgur.com/TSc35U6.png",
            country = "CH",
            qualityLabel = "FHD",
            priority = 2
        ),
        LiveChannel(
            key = "fr_cstar_music",
            ordinal = 34,
            name = "CStar & NRJ Hits TV",
            group = "🎵 Musique & Clips",
            streamUrl = "https://raw.githubusercontent.com/Paradise-91/ParaTV/refs/heads/main/streams/canalplus/cstar-dm.m3u8",
            backupStreamUrl = "https://stream.sen-gt.com/A2iMusic/myStream/playlist.m3u8",
            logoUrl = "https://i.imgur.com/w4kAX1V.png",
            country = "FR",
            qualityLabel = "HD",
            priority = 2
        ),
        LiveChannel(
            key = "ma_2m_monde",
            ordinal = 35,
            name = "2M Monde Maroc",
            group = "🌍 Monde & Afrique",
            streamUrl = "https://d2qh3gh0k5vp3v.cloudfront.net/v1/master/3722c60a815c199d9c0ef36c5b73da68a62b09d1/cc-n6pess5lwbghr/2M_ES.m3u8",
            backupStreamUrl = "https://live-20minutestv.digiteka.com/1961167769/index.m3u8",
            logoUrl = "https://i.imgur.com/MvpntzA.png",
            country = "MA",
            qualityLabel = "FHD",
            priority = 2
        ),
        LiveChannel(
            key = "sn_tv5_afrique",
            ordinal = 36,
            name = "TV5Monde Afrique HD",
            group = "🌍 Monde & Afrique",
            streamUrl = "https://ott.tv5monde.com/Content/HLS/Live/channel(afrique)/index.m3u8",
            backupStreamUrl = "https://africa24.vedge.infomaniak.com/livecast/ik:africa24/manifest.m3u8",
            logoUrl = "",
            country = "SN",
            qualityLabel = "FHD",
            priority = 2
        ),

        // =========================================================================
        // 🔞 MODE ADULTE / OLÉ OLÉ (10 Chaînes 18+ strictement isolées !)
        // Ces chaînes n'apparaissent QUE lorsque le Mode Adulte (Noir & Rose) est activé !
        // =========================================================================
        LiveChannel(
            key = "adult_dorcel_tv",
            ordinal = 1,
            name = "Dorcel TV Glamour 18+",
            group = "🔥 Charme & Glamour TV",
            streamUrl = "https://d35j504z0x2vu2.cloudfront.net/v1/master/0bc8e8376bd8417a1b6761138aa41c26c7309312/trace-uk/master.m3u8",
            backupStreamUrl = "https://onefmhd.vedge.infomaniak.com/livecast/ik:onefmhd/manifest.m3u8",
            logoUrl = "",
            country = "FR",
            qualityLabel = "4K",
            isPpv = true,
            priority = 3
        ),
        LiveChannel(
            key = "adult_xxl_nuit",
            ordinal = 2,
            name = "XXL Nuit & Charme FR",
            group = "🔥 Charme & Glamour TV",
            streamUrl = "https://d2qh3gh0k5vp3v.cloudfront.net/v1/master/3722c60a815c199d9c0ef36c5b73da68a62b09d1/cc-44b3622c-2b7a-4c36-9f69-5e99a4d623b2/manifest.m3u8",
            backupStreamUrl = "https://d35j504z0x2vu2.cloudfront.net/v1/master/0bc8e8376bd8417a1b6761138aa41c26c7309312/trace-uk/master.m3u8",
            logoUrl = "",
            country = "FR",
            qualityLabel = "FHD",
            isPpv = true,
            priority = 3
        ),
        LiveChannel(
            key = "adult_ftv_midnight",
            ordinal = 3,
            name = "Midnight Hot Lingerie & Bikinis",
            group = "🔥 Charme & Glamour TV",
            streamUrl = "https://onefmhd.vedge.infomaniak.com/livecast/ik:onefmhd/manifest.m3u8",
            backupStreamUrl = "https://d35j504z0x2vu2.cloudfront.net/v1/master/0bc8e8376bd8417a1b6761138aa41c26c7309312/trace-uk/master.m3u8",
            logoUrl = "",
            country = "FR",
            qualityLabel = "4K",
            isPpv = true,
            priority = 3
        ),
        LiveChannel(
            key = "adult_pink_club",
            ordinal = 4,
            name = "Pink X / Club Rose 24/7",
            group = "💋 Séduction & Nuit 24/7",
            streamUrl = "https://stream.sen-gt.com/A2iMusic/myStream/playlist.m3u8",
            backupStreamUrl = "https://d35j504z0x2vu2.cloudfront.net/v1/master/0bc8e8376bd8417a1b6761138aa41c26c7309312/trace-uk/master.m3u8",
            logoUrl = "",
            country = "FR",
            qualityLabel = "FHD",
            isPpv = true,
            priority = 3
        ),
        LiveChannel(
            key = "adult_venus_hd",
            ordinal = 5,
            name = "Venus Tentation & Sensuel",
            group = "💋 Séduction & Nuit 24/7",
            streamUrl = "https://edge13.vedge.infomaniak.com/livecast/ik:event/manifest.m3u8",
            backupStreamUrl = "https://d35j504z0x2vu2.cloudfront.net/v1/master/0bc8e8376bd8417a1b6761138aa41c26c7309312/trace-uk/master.m3u8",
            logoUrl = "",
            country = "FR",
            qualityLabel = "FHD",
            isPpv = true,
            priority = 2
        ),
        LiveChannel(
            key = "adult_playboy_tv",
            ordinal = 6,
            name = "Playboy TV Midnight Lounge",
            group = "💋 Séduction & Nuit 24/7",
            streamUrl = "https://d35j504z0x2vu2.cloudfront.net/v1/master/0bc8e8376bd8417a1b6761138aa41c26c7309312/trace-uk/master.m3u8",
            backupStreamUrl = "https://onefmhd.vedge.infomaniak.com/livecast/ik:onefmhd/manifest.m3u8",
            logoUrl = "",
            country = "US",
            qualityLabel = "FHD",
            isPpv = true,
            priority = 2
        ),
        LiveChannel(
            key = "adult_penthouse_hd",
            ordinal = 7,
            name = "Penthouse Gold & Passion",
            group = "🌶️ Club Privé & Sensuel",
            streamUrl = "https://d2qh3gh0k5vp3v.cloudfront.net/v1/master/3722c60a815c199d9c0ef36c5b73da68a62b09d1/cc-44b3622c-2b7a-4c36-9f69-5e99a4d623b2/manifest.m3u8",
            backupStreamUrl = "https://d35j504z0x2vu2.cloudfront.net/v1/master/0bc8e8376bd8417a1b6761138aa41c26c7309312/trace-uk/master.m3u8",
            logoUrl = "",
            country = "US",
            qualityLabel = "4K",
            isPpv = true,
            priority = 3
        ),
        LiveChannel(
            key = "adult_hustler_hd",
            ordinal = 8,
            name = "Hustler Blue Velvet VIP",
            group = "🌶️ Club Privé & Sensuel",
            streamUrl = "https://onefmhd.vedge.infomaniak.com/livecast/ik:onefmhd/manifest.m3u8",
            backupStreamUrl = "https://d35j504z0x2vu2.cloudfront.net/v1/master/0bc8e8376bd8417a1b6761138aa41c26c7309312/trace-uk/master.m3u8",
            logoUrl = "",
            country = "US",
            qualityLabel = "FHD",
            isPpv = true,
            priority = 2
        ),
        LiveChannel(
            key = "adult_redlight_cinema",
            ordinal = 9,
            name = "Redlight Cinéma Nocturne 18+",
            group = "🔞 Cinéma Nocturne 18+",
            streamUrl = "https://rakuten-topfree-7-fr.xiaomi.wurl.tv/playlist.m3u8",
            backupStreamUrl = "https://d35j504z0x2vu2.cloudfront.net/v1/master/0bc8e8376bd8417a1b6761138aa41c26c7309312/trace-uk/master.m3u8",
            logoUrl = "",
            country = "IT",
            qualityLabel = "FHD",
            isPpv = true,
            priority = 3
        ),
        LiveChannel(
            key = "adult_frissons_minuit",
            ordinal = 10,
            name = "Frissons Extrêmes Minuit FR",
            group = "🔞 Cinéma Nocturne 18+",
            streamUrl = "https://d35j504z0x2vu2.cloudfront.net/v1/master/0bc8e8376bd8417a1b6761138aa41c26c7309312/trace-uk/master.m3u8",
            backupStreamUrl = "https://rakuten-topfree-7-fr.xiaomi.wurl.tv/playlist.m3u8",
            logoUrl = "",
            country = "FR",
            qualityLabel = "FHD",
            isPpv = true,
            priority = 2
        )
    )

    private fun defaultAdultVodItems(): List<VodItem> = listOf(
        VodItem(
            id = "adult_vod_1",
            title = "Nuits Roses à Paris (18+)",
            type = VodType.MOVIE,
            category = "🔥 Charme & Romance",
            releaseYear = 2026,
            rating = "18+ 💋",
            durationLabel = "1h 38m",
            overview = "Séduction, glamour et passion nocturne au cœur des clubs privés parisiens.",
            streamUrl = "https://d35j504z0x2vu2.cloudfront.net/v1/master/0bc8e8376bd8417a1b6761138aa41c26c7309312/trace-uk/master.m3u8"
        ),
        VodItem(
            id = "adult_vod_2",
            title = "Velvet Tentation VIP (4K)",
            type = VodType.MOVIE,
            category = "🌶️ Club Privé",
            releaseYear = 2026,
            rating = "18+ 🔥",
            durationLabel = "1h 45m",
            overview = "Une soirée exclusive dans une villa sur la Côte d'Azur réservée aux initiés.",
            streamUrl = "https://d2qh3gh0k5vp3v.cloudfront.net/v1/master/3722c60a815c199d9c0ef36c5b73da68a62b09d1/cc-44b3622c-2b7a-4c36-9f69-5e99a4d623b2/manifest.m3u8"
        ),
        VodItem(
            id = "adult_vod_3",
            title = "Secrets d'Alcôve : La Série Olé Olé",
            type = VodType.SERIES,
            category = "💋 Séduction 24/7",
            releaseYear = 2026,
            rating = "18+ 💋",
            durationLabel = "Saison 1 • 3 Épisodes",
            overview = "Chroniques sensuelles et rencontres interdites après minuit.",
            streamUrl = "https://onefmhd.vedge.infomaniak.com/livecast/ik:onefmhd/manifest.m3u8",
            episodes = listOf(
                EpisodeItem("ad_ep1", "adult_vod_3", 1, 1, "Épisode 1 : Minuit Rose", "Première soirée au club.", 42, "https://d35j504z0x2vu2.cloudfront.net/v1/master/0bc8e8376bd8417a1b6761138aa41c26c7309312/trace-uk/master.m3u8"),
                EpisodeItem("ad_ep2", "adult_vod_3", 1, 2, "Épisode 2 : Suite Impériale", "Rencontre secrète au palace.", 46, "https://onefmhd.vedge.infomaniak.com/livecast/ik:onefmhd/manifest.m3u8"),
                EpisodeItem("ad_ep3", "adult_vod_3", 1, 3, "Épisode 3 : Tentation Ultime", "Dernier acte glamour.", 50, "https://d2qh3gh0k5vp3v.cloudfront.net/v1/master/3722c60a815c199d9c0ef36c5b73da68a62b09d1/cc-44b3622c-2b7a-4c36-9f69-5e99a4d623b2/manifest.m3u8")
            )
        ),
        VodItem(
            id = "adult_vod_4",
            title = "Midnight Riviera : Bikini & Glamour",
            type = VodType.MOVIE,
            category = "🔥 Charme & Glamour",
            releaseYear = 2025,
            rating = "18+ 🔥",
            durationLabel = "1h 28m",
            overview = "Défilés nocturnes, charme et séduction sous les néons roses.",
            streamUrl = "https://stream.sen-gt.com/A2iMusic/myStream/playlist.m3u8"
        )
    )

    private fun defaultVodItems(): List<VodItem> = listOf(
        VodItem(
            id = "vod_movie_1",
            title = "Tears of Steel : Sci-Fi 4K",
            type = VodType.MOVIE,
            category = "Science-Fiction",
            releaseYear = 2025,
            rating = "9.5 ★",
            durationLabel = "1h 14m",
            overview = "Film de science-fiction en streaming adaptatif HLS avec bascule automatique Media3 / LibVLC.",
            streamUrl = "https://cph-p2p-msl.akamaized.net/hls/live/2000341/test/master.m3u8",
            availableLanguages = listOf("FR (VFF)", "EN (Original)"),
            subtitles = listOf(
                SubtitleTrackInfo("sub_fr", "fr", "Français (Complet)"),
                SubtitleTrackInfo("sub_en", "en", "English (CC)")
            ),
            audioTracks = listOf(
                AudioTrackInfo("aud_fr", "fr", "Français Dolby 5.1", "EAC3"),
                AudioTrackInfo("aud_en", "en", "English Original Stereo", "AAC")
            )
        ),
        VodItem(
            id = "vod_movie_2",
            title = "Big Buck Bunny : Remastered 4K",
            type = VodType.MOVIE,
            category = "Animation & Aventure",
            releaseYear = 2025,
            rating = "9.4 ★",
            durationLabel = "1h 42m",
            overview = "Édition remasterisée haute définition avec support multi-audio, reprise de lecture instantanée et bascule de transport Media3 / LibVLC.",
            streamUrl = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
            availableLanguages = listOf("FR (VFF)", "EN (Original)", "ES"),
            subtitles = listOf(
                SubtitleTrackInfo("sub_fr", "fr", "Français (Complet)"),
                SubtitleTrackInfo("sub_en", "en", "English (CC)"),
                SubtitleTrackInfo("sub_ar", "ar", "العربية")
            ),
            audioTracks = listOf(
                AudioTrackInfo("aud_fr", "fr", "Français Dolby 5.1", "EAC3"),
                AudioTrackInfo("aud_en", "en", "English Original Stereo", "AAC")
            )
        ),
        VodItem(
            id = "vod_series_1",
            title = "Chroniques Cybernétiques",
            type = VodType.SERIES,
            category = "Science-Fiction",
            releaseYear = 2026,
            rating = "9.7 ★",
            durationLabel = "Saison 1 • 4 Épisodes",
            overview = "Série de science-fiction avec suivi de progression isolé par épisode.",
            streamUrl = "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_fmp4/master.m3u8",
            availableLanguages = listOf("FR", "VOSTFR"),
            subtitles = listOf(
                SubtitleTrackInfo("sub_fr", "fr", "Français (VOSTFR)"),
                SubtitleTrackInfo("sub_en", "en", "English SDH")
            ),
            audioTracks = listOf(
                AudioTrackInfo("aud_fr", "fr", "Français Audio HD", "AAC"),
                AudioTrackInfo("aud_en", "en", "Original Audio", "AAC")
            ),
            episodes = listOf(
                EpisodeItem(
                    id = "ep_s1e1",
                    seriesId = "vod_series_1",
                    seasonNumber = 1,
                    episodeNumber = 1,
                    title = "Épisode 1 : Le Réveil du Réseau",
                    overview = "Premier contact avec l'infrastructure orbitale.",
                    durationMinutes = 48,
                    streamUrl = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
                ),
                EpisodeItem(
                    id = "ep_s1e2",
                    seriesId = "vod_series_1",
                    seasonNumber = 1,
                    episodeNumber = 2,
                    title = "Épisode 2 : Protocole Fantôme",
                    overview = "Traversée des relais chiffrés et récupération du signal.",
                    durationMinutes = 51,
                    streamUrl = "https://cph-p2p-msl.akamaized.net/hls/live/2000341/test/master.m3u8"
                ),
                EpisodeItem(
                    id = "ep_s1e3",
                    seriesId = "vod_series_1",
                    seasonNumber = 1,
                    episodeNumber = 3,
                    title = "Épisode 3 : Horizon Magnétique",
                    overview = "Découverte du cœur quantique de la station.",
                    durationMinutes = 49,
                    streamUrl = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
                ),
                EpisodeItem(
                    id = "ep_s1e4",
                    seriesId = "vod_series_1",
                    seasonNumber = 1,
                    episodeNumber = 4,
                    title = "Épisode 4 : Point de Convergence",
                    overview = "Final de la saison 1 en qualité 4K HDR.",
                    durationMinutes = 56,
                    streamUrl = "https://rbmn-live.akamaized.net/hls/live/590964/BoRB-AT/master.m3u8"
                )
            )
        ),
        VodItem(
            id = "vod_movie_3",
            title = "Red Bull Extreme : L'Odyssée",
            type = VodType.MOVIE,
            category = "Action & Thriller",
            releaseYear = 2026,
            rating = "9.1 ★",
            durationLabel = "1h 55m",
            overview = "Sports extrêmes et expéditions haute montagne filmés en 4K.",
            streamUrl = "https://rbmn-live.akamaized.net/hls/live/590964/BoRB-AT/master.m3u8",
            subtitles = listOf(SubtitleTrackInfo("sub_fr", "fr", "Français")),
            audioTracks = listOf(AudioTrackInfo("aud_fr", "fr", "Français 5.1", "EAC3"))
        )
    )

    private fun defaultSportsEvents(): List<SportEvent> = listOf(
        SportEvent(
            id = "sp_ucl_1",
            sport = "Football",
            competition = "L'Équipe & Africa 24 Sport • Direct",
            homeTeam = "Paris SG",
            awayTeam = "Real Madrid",
            kickoffLabel = "21:00 • EN DIRECT",
            isLive = true,
            isPpv = true,
            scoreOrStatus = "EN DIRECT",
            channelKeys = listOf("fr_lequipe_sport", "fr_africa24_sport")
        ),
        SportEvent(
            id = "sp_ufc_315",
            sport = "MMA / Boxe",
            competition = "Main Event Championship PPV",
            homeTeam = "Makhachev",
            awayTeam = "Oliveira",
            kickoffLabel = "23:30 • PPV LIVE",
            isLive = true,
            isPpv = true,
            scoreOrStatus = "PPV LIVE",
            channelKeys = listOf("int_redbull_sport", "fr_africa24_sport")
        ),
        SportEvent(
            id = "sp_f1_monaco",
            sport = "Formule 1",
            competition = "Red Bull Racing • Spécial Grand Prix",
            homeTeam = "Grille F1",
            awayTeam = "Circuit International",
            kickoffLabel = "EN CONTINU",
            isLive = true,
            isPpv = false,
            scoreOrStatus = "DIRECT HD",
            channelKeys = listOf("int_redbull_sport")
        ),
        SportEvent(
            id = "sp_nba_1",
            sport = "Basketball",
            competition = "Afrique & International Basket",
            homeTeam = "Sélection All-Star",
            awayTeam = "Finales Continentales",
            kickoffLabel = "20:00 • DIRECT",
            isLive = true,
            isPpv = true,
            scoreOrStatus = "LIVE",
            channelKeys = listOf("fr_africa24_sport")
        )
    )
}
