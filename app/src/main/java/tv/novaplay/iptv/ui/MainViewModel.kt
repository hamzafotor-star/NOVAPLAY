package tv.novaplay.iptv.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tv.novaplay.iptv.data.model.ChannelSortMode
import tv.novaplay.iptv.data.model.EpisodeItem
import tv.novaplay.iptv.data.model.LiveChannel
import tv.novaplay.iptv.data.model.PlayerEngineMode
import tv.novaplay.iptv.data.model.PlaylistSource
import tv.novaplay.iptv.data.model.SourceType
import tv.novaplay.iptv.data.model.SportEvent
import tv.novaplay.iptv.data.model.VodItem
import tv.novaplay.iptv.data.model.WatchProgress
import tv.novaplay.iptv.data.repository.IptvRepository
import tv.novaplay.iptv.player.HybridPlayerController

enum class NavTab(val label: String, val shortLabel: String) {
    HOME("Accueil", "Accueil"),
    LIVE_TV("Direct TV", "Direct"),
    VOD("Films & Séries", "VOD"),
    SPORTS("Sports Live", "Sports"),
    SETTINGS("Playlists & IPTV", "IPTV")
}

data class AppUiState(
    val currentTab: NavTab = NavTab.HOME,
    val channels: List<LiveChannel> = emptyList(),
    val availableCountries: List<String> = listOf("TOUS", "FR"),
    val categories: List<String> = listOf("Toutes"),
    val categoryCounts: Map<String, Int> = emptyMap(),
    val hiddenCategories: Set<String> = emptySet(),
    val selectedCountry: String = "TOUS",
    val selectedCategory: String = "Toutes",
    val sortMode: ChannelSortMode = ChannelSortMode.BY_GROUP,
    val isGridView: Boolean = false,
    val liveSearchQuery: String = "",
    val activeChannel: LiveChannel? = null,
    val isPlayerFullscreen: Boolean = false,
    val vodItems: List<VodItem> = emptyList(),
    val selectedVodCategory: String = "Tous",
    val vodSearchQuery: String = "",
    val selectedVodDetail: VodItem? = null,
    val sportsEvents: List<SportEvent> = emptyList(),
    val selectedSportFilter: String = "Tous",
    val continueWatching: List<WatchProgress> = emptyList(),
    val playlistSources: List<PlaylistSource> = emptyList(),
    val isSyncingSource: Boolean = false,
    val syncStatusMessage: String? = null
)

class MainViewModel(
    private val repository: IptvRepository,
    val playerController: HybridPlayerController
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    init {
        refreshAll()
        val firstChannel = _uiState.value.channels.firstOrNull()
        if (firstChannel != null) {
            selectLiveChannel(firstChannel)
        }
    }

    fun refreshAll() {
        val state = _uiState.value
        val channels = repository.getChannels(
            countryFilter = state.selectedCountry,
            categoryFilter = state.selectedCategory,
            searchQuery = state.liveSearchQuery,
            sortMode = state.sortMode
        )
        _uiState.value = state.copy(
            channels = channels,
            availableCountries = repository.getAvailableCountries(),
            categories = repository.getAllCategories(),
            categoryCounts = repository.getCategoryCounts(),
            hiddenCategories = repository.getHiddenCategories(),
            vodItems = repository.getVodCatalog(state.vodSearchQuery, state.selectedVodCategory),
            sportsEvents = repository.getSportsEvents(state.selectedSportFilter),
            continueWatching = repository.getContinueWatching(),
            playlistSources = repository.getPlaylistSources()
        )
    }

    fun selectTab(tab: NavTab) {
        saveActiveVodProgressIfNeeded()
        _uiState.value = _uiState.value.copy(currentTab = tab, isPlayerFullscreen = false)
    }

    fun selectLiveChannel(channel: LiveChannel, openFullscreen: Boolean = false) {
        saveActiveVodProgressIfNeeded()
        _uiState.value = _uiState.value.copy(
            activeChannel = channel,
            isPlayerFullscreen = openFullscreen
        )
        playerController.startStream(
            title = "${channel.countryFlag} ${channel.name} (${channel.qualityLabel})",
            subtitle = "${channel.group} • ${channel.currentProgram?.title ?: "En Direct"}",
            primaryUrl = channel.streamUrl,
            backupUrl = channel.backupStreamUrl,
            isLive = true
        )
    }

    fun zapChannel(delta: Int) {
        val list = _uiState.value.channels
        if (list.isEmpty()) return
        val currentKey = _uiState.value.activeChannel?.key
        val currentIndex = list.indexOfFirst { it.key == currentKey }.coerceAtLeast(0)
        val nextIndex = (currentIndex + delta).mod(list.size)
        selectLiveChannel(list[nextIndex], _uiState.value.isPlayerFullscreen)
    }

    fun setLiveCountryFilter(country: String) {
        _uiState.value = _uiState.value.copy(selectedCountry = country)
        refreshAll()
    }

    fun setLiveCategoryFilter(category: String) {
        _uiState.value = _uiState.value.copy(selectedCategory = category)
        refreshAll()
    }

    fun cycleSortMode() {
        val modes = ChannelSortMode.entries
        val next = modes[(_uiState.value.sortMode.ordinal + 1) % modes.size]
        _uiState.value = _uiState.value.copy(sortMode = next)
        refreshAll()
    }

    fun toggleGridView() {
        _uiState.value = _uiState.value.copy(isGridView = !_uiState.value.isGridView)
    }

    fun setLiveSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(liveSearchQuery = query)
        refreshAll()
    }

    fun toggleChannelFavorite(channelKey: String) {
        repository.toggleChannelFavorite(channelKey)
        refreshAll()
    }

    fun toggleCategoryHidden(category: String) {
        val currentlyHidden = _uiState.value.hiddenCategories.contains(category)
        repository.setCategoryHidden(category, !currentlyHidden)
        refreshAll()
    }

    fun setVodCategory(category: String) {
        _uiState.value = _uiState.value.copy(selectedVodCategory = category)
        refreshAll()
    }

    fun setVodSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(vodSearchQuery = query)
        refreshAll()
    }

    fun openVodDetail(item: VodItem?) {
        _uiState.value = _uiState.value.copy(selectedVodDetail = item)
    }

    fun playVodItem(item: VodItem, episode: EpisodeItem? = null) {
        saveActiveVodProgressIfNeeded()
        val progressKey = if (episode != null) {
            "${item.id}:S${episode.seasonNumber}E${episode.episodeNumber}"
        } else {
            item.id
        }
        val existingProgress = _uiState.value.continueWatching
            .firstOrNull { it.progressKey == progressKey }
            ?.positionMs ?: 0L

        val displayTitle = if (episode != null) {
            "${item.title} • S${episode.seasonNumber}:E${episode.episodeNumber}"
        } else {
            item.title
        }
        val displaySubtitle = episode?.title ?: "${item.category} • ${item.releaseYear}"
        val streamUrl = episode?.streamUrl ?: item.streamUrl

        playerController.startStream(
            title = displayTitle,
            subtitle = displaySubtitle,
            primaryUrl = streamUrl,
            isLive = false,
            resumePositionMs = existingProgress,
            progressKey = progressKey
        )

        repository.saveProgress(
            WatchProgress(
                progressKey = progressKey,
                contentId = item.id,
                episodeId = episode?.id,
                title = displayTitle,
                subtitle = displaySubtitle,
                positionMs = existingProgress.coerceAtLeast(15_000L),
                durationMs = (episode?.durationMinutes ?: 100) * 60_000L
            )
        )
        _uiState.value = _uiState.value.copy(
            selectedVodDetail = null,
            currentTab = NavTab.LIVE_TV,
            isPlayerFullscreen = true
        )
        refreshAll()
    }

    fun resumeFromWatchProgress(progress: WatchProgress) {
        val vod = _uiState.value.vodItems.firstOrNull { it.id == progress.contentId } ?: return
        val ep = vod.episodes.firstOrNull { it.id == progress.episodeId }
        playVodItem(vod, ep)
    }

    fun setSportFilter(sport: String) {
        _uiState.value = _uiState.value.copy(selectedSportFilter = sport)
        refreshAll()
    }

    fun toggleSportFavorite(sportId: String) {
        repository.toggleSportFavorite(sportId)
        refreshAll()
    }

    fun watchSportEvent(event: SportEvent) {
        val targetKey = event.channelKeys.firstOrNull()
        val channel = _uiState.value.channels.firstOrNull { it.key == targetKey }
            ?: _uiState.value.channels.firstOrNull()
        if (channel != null) {
            _uiState.value = _uiState.value.copy(currentTab = NavTab.LIVE_TV)
            selectLiveChannel(channel, openFullscreen = false)
        }
    }

    fun toggleFullscreen() {
        _uiState.value = _uiState.value.copy(
            isPlayerFullscreen = !_uiState.value.isPlayerFullscreen
        )
    }

    fun addPlaylistSource(
        name: String,
        type: SourceType,
        serverUrl: String,
        username: String = "",
        password: String = "",
        navigateToLiveOnSuccess: Boolean = false
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSyncingSource = true,
                syncStatusMessage = "Téléchargement, nettoyage et classement des bouquets en cours..."
            )
            val result = repository.addAndSyncSource(
                name = name,
                type = type,
                serverUrl = serverUrl,
                username = username,
                password = password
            )
            result.fold(
                onSuccess = { src ->
                    _uiState.value = _uiState.value.copy(
                        selectedCountry = "TOUS",
                        selectedCategory = "Toutes",
                        liveSearchQuery = ""
                    )
                    refreshAll()
                    val first = _uiState.value.channels.firstOrNull()
                    if (first != null) {
                        selectLiveChannel(first, openFullscreen = false)
                    }
                    _uiState.value = _uiState.value.copy(
                        isSyncingSource = false,
                        currentTab = if (navigateToLiveOnSuccess) NavTab.LIVE_TV else _uiState.value.currentTab,
                        syncStatusMessage = "✓ '${src.name}' actif (${src.channelCount} chaînes organisées par bouquet)"
                    )
                },
                onFailure = { err ->
                    _uiState.value = _uiState.value.copy(
                        isSyncingSource = false,
                        syncStatusMessage = "Erreur : ${err.message}"
                    )
                }
            )
        }
    }

    fun restoreBuiltinCatalog() {
        repository.ensureSeedCatalogLoaded(forceReset = true)
        _uiState.value = _uiState.value.copy(
            selectedCountry = "TOUS",
            selectedCategory = "Toutes",
            liveSearchQuery = "",
            syncStatusMessage = "✓ Bouquet officiel NovaPlay restauré (22 chaînes HD classées)"
        )
        refreshAll()
        _uiState.value.channels.firstOrNull()?.let { selectLiveChannel(it) }
    }

    fun deletePlaylistSource(sourceId: String) {
        repository.deletePlaylistSource(sourceId)
        refreshAll()
    }

    fun setEnginePreference(mode: PlayerEngineMode) {
        playerController.setEngineMode(mode)
    }

    fun saveActiveVodProgressIfNeeded() {
        val pState = playerController.uiState.value
        val key = pState.progressKey ?: return
        if (!pState.isLive) {
            val pos = playerController.currentPositionMs()
            val dur = playerController.durationMs().coerceAtLeast(3_600_000L)
            if (pos > 5_000L) {
                repository.saveProgress(
                    WatchProgress(
                        progressKey = key,
                        contentId = key.substringBefore(":"),
                        episodeId = key.substringAfter(":", "").ifBlank { null },
                        title = pState.title,
                        subtitle = pState.subtitle,
                        positionMs = pos,
                        durationMs = dur
                    )
                )
            }
        }
    }

    override fun onCleared() {
        saveActiveVodProgressIfNeeded()
        super.onCleared()
    }
}
