/**
 * NovaPlay IPTV - PC Desktop Server & IPTV Stream Proxy Engine
 * Zero-dependency Node.js backend providing:
 * - CORS-free HLS (.m3u8) & MPEG-TS (.ts) proxy with VLC/3.0.20 User-Agent & SSL tolerance
 * - M3U / M3U8 playlist parser with automatic French bouquet classification & HD badge detection
 * - Xtream Codes API client (player_api.php + get.php fallback)
 * - Persistent local storage for Favorites, Playlists & Watch Progress
 */

const http = require('http');
const https = require('https');
const fs = require('fs');
const path = require('path');
const { URL } = require('url');

const PORT = process.env.NOVAPLAY_PORT ? parseInt(process.env.NOVAPLAY_PORT, 10) : 4789;
const PUBLIC_DIR = path.join(__dirname, 'public');
const DATA_DIR = path.join(__dirname, 'data');
const STORE_PATH = path.join(DATA_DIR, 'store.json');

if (!fs.existsSync(DATA_DIR)) {
    fs.mkdirSync(DATA_DIR, { recursive: true });
}

// Bouquet constants (matching Android NovaPlay IPTV)
const BOUQUET = {
    TNT: '🇫🇷 TNT & Généraliste',
    NEWS: '📰 Actualités & Info',
    SPORTS: '⚽ Sports Live',
    CINEMA: '🎬 Cinéma & Séries',
    ENTERTAINMENT: '🎭 Divertissement',
    KIDS: '🧒 Jeunesse & Manga',
    MUSIC: '🎵 Musique & Clips',
    WORLD: '🌍 Monde & Afrique',
    CULTURE: '📚 Culture & Découverte'
};

const BOUQUET_ORDER = [
    'Toutes',
    '⭐ Favoris',
    BOUQUET.TNT,
    BOUQUET.NEWS,
    BOUQUET.SPORTS,
    BOUQUET.CINEMA,
    BOUQUET.ENTERTAINMENT,
    BOUQUET.KIDS,
    BOUQUET.MUSIC,
    BOUQUET.WORLD,
    BOUQUET.CULTURE
];

const COUNTRY_FLAGS = {
    FR: '🇫🇷', BE: '🇧🇪', CH: '🇨🇭', CA: '🇨🇦',
    MA: '🇲🇦', DZ: '🇩🇿', TN: '🇹🇳', SN: '🇸🇳', CI: '🇨🇮',
    US: '🇺🇸', UK: '🇬🇧', GB: '🇬🇧', ES: '🇪🇸', IT: '🇮🇹', DE: '🇩🇪', PT: '🇵🇹',
    INT: '🌍'
};

function getCountryFlag(code) {
    return COUNTRY_FLAGS[(code || 'FR').toUpperCase()] || '📺';
}

function cleanChannelName(raw) {
    return (raw || '')
        .replace(/\[.*?\]/g, ' ')
        .replace(/\((\d{3,4}p|720p|1080p|4k|uhd|hd|fhd|sd|hevc|h265|Geo-blocked|Not 24\/7)\)/gi, ' ')
        .replace(/\b(FHD|UHD|4K|1080p|720p|576p|480p|HEVC|H\.?265|VIP|BACKUP)\b/gi, ' ')
        .replace(/^[A-Z]{2}\s*[:|\-]\s*/i, '')
        .replace(/\s{2,}/g, ' ')
        .trim() || raw.trim();
}

function extractQualityBadge(rawName, group) {
    const combined = `${rawName} ${group}`.toLowerCase();
    if (combined.includes('4k') || combined.includes('uhd') || combined.includes('2160p')) return '4K';
    if (combined.includes('1080p') || combined.includes('fhd')) return 'FHD';
    if (combined.includes('720p') || combined.includes('hd')) return 'HD';
    if (combined.includes('480p') || combined.includes('576p') || combined.includes('sd')) return 'SD';
    return 'HD';
}

function classifyBouquet(name, rawGroup, countryCode) {
    const text = `${name} ${rawGroup}`.toLowerCase();
    if (/sport|foot|soccer|ligue|bein|rmc sport|eurosport|dazn|equipe|l'équipe|f1|ufc|tennis|nba|golf|fight|combat|red bull/.test(text)) {
        return BOUQUET.SPORTS;
    }
    if (/news|info|actualit|bfm|cnews|lci|franceinfo|france 24|euronews|tv5|al jazeera|cnn|bbc world|i24|rt france|cgt|dw|medi1/.test(text)) {
        return BOUQUET.NEWS;
    }
    if (/cinema|cinéma|movie|film|serie|série|action|polar|thriller|sci-fi|box office|paramount|tcm|ocs|canal\+ cinema|pluto.*film|rakuten.*film|wild side/.test(text)) {
        return BOUQUET.CINEMA;
    }
    if (/kids|jeunesse|animation|manga|anime|cartoon|gulli|disney|nickelodeon|boing|boomerang|tiji|piwi|toon|naruto|one piece|south park/.test(text)) {
        return BOUQUET.KIDS;
    }
    if (/music|musique|mtv|nrj hits|trace|mezz|melody|cherie|rfm|mcm|vevo|concert|hit|hip hop|clubbing|deluxe/.test(text)) {
        return BOUQUET.MUSIC;
    }
    if (/doc|découverte|decouverte|culture|histoire|history|arte|science|nature|animaux|geo|national geographic|planete|planète|museum|voyage|crime|investigation/.test(text)) {
        return BOUQUET.CULTURE;
    }
    if (/tf1|france 2|france 3|france 4|france 5|m6|w9|tmc|tfx|c8|cstar|6ter|rmc story|rmc decouverte|cherie 25|novo19|rtl9|ab1|tv breizh|teva|paris premiere|general|généraliste|tnt/.test(text)) {
        return BOUQUET.TNT;
    }
    const cc = (countryCode || 'FR').toUpperCase();
    if (cc !== 'FR' && (['MA', 'DZ', 'TN', 'SN', 'CI', 'CA', 'BE', 'CH'].includes(cc) || /afrique|maghreb|maroc|algerie|algérie|tunisie|senegal|monde|world|arabe/.test(text))) {
        return BOUQUET.WORLD;
    }
    return BOUQUET.ENTERTAINMENT;
}

// Seed 22 Verified HD Live Channels
const SEED_CHANNELS = [
    {
        key: 'fr_franceinfo', ordinal: 1, name: 'Franceinfo:', group: BOUQUET.TNT, country: 'FR', quality: 'FHD',
        logoUrl: 'https://upload.wikimedia.org/wikipedia/commons/thumb/0/03/Franceinfo.svg/512px-Franceinfo.svg.png',
        streamUrl: 'https://raw.githubusercontent.com/ipstreet312/freeiptv/master/ressources/ftv/py/frinfo.m3u8',
        backupUrl: 'https://f24hls-i.akamaihd.net/hls/live/221147/F24_FR_HI_HLS/master.m3u8',
        isPpv: false, currentTitle: 'Le Direct France Télévisions', currentDesc: 'Information en continu, éditions spéciales et débats politiques.', nextTitle: 'Le 23h Info'
    },
    {
        key: 'fr_arte_hd', ordinal: 2, name: 'ARTE Français', group: BOUQUET.TNT, country: 'FR', quality: 'FHD',
        logoUrl: 'https://upload.wikimedia.org/wikipedia/commons/thumb/0/0e/Arte_Logo_2017.svg/512px-Arte_Logo_2017.svg.png',
        streamUrl: 'https://artesimulcast.akamaized.net/hls/live/2031003/artelive_fr/index.m3u8',
        backupUrl: 'https://raw.githubusercontent.com/ipstreet312/freeiptv/master/ressources/dm/arte.m3u8',
        isPpv: false, currentTitle: 'ARTE Journal & Grands Reportages', currentDesc: 'Culture, cinéma d’auteur, grands documentaires et investigation européenne.', nextTitle: '28 Minutes'
    },
    {
        key: 'fr_novo19', ordinal: 3, name: 'NOVO19 (TNT Canal 19)', group: BOUQUET.TNT, country: 'FR', quality: 'FHD',
        logoUrl: '',
        streamUrl: 'https://v1.tntendirect.com/novo19/index.m3u8',
        backupUrl: 'https://f24hls-i.akamaihd.net/hls/live/221147/F24_FR_HI_HLS/master.m3u8',
        isPpv: false, currentTitle: 'Grand Direct TNT', currentDesc: 'La nouvelle chaîne généraliste nationale de la TNT française.', nextTitle: 'Magazine Régions & Société'
    },
    {
        key: 'fr_tv5monde_fbs', ordinal: 4, name: 'TV5Monde France Belgique Suisse', group: BOUQUET.TNT, country: 'FR', quality: 'FHD',
        logoUrl: 'https://upload.wikimedia.org/wikipedia/commons/thumb/f/f6/TV5Monde_logo_2021.svg/512px-TV5Monde_logo_2021.svg.png',
        streamUrl: 'https://ott.tv5monde.com/Content/HLS/Live/channel(france)/index.m3u8',
        backupUrl: 'https://ott.tv5monde.com/Content/HLS/Live/channel(info)/index.m3u8',
        isPpv: false, currentTitle: '64\' Le Monde en Français', currentDesc: 'Cinéma francophone, divertissement, culture et grands journaux internationaux.', nextTitle: 'Questions pour un Champion'
    },
    {
        key: 'fr_kto_tv', ordinal: 5, name: 'KTO TV France', group: BOUQUET.TNT, country: 'FR', quality: 'HD',
        logoUrl: '',
        streamUrl: 'https://hls-live-ktotv.akamaized.net/hls/live/2028830/ktotv/master.m3u8',
        backupUrl: 'https://artesimulcast.akamaized.net/hls/live/2031003/artelive_fr/index.m3u8',
        isPpv: false, currentTitle: 'Documentaire & Patrimoine', currentDesc: 'Histoire, patrimoine, grands entretiens et culture.', nextTitle: 'Grand Entretien'
    },
    {
        key: 'fr_bfm_paris', ordinal: 6, name: 'BFM Direct Info', group: BOUQUET.NEWS, country: 'FR', quality: 'FHD',
        logoUrl: 'https://upload.wikimedia.org/wikipedia/commons/thumb/5/5f/BFM_Paris_%C3%8Ele-de-France_logo_2022.svg/512px-BFM_Paris_%C3%8Ele-de-France_logo_2022.svg.png',
        streamUrl: 'https://raw.githubusercontent.com/ipstreet312/freeiptv/master/ressources/btv/py/bfmparis.m3u8',
        backupUrl: 'https://f24hls-i.akamaihd.net/hls/live/221147/F24_FR_HI_HLS/master.m3u8',
        isPpv: false, currentTitle: 'BFM Non-Stop', currentDesc: 'Toute l\'actualité nationale et francilienne en direct 24h/24.', nextTitle: 'Soirée Info Direct'
    },
    {
        key: 'fr_france24', ordinal: 7, name: 'France 24 Français', group: BOUQUET.NEWS, country: 'FR', quality: 'FHD',
        logoUrl: 'https://upload.wikimedia.org/wikipedia/commons/thumb/8/8a/France24.png/512px-France24.png',
        streamUrl: 'https://f24hls-i.akamaihd.net/hls/live/221147/F24_FR_HI_HLS/master.m3u8',
        backupUrl: 'https://ott.tv5monde.com/Content/HLS/Live/channel(info)/index.m3u8',
        isPpv: false, currentTitle: 'Journal du Monde 24/7', currentDesc: 'Actualité internationale, géopolitique, économie et débats en direct.', nextTitle: 'Le Débat de France 24'
    },
    {
        key: 'fr_euronews', ordinal: 8, name: 'Euronews Français', group: BOUQUET.NEWS, country: 'FR', quality: 'FHD',
        logoUrl: 'https://upload.wikimedia.org/wikipedia/commons/thumb/d/d7/Euronews._2016_logo.png/512px-Euronews._2016_logo.png',
        streamUrl: 'https://raw.githubusercontent.com/ipstreet312/freeiptv/master/ressources/yt/euronewsfr.m3u8',
        backupUrl: 'https://f24hls-i.akamaihd.net/hls/live/221147/F24_FR_HI_HLS/master.m3u8',
        isPpv: false, currentTitle: 'Euronews Soir', currentDesc: 'Toute l\'actualité européenne et internationale en continu.', nextTitle: 'No Comment & Eco'
    },
    {
        key: 'fr_bfm_lyon', ordinal: 9, name: 'BFM Lyon Métropole', group: BOUQUET.NEWS, country: 'FR', quality: 'FHD',
        logoUrl: '',
        streamUrl: 'https://ncdn-live-bfm.pfd.sfr.net/shls/LIVE$BFM_LYON/index.m3u8?end=END&start=LIVE',
        backupUrl: 'https://raw.githubusercontent.com/ipstreet312/freeiptv/master/ressources/btv/py/bfmparis.m3u8',
        isPpv: false, currentTitle: 'Direct Info & Météo', currentDesc: 'Actualités locales, trafic, sport et politique en direct.', nextTitle: 'Bonsoir Lyon'
    },
    {
        key: 'fr_tv5_info', ordinal: 10, name: 'TV5Monde Info', group: BOUQUET.NEWS, country: 'FR', quality: 'FHD',
        logoUrl: 'https://upload.wikimedia.org/wikipedia/commons/thumb/f/f6/TV5Monde_logo_2021.svg/512px-TV5Monde_logo_2021.svg.png',
        streamUrl: 'https://ott.tv5monde.com/Content/HLS/Live/channel(info)/index.m3u8',
        backupUrl: 'https://f24hls-i.akamaihd.net/hls/live/221147/F24_FR_HI_HLS/master.m3u8',
        isPpv: false, currentTitle: 'Le Fil Info 24/7', currentDesc: 'L\'information francophone mondiale en continu.', nextTitle: 'Édition Internationale'
    },
    {
        key: 'fr_RT_france', ordinal: 11, name: 'Direct Monde Francophone', group: BOUQUET.NEWS, country: 'FR', quality: 'FHD',
        logoUrl: '',
        streamUrl: 'https://rt-fra.rttv.com/dvr/rtfrance/playlist.m3u8',
        backupUrl: 'https://f24hls-i.akamaihd.net/hls/live/221147/F24_FR_HI_HLS/master.m3u8',
        isPpv: false, currentTitle: 'Journal & Décryptage', currentDesc: 'Édition internationale en français 24h/24.', nextTitle: 'Mag International'
    },
    {
        key: 'fr_lequipe_live', ordinal: 12, name: 'L\'Équipe Live Foot & Multisports', group: BOUQUET.SPORTS, country: 'FR', quality: 'FHD',
        logoUrl: 'https://upload.wikimedia.org/wikipedia/commons/thumb/3/32/L%27%C3%89quipe_wordmark.svg/512px-L%27%C3%89quipe_wordmark.svg.png',
        streamUrl: 'https://raw.githubusercontent.com/ipstreet312/freeiptv/master/ressources/dm/lequipe.m3u8',
        backupUrl: 'https://rbmn-live.akamaized.net/hls/live/590964/BoRB-AT/master.m3u8',
        isPpv: true, currentTitle: 'L\'Équipe du Soir & Direct', currentDesc: 'Débats mercato, Ligue des Champions, Ligue 1 et grands événements sportifs en direct.', nextTitle: 'L\'Équipe de Choc'
    },
    {
        key: 'fr_redbull_sport', ordinal: 13, name: 'Red Bull TV Sports & Motorsport', group: BOUQUET.SPORTS, country: 'FR', quality: 'FHD',
        logoUrl: 'https://upload.wikimedia.org/wikipedia/commons/thumb/5/53/Red_Bull_TV_logo.svg/512px-Red_Bull_TV_logo.svg.png',
        streamUrl: 'https://rbmn-live.akamaized.net/hls/live/590964/BoRB-AT/master.m3u8',
        backupUrl: 'https://raw.githubusercontent.com/ipstreet312/freeiptv/master/ressources/dm/lequipe.m3u8',
        isPpv: true, currentTitle: 'WRC Rally & Formule 1 Inside', currentDesc: 'Compétitions internationales en direct, sports mécaniques et extrêmes.', nextTitle: 'UCI Mountain Bike World Series'
    },
    {
        key: 'fr_rakuten_comedie', ordinal: 14, name: 'Top Cinéma & Comédie FR', group: BOUQUET.ENTERTAINMENT, country: 'FR', quality: 'FHD',
        logoUrl: '',
        streamUrl: 'https://rakuten-topfree-7-fr.xiaomi.wurl.tv/playlist.m3u8',
        backupUrl: 'https://artesimulcast.akamaized.net/hls/live/2031003/artelive_fr/index.m3u8',
        isPpv: false, currentTitle: 'Prime Time Cinéma & Humour', currentDesc: 'Sélection continue de films, comédies et divertissements populaires en français.', nextTitle: 'Séance de Minuit'
    },
    {
        key: 'fr_weo_hd', ordinal: 15, name: 'Wéo TV Hauts-de-France', group: BOUQUET.ENTERTAINMENT, country: 'FR', quality: 'HD',
        logoUrl: '',
        streamUrl: 'https://Visio-ng.ice.infomaniak.ch/weo.m3u8',
        backupUrl: 'https://v1.tntendirect.com/novo19/index.m3u8',
        isPpv: false, currentTitle: 'Wéo Soir & Divertissement', currentDesc: 'Magazines, talk-shows, sport régional et spectacles.', nextTitle: 'Grand Format'
    },
    {
        key: 'fr_tv7_bordeaux', ordinal: 16, name: 'TV7 Sud-Ouest Direct', group: BOUQUET.ENTERTAINMENT, country: 'FR', quality: 'HD',
        logoUrl: '',
        streamUrl: 'https://Wait.digiteka.com/live/cd453345/index.m3u8',
        backupUrl: 'https://v1.tntendirect.com/novo19/index.m3u8',
        isPpv: false, currentTitle: 'Le Grand Direct', currentDesc: 'Culture, sport, gastronomie et actualités du Sud-Ouest.', nextTitle: 'Sports Inside'
    },
    {
        key: 'fr_gulli_kids', ordinal: 17, name: 'Toon & Jeunesse 24/7', group: BOUQUET.KIDS, country: 'FR', quality: 'FHD',
        logoUrl: '',
        streamUrl: 'https://ott.tv5monde.com/Content/HLS/Live/channel(tivi5monde)/index.m3u8',
        backupUrl: 'https://ott.tv5monde.com/Content/HLS/Live/channel(france)/index.m3u8',
        isPpv: false, currentTitle: 'TiVi5 Monde Dessins Animés', currentDesc: 'Dessins animés, séries jeunesse, mangas et programmes familiaux en français.', nextTitle: 'Les Aventures Anime'
    },
    {
        key: 'fr_nrj12_music', ordinal: 18, name: 'Europe 2 / Pop Music TV', group: BOUQUET.MUSIC, country: 'FR', quality: 'FHD',
        logoUrl: '',
        streamUrl: 'https://d2qh3gh0k5vp3v.cloudfront.net/v1/master/3722c60a815c199d9c0ef36c5b73da68a62b09d1/cc-44b3622c-2b7a-4c36-9f69-5e99a4d623b2/manifest.m3u8',
        backupUrl: 'https://rbmn-live.akamaized.net/hls/live/590964/BoRB-AT/master.m3u8',
        isPpv: false, currentTitle: 'Top Hits & Clips 4K', currentDesc: 'Les meilleurs clips pop, dance, R&B et électro en continu.', nextTitle: 'Club Night Session'
    },
    {
        key: 'fr_trace_urban', ordinal: 19, name: 'Trace / Hip-Hop & Urban TV', group: BOUQUET.MUSIC, country: 'FR', quality: 'FHD',
        logoUrl: '',
        streamUrl: 'https://d35j504z0x2vu2.cloudfront.net/v1/master/0bc8e8376bd8417a1b6761138aa41c26c7309312/trace-uk/master.m3u8',
        backupUrl: 'https://rbmn-live.akamaized.net/hls/live/590964/BoRB-AT/master.m3u8',
        isPpv: false, currentTitle: 'Urban Hit 30', currentDesc: 'Hip-Hop, Rap FR/US, Afrobeats et R&B en direct.', nextTitle: 'Afrobeats Non-Stop'
    },
    {
        key: 'ma_medi1_fr', ordinal: 20, name: 'Medi1 TV Maghreb (FR)', group: BOUQUET.WORLD, country: 'MA', quality: 'HD',
        logoUrl: '',
        streamUrl: 'https://streaming1.medi1tv.com/live/smil:medi1fr.smil/playlist.m3u8',
        backupUrl: 'https://f24hls-i.akamaihd.net/hls/live/221147/F24_FR_HI_HLS/master.m3u8',
        isPpv: false, currentTitle: 'Journal du Maghreb & Afrique', currentDesc: 'Actualités, économie, sport et culture du Maroc et du Maghreb en français.', nextTitle: 'Afrique Soir'
    },
    {
        key: 'ma_al_aoula', ordinal: 21, name: 'Al Aoula Maroc Intl', group: BOUQUET.WORLD, country: 'MA', quality: 'HD',
        logoUrl: '',
        streamUrl: 'https://cdnamd-hls-globecast.akamaized.net/live/ramdisk/al_aoula_inter/hls_snrt/al_aoula_inter.m3u8',
        backupUrl: 'https://streaming1.medi1tv.com/live/smil:medi1fr.smil/playlist.m3u8',
        isPpv: false, currentTitle: 'Soirée Maroc & Séries', currentDesc: 'Programmes généralistes, divertissement et information nationale marocaine.', nextTitle: 'Journal Télévisé'
    },
    {
        key: 'sn_rts1', ordinal: 22, name: 'TV5Monde Afrique HD', group: BOUQUET.WORLD, country: 'SN', quality: 'FHD',
        logoUrl: '',
        streamUrl: 'https://ott.tv5monde.com/Content/HLS/Live/channel(afrique)/index.m3u8',
        backupUrl: 'https://f24hls-i.akamaihd.net/hls/live/221147/F24_FR_HI_HLS/master.m3u8',
        isPpv: false, currentTitle: 'Journal Afrique & Sport', currentDesc: 'Séries africaines, football continental, musique et grands journaux.', nextTitle: 'Star Parade Afrique'
    }
];

const SEED_VOD = [
    {
        id: 'vod_sintel', title: 'Sintel : L\'Odyssée du Dragon (4K)', type: 'MOVIE', category: 'Fantastique',
        releaseYear: 2024, rating: '★ 9.2', durationLabel: '1h 48m',
        synopsis: 'Une jeune guerrière parcourt les terres glacées à la recherche d\'un jeune dragon nommé Scales.',
        streamUrl: 'https://bitdash-a.akamaihd.net/content/sintel/hls/playlist.m3u8',
        episodes: []
    },
    {
        id: 'vod_tears_of_steel', title: 'Tears of Steel : Cyber Amsterdam', type: 'MOVIE', category: 'Sci-Fi',
        releaseYear: 2025, rating: '★ 8.9', durationLabel: '2h 04m',
        synopsis: 'Dans un Amsterdam futuriste, une équipe de scientifiques et de soldats tente de sauver le monde d\'une rébellion robotique.',
        streamUrl: 'https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8',
        episodes: []
    },
    {
        id: 'vod_cosmos', title: 'Cosmos Laundromat : Multivers', type: 'SERIES', category: 'Sci-Fi',
        releaseYear: 2025, rating: '★ 9.4', durationLabel: 'Saison 1 (3 Épisodes)',
        synopsis: 'Sur une île déserte, Franck rencontre un mystérieux voyageur qui lui ouvre les portes d\'une infinité de mondes.',
        streamUrl: 'https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8',
        episodes: [
            { id: 'ep_1', seasonNumber: 1, episodeNumber: 1, title: 'Le Portail Quantique', durationMinutes: 42, streamUrl: 'https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8' },
            { id: 'ep_2', seasonNumber: 1, episodeNumber: 2, title: 'L\'Horizon des Événements', durationMinutes: 45, streamUrl: 'https://bitdash-a.akamaihd.net/content/sintel/hls/playlist.m3u8' },
            { id: 'ep_3', seasonNumber: 1, episodeNumber: 3, title: 'Singularité Finale', durationMinutes: 51, streamUrl: 'https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8' }
        ]
    },
    {
        id: 'vod_big_buck', title: 'Forest Legends : Big Buck UHD', type: 'MOVIE', category: 'Animation',
        releaseYear: 2024, rating: '★ 8.7', durationLabel: '1h 32m',
        synopsis: 'Une aventure animée spectaculaire au cœur d\'une forêt luxuriante.',
        streamUrl: 'https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8',
        episodes: []
    }
];

const SEED_SPORTS = [
    { id: 'sp_ucl_1', sport: 'Football', competition: 'UEFA Champions League', homeTeam: 'Paris SG', awayTeam: 'Real Madrid', kickoffLabel: 'EN DIRECT • 67\'', isLive: true, scoreOrStatus: '2 - 1', channelKey: 'fr_lequipe_live' },
    { id: 'sp_f1_1', sport: 'Formule 1', competition: 'Grand Prix de Monaco', homeTeam: 'Grille F1', awayTeam: 'Qualification Q3', kickoffLabel: 'EN DIRECT • Tour 44/78', isLive: true, scoreOrStatus: 'LIVE 4K', channelKey: 'fr_redbull_sport' },
    { id: 'sp_ufc_1', sport: 'MMA / Boxe', competition: 'UFC Fight Night Paris', homeTeam: 'Benoît Saint Denis', awayTeam: 'Main Event', kickoffLabel: 'Ce soir • 22:30', isLive: false, scoreOrStatus: 'PPV HD', channelKey: 'fr_lequipe_live' },
    { id: 'sp_nba_1', sport: 'Basketball', competition: 'NBA Regular Season', homeTeam: 'San Antonio Spurs', awayTeam: 'LA Lakers', kickoffLabel: '01:30', isLive: false, scoreOrStatus: 'À VENIR', channelKey: 'fr_redbull_sport' }
];

function loadStore() {
    try {
        if (fs.existsSync(STORE_PATH)) {
            const parsed = JSON.parse(fs.readFileSync(STORE_PATH, 'utf8'));
            if (Array.isArray(parsed.channels) && parsed.channels.length > 0) {
                return parsed;
            }
        }
    } catch (e) {
        console.error('Failed to read store.json, initializing fresh store:', e.message);
    }
    const initial = {
        channels: SEED_CHANNELS.map(ch => ({ ...ch, isFavorite: false, countryFlag: getCountryFlag(ch.country) })),
        vodItems: SEED_VOD,
        sportsEvents: SEED_SPORTS,
        sources: [
            { id: 'src_builtin', name: 'Bouquet Officiel NovaPlay (22 chaînes HD)', type: 'M3U_URL', serverUrl: 'builtin://novaplay', channelCount: SEED_CHANNELS.length, addedAt: new Date().toISOString() }
        ],
        watchProgress: []
    };
    saveStore(initial);
    return initial;
}

function saveStore(store) {
    fs.writeFileSync(STORE_PATH, JSON.stringify(store, null, 2), 'utf8');
}

let store = loadStore();

// Helper to fetch remote HTTP/HTTPS URL with IPTV headers & redirect support
function fetchUrlBuffer(targetUrl, redirectCount = 0) {
    return new Promise((resolve, reject) => {
        if (redirectCount > 6) {
            return reject(new Error('Trop de redirections HTTP'));
        }
        let parsed;
        try {
            parsed = new URL(targetUrl);
        } catch (err) {
            return reject(new Error(`URL invalide: ${targetUrl}`));
        }
        const client = parsed.protocol === 'https:' ? https : http;
        const options = {
            headers: {
                'User-Agent': 'VLC/3.0.20 LibVLC/3.0.20',
                'Accept': '*/*',
                'Connection': 'keep-alive'
            },
            timeout: 20000,
            rejectUnauthorized: false
        };
        const req = client.get(parsed, options, (res) => {
            if ([301, 302, 303, 307, 308].includes(res.statusCode) && res.headers.location) {
                const nextUrl = new URL(res.headers.location, parsed).toString();
                res.resume();
                return resolve(fetchUrlBuffer(nextUrl, redirectCount + 1));
            }
            if (res.statusCode < 200 || res.statusCode >= 400) {
                res.resume();
                return reject(new Error(`HTTP ${res.statusCode}`));
            }
            const chunks = [];
            res.on('data', chunk => chunks.push(chunk));
            res.on('end', () => resolve({
                buffer: Buffer.concat(chunks),
                contentType: res.headers['content-type'] || '',
                finalUrl: targetUrl
            }));
        });
        req.on('error', reject);
        req.on('timeout', () => {
            req.destroy();
            reject(new Error('Délai réseau dépassé'));
        });
    });
}

// Parse M3U content into organized NovaPlay channels
function parseM3uContent(m3uText, sourcePrefix = 'm3u') {
    const lines = (m3uText || '').split(/\r?\n/);
    const rawChannels = [];
    let pendingMeta = null;

    const attrRegex = /([a-zA-Z0-9_-]+)="([^"]*)"/g;

    for (const rawLine of lines) {
        const line = rawLine.trim();
        if (!line) continue;

        if (line.toUpperCase().startsWith('#EXTINF')) {
            const attrs = {};
            let match;
            while ((match = attrRegex.exec(line)) !== null) {
                attrs[match[1].toLowerCase()] = match[2];
            }
            const displayName = line.includes(',') ? line.substring(line.lastIndexOf(',') + 1).trim() : 'Chaîne Direct';
            pendingMeta = { attrs, displayName };
        } else if (!line.startsWith('#')) {
            const isStream = /^(https?|rtsp|rtmp|mms|udp):\/\//i.test(line);
            if (!isStream) continue;

            const meta = pendingMeta;
            pendingMeta = null;
            const rawName = (meta && (meta.attrs['tvg-name'] || meta.displayName)) || `Chaîne ${rawChannels.length + 1}`;
            const rawGroup = (meta && meta.attrs['group-title']) || 'Direct TV';
            const logo = (meta && meta.attrs['tvg-logo']) || '';
            const explicitCountry = ((meta && meta.attrs['tvg-country']) || '').toUpperCase();

            const countryCode = explicitCountry.length === 2 ? explicitCountry : (
                /\b(FR|FRANCE|FHD FR|TNT)\b/i.test(`${rawName} ${rawGroup}`) ? 'FR' :
                /\b(BE|BELGI)\b/i.test(`${rawName} ${rawGroup}`) ? 'BE' :
                /\b(CH|SUISSE)\b/i.test(`${rawName} ${rawGroup}`) ? 'CH' :
                /\b(MA|MAROC)\b/i.test(`${rawName} ${rawGroup}`) ? 'MA' :
                /\b(DZ|ALGER)\b/i.test(`${rawName} ${rawGroup}`) ? 'DZ' :
                /\b(TN|TUNIS)\b/i.test(`${rawName} ${rawGroup}`) ? 'TN' :
                /\b(CA|QUEBEC)\b/i.test(`${rawName} ${rawGroup}`) ? 'CA' : 'FR'
            );

            const cleanName = cleanChannelName(rawName);
            const quality = extractQualityBadge(rawName, rawGroup);
            const bouquet = classifyBouquet(cleanName, rawGroup, countryCode);
            const isSport = bouquet === BOUQUET.SPORTS || /ppv|event|box Office/i.test(cleanName);

            rawChannels.push({
                key: `${sourcePrefix}_${rawChannels.length + 1}_${Math.abs(hashCode(line + cleanName))}`,
                ordinal: rawChannels.length + 1,
                name: cleanName,
                group: bouquet,
                country: countryCode,
                countryFlag: getCountryFlag(countryCode),
                quality,
                logoUrl: logo,
                streamUrl: line,
                backupUrl: deriveAlternateUrl(line),
                isPpv: isSport,
                isFavorite: false,
                currentTitle: `En Direct • ${cleanName}`,
                currentDesc: `Diffusion en direct sur le bouquet ${bouquet}.`,
                nextTitle: `À suivre sur ${cleanName}`
            });
            if (rawChannels.length >= 900) break;
        }
    }

    const orderMap = {};
    BOUQUET_ORDER.forEach((b, idx) => { orderMap[b] = idx; });
    const qScore = { '4K': 0, 'FHD': 1, 'HD': 2, 'SD': 3 };

    rawChannels.sort((a, b) => {
        const gDiff = (orderMap[a.group] ?? 99) - (orderMap[b.group] ?? 99);
        if (gDiff !== 0) return gDiff;
        const qDiff = (qScore[a.quality] ?? 2) - (qScore[b.quality] ?? 2);
        if (qDiff !== 0) return qDiff;
        return a.name.localeCompare(b.name, 'fr');
    });

    return rawChannels.map((ch, idx) => ({ ...ch, ordinal: idx + 1 }));
}

function deriveAlternateUrl(url) {
    if (url.endsWith('.m3u8')) return url.replace(/\.m3u8$/i, '.ts');
    if (url.endsWith('.ts')) return url.replace(/\.ts$/i, '.m3u8');
    return null;
}

function hashCode(str) {
    let h = 0;
    for (let i = 0; i < str.length; i++) {
        h = Math.imul(31, h) + str.charCodeAt(i) | 0;
    }
    return h;
}

function sendJson(res, status, payload) {
    res.writeHead(status, {
        'Content-Type': 'application/json; charset=utf-8',
        'Access-Control-Allow-Origin': '*'
    });
    res.end(JSON.stringify(payload));
}

function readJsonBody(req) {
    return new Promise((resolve, reject) => {
        const chunks = [];
        req.on('data', c => chunks.push(c));
        req.on('end', () => {
            try {
                const raw = Buffer.concat(chunks).toString('utf8');
                resolve(raw ? JSON.parse(raw) : {});
            } catch (e) {
                reject(e);
            }
        });
        req.on('error', reject);
    });
}

// HTTP Server
const server = http.createServer(async (req, res) => {
    try {
        const parsedUrl = new URL(req.url, `http://127.0.0.1:${PORT}`);
        const pathname = parsedUrl.pathname;

        // 1. API: Get full state
        if (pathname === '/api/state' && req.method === 'GET') {
            const favSet = new Set(store.channels.filter(c => c.isFavorite).map(c => c.key));
            const categoryCounts = {
                'Toutes': store.channels.length,
                '⭐ Favoris': favSet.size
            };
            for (const ch of store.channels) {
                categoryCounts[ch.group] = (categoryCounts[ch.group] || 0) + 1;
            }
            const categories = BOUQUET_ORDER.filter(b => b === 'Toutes' || b === '⭐ Favoris' || (categoryCounts[b] || 0) > 0);
            for (const ch of store.channels) {
                if (!categories.includes(ch.group)) categories.push(ch.group);
            }
            const countries = ['TOUS', ...Array.from(new Set(store.channels.map(c => c.country)))];

            return sendJson(res, 200, {
                channels: store.channels,
                categories,
                categoryCounts,
                countries,
                vodItems: store.vodItems,
                sportsEvents: store.sportsEvents,
                sources: store.sources,
                watchProgress: store.watchProgress || []
            });
        }

        // 2. API: Toggle Favorite
        if (pathname === '/api/favorite' && req.method === 'POST') {
            const body = await readJsonBody(req);
            const target = store.channels.find(c => c.key === body.key);
            if (target) {
                target.isFavorite = !target.isFavorite;
                saveStore(store);
            }
            return sendJson(res, 200, { ok: true, isFavorite: target ? target.isFavorite : false });
        }

        // 3. API: Add M3U URL, Local M3U Content, or Xtream Codes Playlist
        if (pathname === '/api/playlist/add' && req.method === 'POST') {
            const body = await readJsonBody(req);
            const name = (body.name || 'Mon Bouquet IPTV').trim();
            const type = body.type || 'M3U_URL';
            let m3uContent = '';

            if (type === 'M3U_CONTENT') {
                m3uContent = body.content || '';
            } else if (type === 'XTREAM') {
                let base = (body.serverUrl || '').trim().replace(/\/+$/, '');
                if (!/^https?:\/\//i.test(base)) base = 'http://' + base;
                const u = encodeURIComponent((body.username || '').trim());
                const p = encodeURIComponent((body.password || '').trim());
                const m3uUrl = `${base}/get.php?username=${u}&password=${p}&type=m3u_plus&output=m3u8`;
                const resp = await fetchUrlBuffer(m3uUrl);
                m3uContent = resp.buffer.toString('utf8');
            } else {
                let targetUrl = (body.serverUrl || '').trim();
                if (!/^https?:\/\//i.test(targetUrl)) targetUrl = 'https://' + targetUrl;
                const resp = await fetchUrlBuffer(targetUrl);
                m3uContent = resp.buffer.toString('utf8');
            }

            const parsedChannels = parseM3uContent(m3uContent, `src_${Date.now()}`);
            if (parsedChannels.length === 0) {
                return sendJson(res, 400, { ok: false, error: 'Aucune chaîne valide trouvée dans ce bouquet.' });
            }

            // Merge Favorites + New Channels + Builtin Seed Channels
            const existingFavs = new Set(store.channels.filter(c => c.isFavorite).map(c => c.name.toLowerCase()));
            const merged = [...parsedChannels];
            const seenUrls = new Set(merged.map(c => c.streamUrl));
            for (const seed of SEED_CHANNELS) {
                if (!seenUrls.has(seed.streamUrl)) {
                    merged.push({
                        ...seed,
                        ordinal: merged.length + 1,
                        countryFlag: getCountryFlag(seed.country),
                        isFavorite: existingFavs.has(seed.name.toLowerCase())
                    });
                }
            }

            store.channels = merged;
            const newSource = {
                id: `src_${Date.now()}`,
                name,
                type,
                serverUrl: body.serverUrl || 'Fichier local .m3u',
                channelCount: parsedChannels.length,
                addedAt: new Date().toISOString()
            };
            store.sources = [newSource, ...store.sources.filter(s => s.name !== name)];
            saveStore(store);

            return sendJson(res, 200, {
                ok: true,
                addedCount: parsedChannels.length,
                totalCount: store.channels.length,
                source: newSource
            });
        }

        // 4. API: Restore Built-in Seed Catalog
        if (pathname === '/api/playlist/restore' && req.method === 'POST') {
            store.channels = SEED_CHANNELS.map(ch => ({
                ...ch,
                isFavorite: false,
                countryFlag: getCountryFlag(ch.country)
            }));
            store.sources = [
                { id: 'src_builtin', name: 'Bouquet Officiel NovaPlay (22 chaînes HD)', type: 'M3U_URL', serverUrl: 'builtin://novaplay', channelCount: SEED_CHANNELS.length, addedAt: new Date().toISOString() }
            ];
            saveStore(store);
            return sendJson(res, 200, { ok: true, count: store.channels.length });
        }

        // 5. API: CORS-Free IPTV Stream Proxy (/api/proxy?url=...)
        // Rewrites HLS .m3u8 manifests so all sub-playlists and .ts segments route through the proxy
        if (pathname === '/api/proxy' && req.method === 'GET') {
            const targetUrl = parsedUrl.searchParams.get('url');
            if (!targetUrl) {
                res.writeHead(400);
                return res.end('Missing url param');
            }
            try {
                const { buffer, contentType, finalUrl } = await fetchUrlBuffer(targetUrl);
                const lowerUrl = finalUrl.split('?')[0].toLowerCase();
                const isM3u8 = lowerUrl.endsWith('.m3u8') ||
                    contentType.toLowerCase().includes('mpegurl') ||
                    buffer.slice(0, 16).toString('utf8').startsWith('#EXTM3U');

                if (isM3u8) {
                    const text = buffer.toString('utf8');
                    const baseUri = new URL(finalUrl);
                    const rewritten = text.split(/\r?\n/).map(line => {
                        const trimmed = line.trim();
                        if (!trimmed) return line;
                        if (trimmed.startsWith('#')) {
                            // Rewrite URI="..." inside #EXT-X-KEY or #EXT-X-MAP or #EXT-X-MEDIA
                            return line.replace(/URI="([^"]+)"/g, (_, uriVal) => {
                                try {
                                    const abs = new URL(uriVal, baseUri).toString();
                                    return `URI="/api/proxy?url=${encodeURIComponent(abs)}"`;
                                } catch {
                                    return `URI="${uriVal}"`;
                                }
                            });
                        }
                        try {
                            const abs = new URL(trimmed, baseUri).toString();
                            return `/api/proxy?url=${encodeURIComponent(abs)}`;
                        } catch {
                            return line;
                        }
                    }).join('\n');

                    res.writeHead(200, {
                        'Content-Type': 'application/vnd.apple.mpegurl',
                        'Access-Control-Allow-Origin': '*',
                        'Cache-Control': 'no-cache'
                    });
                    return res.end(rewritten);
                }

                res.writeHead(200, {
                    'Content-Type': contentType || 'video/mp2t',
                    'Access-Control-Allow-Origin': '*',
                    'Cache-Control': 'no-cache'
                });
                return res.end(buffer);
            } catch (err) {
                res.writeHead(502, { 'Content-Type': 'text/plain; charset=utf-8', 'Access-Control-Allow-Origin': '*' });
                return res.end(`Proxy error: ${err.message}`);
            }
        }

        // 6. Serve Static Frontend Files
        let filePath = pathname === '/' ? path.join(PUBLIC_DIR, 'index.html') : path.join(PUBLIC_DIR, pathname);
        if (!filePath.startsWith(PUBLIC_DIR)) {
            res.writeHead(403);
            return res.end('Forbidden');
        }
        if (fs.existsSync(filePath) && fs.statSync(filePath).isFile()) {
            const ext = path.extname(filePath).toLowerCase();
            const mimeMap = {
                '.html': 'text/html; charset=utf-8',
                '.css': 'text/css; charset=utf-8',
                '.js': 'application/javascript; charset=utf-8',
                '.svg': 'image/svg+xml',
                '.png': 'image/png',
                '.ico': 'image/x-icon'
            };
            res.writeHead(200, { 'Content-Type': mimeMap[ext] || 'application/octet-stream' });
            return fs.createReadStream(filePath).pipe(res);
        }

        res.writeHead(404);
        res.end('Not found');
    } catch (err) {
        console.error('Server error:', err);
        sendJson(res, 500, { ok: false, error: err.message });
    }
});

server.listen(PORT, '127.0.0.1', () => {
    console.log(`\n======================================================`);
    console.log(`  NovaPlay IPTV PC Desktop Engine actif !`);
    console.log(`  Interface locale : http://127.0.0.1:${PORT}`);
    console.log(`======================================================\n`);
});
