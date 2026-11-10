/**
 * NovaPlay IPTV - PC Desktop Application Controller
 * Combines HLS.js + MPEGTS.js + Local IPTV VLC Proxy with automatic multi-stage recovery.
 */

const state = {
    channels: [],
    categories: ['Toutes'],
    categoryCounts: {},
    countries: ['TOUS'],
    vodItems: [],
    sportsEvents: [],
    sources: [],
    selectedCategory: 'Toutes',
    selectedCountry: 'TOUS',
    searchQuery: '',
    sortModeIndex: 0, // 0: Bouquet, 1: Numéro, 2: A-Z, 3: Qualité HD
    isGridView: false,
    activeChannel: null,
    scaleModeIndex: 0, // 0: 16:9, 1: ZOOM, 2: 100%, 3: 4:3
    forceProxy: false,
    fallbackStage: 0 // 0: Direct Primary, 1: Proxy Primary, 2: Proxy Backup
};

const SORT_MODES = [
    { id: 'BY_GROUP', label: 'Bouquet' },
    { id: 'BY_NUMBER', label: 'N° Chaîne' },
    { id: 'BY_NAME', label: 'A–Z' },
    { id: 'BY_QUALITY', label: 'Qualité HD' }
];

const SCALE_MODES = [
    { css: 'scale-fit', badge: '📐 16:9', name: 'Ajusté (16:9)' },
    { css: 'scale-zoom', badge: '📐 ZOOM', name: 'Zoom Plein Écran' },
    { css: 'scale-fill', badge: '📐 100%', name: 'Étirer (100%)' },
    { css: 'scale-4-3', badge: '📐 4:3', name: 'Format 4:3' }
];

let hlsInstance = null;
let mpegtsInstance = null;
let watchdogTimer = null;

document.addEventListener('DOMContentLoaded', async () => {
    setupKeyboardShortcuts();
    await loadStateFromServer(true);
});

async function loadStateFromServer(playFirst = false) {
    try {
        const res = await fetch('/api/state');
        const data = await res.json();
        state.channels = data.channels || [];
        state.categories = data.categories || ['Toutes'];
        state.categoryCounts = data.categoryCounts || {};
        state.countries = data.countries || ['TOUS'];
        state.vodItems = data.vodItems || [];
        state.sportsEvents = data.sportsEvents || [];
        state.sources = data.sources || [];

        updateTopStats();
        renderBouquetTabs();
        renderCountryTabs();
        renderChannelCollection();
        renderHomeDashboard();
        renderVodCatalog();
        renderSportsCatalog();
        renderSourcesList();

        if (playFirst && state.channels.length > 0 && !state.activeChannel) {
            selectChannel(state.channels[0]);
        }
    } catch (err) {
        console.error('Failed to load state:', err);
    }
}

function updateTopStats() {
    const bouquetCount = Math.max(1, state.categories.filter(c => c !== 'Toutes' && c !== '⭐ Favoris').length);
    document.getElementById('topStatsText').textContent =
        `${state.channels.length} chaînes HD • ${bouquetCount} bouquets organisés • Moteur HLS.js + Proxy VLC`;
    const heroBadge = document.getElementById('heroChannelCount');
    if (heroBadge) heroBadge.textContent = `${state.channels.length} CHAÎNES PRÊTES`;
}

function switchTab(tabId) {
    document.querySelectorAll('.nav-item').forEach(el => {
        el.classList.toggle('active', el.dataset.tab === tabId);
    });
    document.querySelectorAll('.view-section').forEach(sec => {
        sec.classList.toggle('active', sec.id === `view-${tabId}`);
    });
}

function getFilteredAndSortedChannels() {
    const q = state.searchQuery.trim().toLowerCase();
    let list = state.channels.filter(ch => {
        if (state.selectedCountry !== 'TOUS' && ch.country !== state.selectedCountry) return false;
        if (state.selectedCategory === '⭐ Favoris' && !ch.isFavorite) return false;
        if (state.selectedCategory !== 'Toutes' && state.selectedCategory !== '⭐ Favoris' && ch.group !== state.selectedCategory) {
            return false;
        }
        if (q) {
            const hay = `${ch.name} ${ch.group} ${ch.country} ${ch.currentTitle || ''}`.toLowerCase();
            if (!hay.includes(q)) return false;
        }
        return true;
    });

    const sortId = SORT_MODES[state.sortModeIndex].id;
    const qRank = { '4K': 0, 'FHD': 1, 'HD': 2, 'SD': 3 };

    list = [...list].sort((a, b) => {
        if (a.isFavorite !== b.isFavorite) return a.isFavorite ? -1 : 1;
        if (sortId === 'BY_NUMBER') return a.ordinal - b.ordinal;
        if (sortId === 'BY_NAME') return a.name.localeCompare(b.name, 'fr');
        if (sortId === 'BY_QUALITY') {
            const d = (qRank[a.quality] ?? 2) - (qRank[b.quality] ?? 2);
            return d !== 0 ? d : a.ordinal - b.ordinal;
        }
        return a.ordinal - b.ordinal;
    });

    return list;
}

function renderBouquetTabs() {
    const container = document.getElementById('bouquetTabs');
    container.innerHTML = '';
    for (const cat of state.categories) {
        const count = state.categoryCounts[cat];
        const btn = document.createElement('button');
        btn.className = `bouquet-tab ${state.selectedCategory === cat ? 'active' : ''}`;
        btn.textContent = count !== undefined && count > 0 ? `${cat} (${count})` : cat;
        btn.onclick = () => {
            state.selectedCategory = cat;
            renderBouquetTabs();
            renderChannelCollection();
        };
        container.appendChild(btn);
    }
}

function renderCountryTabs() {
    const container = document.getElementById('countryTabs');
    if (state.countries.length <= 2) {
        container.style.display = 'none';
        return;
    }
    container.style.display = 'flex';
    container.innerHTML = '';
    for (const country of state.countries) {
        const btn = document.createElement('button');
        btn.className = `bouquet-tab ${state.selectedCountry === country ? 'active' : ''}`;
        btn.textContent = country === 'TOUS' ? '🌍 Tous Pays' : country;
        btn.onclick = () => {
            state.selectedCountry = country;
            renderCountryTabs();
            renderChannelCollection();
        };
        container.appendChild(btn);
    }
}

function renderChannelCollection() {
    const container = document.getElementById('channelListContainer');
    const drawerList = document.getElementById('fsDrawerList');
    container.classList.toggle('grid-mode', state.isGridView);
    container.innerHTML = '';
    if (drawerList) drawerList.innerHTML = '';

    const filtered = getFilteredAndSortedChannels();
    if (filtered.length === 0) {
        container.innerHTML = `
            <div class="panel-card" style="grid-column: 1 / -1; text-align:center; padding:24px;">
                <h4>Aucune chaîne ne correspond à ce filtre</h4>
                <div style="display:flex; justify-content:center; gap:10px; margin-top:10px;">
                    <button class="btn-secondary" onclick="resetFilters()">Tout afficher</button>
                    <button class="btn-primary" onclick="switchTab('settings')">➕ Charger un Bouquet IPTV</button>
                </div>
            </div>
        `;
        return;
    }

    const showSectionHeaders = !state.isGridView &&
        state.selectedCategory === 'Toutes' &&
        SORT_MODES[state.sortModeIndex].id === 'BY_GROUP' &&
        !state.searchQuery.trim();

    let lastGroup = null;

    for (const ch of filtered) {
        if (showSectionHeaders && ch.group !== lastGroup) {
            lastGroup = ch.group;
            const groupCount = filtered.filter(x => x.group === ch.group).length;
            const header = document.createElement('div');
            header.className = 'bouquet-section-header';
            header.innerHTML = `<span>${escapeHtml(ch.group)}</span><span style="font-size:11px; opacity:0.85;">${groupCount} chaînes</span>`;
            container.appendChild(header);
        }

        const card = buildChannelCardElement(ch);
        container.appendChild(card);

        if (drawerList) {
            drawerList.appendChild(buildChannelCardElement(ch));
        }
    }
}

function buildChannelCardElement(ch) {
    const isSelected = state.activeChannel && state.activeChannel.key === ch.key;
    const card = document.createElement('div');
    card.className = `channel-card ${isSelected ? 'active' : ''}`;
    card.onclick = () => selectChannel(ch);

    const logoHtml = ch.logoUrl
        ? `<img src="${escapeHtml(ch.logoUrl)}" alt="" onerror="this.style.display='none'; this.parentElement.textContent='#${ch.ordinal}';" />`
        : `#${ch.ordinal}`;

    const qClass = ch.quality === '4K' ? 'q-badge q-4k' : 'q-badge';

    card.innerHTML = `
        <div class="ch-left">
            <div class="ch-num-box">${logoHtml}</div>
            <div class="ch-meta">
                <div class="ch-title-row">
                    <span class="ch-name">${escapeHtml(ch.countryFlag || '🇫🇷')} ${escapeHtml(ch.name)}</span>
                    <span class="${qClass}">${escapeHtml(ch.quality || 'HD')}</span>
                </div>
                <div class="ch-sub">${escapeHtml(ch.group)} • ${escapeHtml(ch.currentTitle || 'En Direct')}</div>
            </div>
        </div>
        <button class="fav-btn ${ch.isFavorite ? 'active' : ''}" title="Favori">★</button>
    `;

    const favBtn = card.querySelector('.fav-btn');
    favBtn.onclick = async (e) => {
        e.stopPropagation();
        await toggleFavorite(ch.key);
    };

    return card;
}

function selectChannel(ch) {
    state.activeChannel = ch;
    state.fallbackStage = state.forceProxy ? 1 : 0;

    document.getElementById('playerTitle').textContent = `${ch.countryFlag || '🇫🇷'} ${ch.name} (${ch.quality || 'HD'})`;
    document.getElementById('playerSubtitle').textContent = `${ch.group} • ${ch.currentTitle || 'Direct Live'}`;

    document.getElementById('epgChannelName').textContent = `#${ch.ordinal} ${ch.countryFlag || '🇫🇷'} ${ch.name} (${ch.quality || 'HD'})`;
    document.getElementById('epgProgramTitle').textContent = `${ch.group} • ${ch.currentTitle || 'Programme en direct'}`;
    document.getElementById('epgProgramDesc').textContent = ch.currentDesc || 'Diffusion HD en direct.';

    renderChannelCollection();
    startStreamPlayback(ch.streamUrl, ch.backupUrl);
}

function startStreamPlayback(primaryUrl, backupUrl) {
    destroyActivePlayers();
    showBuffering(true, 'Connexion au flux HD...');
    setStatusBanner('');

    let targetUrl = primaryUrl;
    if (state.fallbackStage === 1) {
        targetUrl = `/api/proxy?url=${encodeURIComponent(primaryUrl)}`;
        setStatusBanner('Relais Proxy IPTV VLC actif');
    } else if (state.fallbackStage >= 2) {
        const alt = backupUrl || primaryUrl;
        targetUrl = `/api/proxy?url=${encodeURIComponent(alt)}`;
        setStatusBanner('Flux de secours via Proxy IPTV VLC');
    }

    const video = document.getElementById('videoPlayer');
    const lower = primaryUrl.split('?')[0].toLowerCase();

    // Start 8s Buffering Watchdog
    clearTimeout(watchdogTimer);
    watchdogTimer = setTimeout(() => {
        if (video.paused || video.readyState < 2) {
            triggerFallback('Délai réseau dépassé');
        }
    }, 8000);

    video.onplaying = () => {
        clearTimeout(watchdogTimer);
        showBuffering(false);
        updatePlayPauseButton();
    };
    video.onwaiting = () => showBuffering(true, 'Mise en mémoire tampon...');
    video.onpause = () => updatePlayPauseButton();

    if (lower.endsWith('.ts') && window.mpegts && mpegts.getFeatureList().mseLivePlayback) {
        mpegtsInstance = mpegts.createPlayer({
            type: 'mpegts',
            isLive: true,
            url: `/api/proxy?url=${encodeURIComponent(primaryUrl)}`
        });
        mpegtsInstance.attachMediaElement(video);
        mpegtsInstance.load();
        mpegtsInstance.play().catch(() => {});
        mpegtsInstance.on(mpegts.Events.ERROR, () => triggerFallback('Erreur flux MPEG-TS'));
        return;
    }

    if (window.Hls && Hls.isSupported()) {
        hlsInstance = new Hls({
            enableWorker: true,
            lowLatencyMode: true,
            backBufferLength: 30,
            maxBufferLength: 25
        });
        hlsInstance.loadSource(targetUrl);
        hlsInstance.attachMedia(video);
        hlsInstance.on(Hls.Events.MANIFEST_PARSED, () => {
            showBuffering(false);
            video.play().catch(() => {});
        });
        hlsInstance.on(Hls.Events.ERROR, (_, data) => {
            if (data.fatal) {
                triggerFallback(`HLS (${data.details || 'Erreur réseau'})`);
            }
        });
    } else {
        video.src = targetUrl;
        video.play().catch(() => triggerFallback('Format non supporté en direct'));
    }
}

function triggerFallback(reason) {
    clearTimeout(watchdogTimer);
    const ch = state.activeChannel;
    if (!ch) return;

    if (state.fallbackStage === 0) {
        state.fallbackStage = 1;
        setStatusBanner(`Bascule automatique vers le Proxy IPTV VLC (${reason})...`);
        startStreamPlayback(ch.streamUrl, ch.backupUrl);
    } else if (state.fallbackStage === 1 && ch.backupUrl && ch.backupUrl !== ch.streamUrl) {
        state.fallbackStage = 2;
        setStatusBanner('Bascule automatique vers le flux de secours...');
        startStreamPlayback(ch.streamUrl, ch.backupUrl);
    } else {
        showBuffering(false);
        setStatusBanner('Flux momentanément indisponible — Cliquez sur Relancer ou choisissez une autre chaîne.');
    }
}

function destroyActivePlayers() {
    clearTimeout(watchdogTimer);
    if (hlsInstance) {
        try { hlsInstance.destroy(); } catch {}
        hlsInstance = null;
    }
    if (mpegtsInstance) {
        try { mpegtsInstance.destroy(); } catch {}
        mpegtsInstance = null;
    }
}

function retryCurrentStream() {
    if (!state.activeChannel) return;
    state.fallbackStage = state.forceProxy ? 1 : 0;
    startStreamPlayback(state.activeChannel.streamUrl, state.activeChannel.backupUrl);
}

function zapChannel(delta) {
    const list = getFilteredAndSortedChannels();
    if (list.length === 0) return;
    const currentKey = state.activeChannel ? state.activeChannel.key : null;
    const idx = Math.max(0, list.findIndex(c => c.key === currentKey));
    const nextIdx = (idx + delta + list.length) % list.length;
    selectChannel(list[nextIdx]);
}

function cycleScaleMode() {
    state.scaleModeIndex = (state.scaleModeIndex + 1) % SCALE_MODES.length;
    const mode = SCALE_MODES[state.scaleModeIndex];
    const video = document.getElementById('videoPlayer');
    video.className = mode.css;
    document.getElementById('scaleModeBtn').textContent = mode.badge;
    setStatusBanner(`Format d'écran : ${mode.name}`);
}

function toggleProxyMode() {
    state.forceProxy = !state.forceProxy;
    const btn = document.getElementById('proxyModeBtn');
    btn.textContent = state.forceProxy ? '🛡️ PROXY VLC FORCÉ' : '⚡ DIRECT + PROXY AUTO';
    if (state.activeChannel) {
        retryCurrentStream();
    }
}

function togglePlayPause() {
    const video = document.getElementById('videoPlayer');
    if (video.paused) video.play().catch(() => {});
    else video.pause();
    updatePlayPauseButton();
}

function updatePlayPauseButton() {
    const video = document.getElementById('videoPlayer');
    const btn = document.getElementById('playPauseBtn');
    if (btn) btn.textContent = video.paused ? '▶ Lecture' : '⏸ Pause';
}

function toggleMute() {
    const video = document.getElementById('videoPlayer');
    video.muted = !video.muted;
    document.getElementById('muteBtn').textContent = video.muted ? '🔇' : '🔊';
}

function changeVolume(val) {
    const video = document.getElementById('videoPlayer');
    video.volume = parseFloat(val);
    video.muted = video.volume === 0;
    document.getElementById('muteBtn').textContent = video.muted ? '🔇' : '🔊';
}

function toggleFullscreen() {
    const container = document.getElementById('videoContainer');
    if (!document.fullscreenElement) {
        container.requestFullscreen().catch(() => {});
    } else {
        document.exitFullscreen().catch(() => {});
    }
}

function toggleFullscreenDrawer() {
    document.getElementById('fsDrawer').classList.toggle('open');
}

function requestPip() {
    const video = document.getElementById('videoPlayer');
    if (document.pictureInPictureElement) {
        document.exitPictureInPicture().catch(() => {});
    } else if (video.requestPictureInPicture) {
        video.requestPictureInPicture().catch(() => {});
    }
}

function showBuffering(visible, text) {
    const box = document.getElementById('bufferingBox');
    if (!box) return;
    if (text) document.getElementById('bufferingText').textContent = text;
    box.classList.toggle('show', visible);
}

function setStatusBanner(msg) {
    const el = document.getElementById('playerStatusBanner');
    if (el) el.textContent = msg || '';
}

function onSearchInput(val) {
    state.searchQuery = val;
    renderChannelCollection();
}

function resetFilters() {
    state.selectedCategory = 'Toutes';
    state.selectedCountry = 'TOUS';
    state.searchQuery = '';
    document.getElementById('searchInput').value = '';
    renderBouquetTabs();
    renderCountryTabs();
    renderChannelCollection();
}

function cycleSortMode() {
    state.sortModeIndex = (state.sortModeIndex + 1) % SORT_MODES.length;
    document.getElementById('sortModeBtn').textContent = `Tri: ${SORT_MODES[state.sortModeIndex].label}`;
    renderChannelCollection();
}

function toggleGridView() {
    state.isGridView = !state.isGridView;
    document.getElementById('gridModeBtn').textContent = state.isGridView ? '☰ Liste' : '⊞ Grille';
    renderChannelCollection();
}

async function toggleFavorite(channelKey) {
    await fetch('/api/favorite', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ key: channelKey })
    });
    await loadStateFromServer(false);
}

// Home, VOD, Sports & Settings Rendering
function renderHomeDashboard() {
    const chGrid = document.getElementById('homeChannelsGrid');
    chGrid.innerHTML = '';
    for (const ch of state.channels.slice(0, 12)) {
        const div = document.createElement('div');
        div.className = 'media-card';
        div.onclick = () => {
            switchTab('live');
            selectChannel(ch);
        };
        div.innerHTML = `
            <div style="display:flex; justify-content:space-between; align-items:center;">
                <span class="q-badge">${escapeHtml(ch.countryFlag || '🇫🇷')} ${escapeHtml(ch.quality || 'HD')}</span>
                <span style="font-size:11px; color:var(--text-secondary);">Ch. ${ch.ordinal}</span>
            </div>
            <h4 style="font-size:15px;">${escapeHtml(ch.name)}</h4>
            <p style="font-size:12px; color:var(--text-secondary);">${escapeHtml(ch.group)}</p>
        `;
        chGrid.appendChild(div);
    }

    const vodGrid = document.getElementById('homeVodGrid');
    vodGrid.innerHTML = '';
    for (const vod of state.vodItems) {
        vodGrid.appendChild(createVodCard(vod));
    }
}

function renderVodCatalog() {
    const grid = document.getElementById('vodCatalogGrid');
    grid.innerHTML = '';
    for (const vod of state.vodItems) {
        grid.appendChild(createVodCard(vod));
    }
}

function createVodCard(vod) {
    const div = document.createElement('div');
    div.className = 'media-card';
    div.onclick = () => {
        switchTab('live');
        state.activeChannel = {
            key: vod.id,
            ordinal: 0,
            name: vod.title,
            group: `VOD • ${vod.category}`,
            countryFlag: '🎬',
            quality: '4K',
            streamUrl: vod.streamUrl,
            backupUrl: null,
            currentTitle: `${vod.releaseYear} • ${vod.durationLabel}`,
            currentDesc: vod.synopsis
        };
        selectChannel(state.activeChannel);
    };
    div.innerHTML = `
        <div style="display:flex; justify-content:space-between;">
            <span class="q-badge">${escapeHtml(vod.type)}</span>
            <span style="color:var(--gold); font-size:12px; font-weight:700;">${escapeHtml(vod.rating)}</span>
        </div>
        <h4 style="font-size:15px; margin-top:4px;">${escapeHtml(vod.title)}</h4>
        <p style="font-size:12px; color:var(--cyan);">${escapeHtml(vod.category)} • ${escapeHtml(vod.durationLabel)}</p>
        <p style="font-size:11.5px; color:var(--text-secondary); line-height:1.4;">${escapeHtml(vod.synopsis)}</p>
    `;
    return div;
}

function renderSportsCatalog() {
    const grid = document.getElementById('sportsCatalogGrid');
    grid.innerHTML = '';
    for (const ev of state.sportsEvents) {
        const div = document.createElement('div');
        div.className = 'media-card';
        div.onclick = () => {
            const target = state.channels.find(c => c.key === ev.channelKey) || state.channels[0];
            if (target) {
                switchTab('live');
                selectChannel(target);
            }
        };
        div.innerHTML = `
            <div style="display:flex; justify-content:space-between; align-items:center;">
                <span style="color:var(--cyan); font-size:12px; font-weight:700;">⚽ ${escapeHtml(ev.sport)}</span>
                <span class="pill-live" style="padding:2px 8px;">${escapeHtml(ev.scoreOrStatus)}</span>
            </div>
            <h4 style="font-size:15px; margin-top:4px;">${escapeHtml(ev.homeTeam)} vs ${escapeHtml(ev.awayTeam)}</h4>
            <p style="font-size:12px; color:var(--text-secondary);">${escapeHtml(ev.competition)} • ${escapeHtml(ev.kickoffLabel)}</p>
        `;
        grid.appendChild(div);
    }
}

function renderSourcesList() {
    const container = document.getElementById('sourcesList');
    if (!container) return;
    container.innerHTML = '';
    for (const src of state.sources) {
        const row = document.createElement('div');
        row.style.cssText = 'background:var(--bg-surface-dark); padding:10px 12px; border-radius:10px; display:flex; justify-content:space-between; align-items:center;';
        row.innerHTML = `
            <div>
                <div style="font-weight:700; font-size:13px;">${escapeHtml(src.name)}</div>
                <div style="font-size:11.5px; color:var(--cyan);">${src.channelCount} chaînes • ${escapeHtml(src.type)}</div>
            </div>
            <span class="q-badge">ACTIF</span>
        `;
        container.appendChild(row);
    }
}

// Playlist Import Actions
async function quickLoadBouquet(name, url) {
    setSyncBanner(`⏳ Téléchargement et classement de "${name}" en cours...`);
    try {
        const res = await fetch('/api/playlist/add', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ name, type: 'M3U_URL', serverUrl: url })
        });
        const data = await res.json();
        if (!data.ok) throw new Error(data.error || 'Erreur chargement');
        await loadStateFromServer(false);
        switchTab('live');
        if (state.channels.length > 0) selectChannel(state.channels[0]);
        setSyncBanner(`✓ "${name}" chargé (${data.addedCount} chaînes classées par bouquet)`);
    } catch (err) {
        setSyncBanner(`❌ Erreur : ${err.message}`);
    }
}

async function submitM3uUrl() {
    const name = document.getElementById('m3uNameInput').value.trim() || 'Bouquet M3U';
    const url = document.getElementById('m3uUrlInput').value.trim();
    if (!url) return alert('Veuillez saisir une URL M3U / M3U8');
    await quickLoadBouquet(name, url);
}

function handleLocalM3uFile(input) {
    const file = input.files && input.files[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = async () => {
        setSyncBanner(`⏳ Analyse du fichier local "${file.name}"...`);
        try {
            const res = await fetch('/api/playlist/add', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    name: file.name.replace(/\.[^.]+$/, ''),
                    type: 'M3U_CONTENT',
                    content: reader.result
                })
            });
            const data = await res.json();
            if (!data.ok) throw new Error(data.error || 'Fichier M3U invalide');
            await loadStateFromServer(false);
            switchTab('live');
            if (state.channels.length > 0) selectChannel(state.channels[0]);
            setSyncBanner(`✓ Fichier "${file.name}" importé (${data.addedCount} chaînes classées)`);
        } catch (err) {
            setSyncBanner(`❌ Erreur : ${err.message}`);
        }
    };
    reader.readAsText(file);
}

async function submitXtreamLogin() {
    const name = document.getElementById('xtreamNameInput').value.trim() || 'Serveur Xtream';
    const serverUrl = document.getElementById('xtreamServerInput').value.trim();
    const username = document.getElementById('xtreamUserInput').value.trim();
    const password = document.getElementById('xtreamPassInput').value.trim();
    if (!serverUrl || !username || !password) {
        return alert('Veuillez renseigner l\'URL du serveur, l\'utilisateur et le mot de passe.');
    }
    setSyncBanner(`⏳ Connexion au serveur Xtream "${name}"...`);
    try {
        const res = await fetch('/api/playlist/add', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ name, type: 'XTREAM', serverUrl, username, password })
        });
        const data = await res.json();
        if (!data.ok) throw new Error(data.error || 'Échec connexion Xtream');
        await loadStateFromServer(false);
        switchTab('live');
        if (state.channels.length > 0) selectChannel(state.channels[0]);
        setSyncBanner(`✓ Xtream "${name}" connecté (${data.addedCount} chaînes chargées)`);
    } catch (err) {
        setSyncBanner(`❌ Erreur Xtream : ${err.message}`);
    }
}

async function restoreBuiltinCatalog() {
    await fetch('/api/playlist/restore', { method: 'POST' });
    await loadStateFromServer(false);
    if (state.channels.length > 0) selectChannel(state.channels[0]);
    setSyncBanner('✓ Bouquet officiel NovaPlay restauré (22 chaînes HD)');
}

function setSyncBanner(msg) {
    const el = document.getElementById('syncStatusBanner');
    if (el) el.textContent = msg;
    setStatusBanner(msg);
}

function setupKeyboardShortcuts() {
    document.addEventListener('keydown', (e) => {
        if (['INPUT', 'TEXTAREA'].includes(document.activeElement.tagName)) return;
        if (e.key === 'ArrowUp') {
            e.preventDefault();
            zapChannel(-1);
        } else if (e.key === 'ArrowDown') {
            e.preventDefault();
            zapChannel(1);
        } else if (e.key === 'f' || e.key === 'F') {
            e.preventDefault();
            toggleFullscreen();
        } else if (e.key === 'r' || e.key === 'R') {
            e.preventDefault();
            cycleScaleMode();
        } else if (e.key === 'm' || e.key === 'M') {
            e.preventDefault();
            toggleMute();
        } else if (e.key === ' ') {
            e.preventDefault();
            togglePlayPause();
        }
    });
}

function escapeHtml(str) {
    return String(str || '')
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;');
}
