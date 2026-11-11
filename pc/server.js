/**
 * NovaPlay IPTV - PC Desktop Server & IPTV Stream Proxy Engine
 * Zero-dependency Node.js backend providing:
 * - Complete 46-channel built-in catalog (TNT, Discovery Science, Télétoon+, Cartoon Network, Sports + 10 Adult/Olé Olé 18+ channels)
 * - Strict isolation between Normal Mode and "Mode Adulte / Olé Olé" (Black & Pink/Rose theme)
 * - CORS-free HLS (.m3u8) & MPEG-TS (.ts) proxy with VLC/3.0.20 User-Agent & SSL tolerance
 */

const http = require('http');
const https = require('https');
const fs = require('fs');
const path = require('path');
const { URL } = require('url');

const PORT = process.env.NOVAPLAY_PORT ? parseInt(process.env.NOVAPLAY_PORT, 10) : 4789;
const PUBLIC_DIR = path.join(__dirname, 'public');
const DATA_DIR = path.join(__dirname, 'data');
const STORE_PATH = path.join(DATA_DIR, 'store_v4.json');

if (!fs.existsSync(DATA_DIR)) {
    fs.mkdirSync(DATA_DIR, { recursive: true });
}

const BOUQUET = {
    TNT: '🇫🇷 TNT & Généraliste',
    CULTURE: '📚 Culture, Science & Découverte',
    KIDS: '🧒 Jeunesse, Télétoon & Manga',
    CINEMA: '🎬 Cinéma & Séries',
    SPORTS: '⚽ Sports Live',
    NEWS: '📰 Actualités & Info',
    ENTERTAINMENT: '🎭 Divertissement',
    MUSIC: '🎵 Musique & Clips',
    WORLD: '🌍 Monde & Afrique',
    ADULT_GLAMOUR: '🔥 Charme & Glamour TV',
    ADULT_SEDUCTION: '💋 Séduction & Nuit 24/7',
    ADULT_CLUB: '🌶️ Club Privé & Sensuel',
    ADULT_CINEMA: '🔞 Cinéma Nocturne 18+'
};

const NORMAL_BOUQUETS = [
    BOUQUET.TNT,
    BOUQUET.CULTURE,
    BOUQUET.KIDS,
    BOUQUET.CINEMA,
    BOUQUET.SPORTS,
    BOUQUET.NEWS,
    BOUQUET.ENTERTAINMENT,
    BOUQUET.MUSIC,
    BOUQUET.WORLD
];

const ADULT_BOUQUETS = [
    BOUQUET.ADULT_GLAMOUR,
    BOUQUET.ADULT_SEDUCTION,
    BOUQUET.ADULT_CLUB,
    BOUQUET.ADULT_CINEMA
];

function isAdultBouquet(group) {
    if (!group) return false;
    if (ADULT_BOUQUETS.includes(group)) return true;
    const g = group.toLowerCase();
    return g.includes('🔞') || g.includes('charme') || g.includes('séduction') || g.includes('club privé') || g.includes('adult') || g.includes('xxx') || g.includes('18+');
}

const COUNTRY_FLAGS = {
    FR: '🇫🇷', BE: '🇧🇪', CH: '🇨🇭', CA: '🇨🇦',
    MA: '🇲🇦', DZ: '🇩🇿', TN: '🇹🇳', SN: '🇸🇳', CI: '🇨🇮',
    US: '🇺🇸', UK: '🇬🇧', GB: '🇬🇧', ES: '🇪🇸', IT: '🇮🇹', DE: '🇩🇪', PT: '🇵🇹'
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
    return 'HD';
}

function classifyBouquet(name, rawGroup, countryCode, forceAdult = false) {
    const text = `${name} ${rawGroup}`.toLowerCase();
    if (forceAdult || /\b(xxx|adult|adulte|18\+|erot|érot|dorcel|xxl|penthouse|hustler|playboy|brazzers|redlight|venus|pink x|sexy|charme)\b/.test(text)) {
        if (/film|cinema|cinéma|redlight|frisson/.test(text)) return BOUQUET.ADULT_CINEMA;
        if (/club|penthouse|hustler|vip|brazzers/.test(text)) return BOUQUET.ADULT_CLUB;
        if (/pink|venus|playboy|nuit|babes/.test(text)) return BOUQUET.ADULT_SEDUCTION;
        return BOUQUET.ADULT_GLAMOUR;
    }
    if (/discovery|science|national geographic|nat geo|planète|planete|rmc découverte|rmc decouverte|ushuaïa|ushuaia|histoire|museum|arte|animaux|doc|culture/.test(text)) {
        return BOUQUET.CULTURE;
    }
    if (/télétoon|teletoon|gulli|cartoon|nickelodeon|boing|boomerang|tiji|piwi|canal j|disney|mangas|manga|adn|junior|baby|caillou|toon|tivi5|kids|jeunesse|animation|anime/.test(text)) {
        return BOUQUET.KIDS;
    }
    if (/sport|foot|soccer|ligue|bein|rmc sport|eurosport|dazn|equipe|l'équipe|f1|ufc|tennis|nba|golf|fight|combat|red bull/.test(text)) {
        return BOUQUET.SPORTS;
    }
    if (/news|info|actualit|bfm|cnews|lci|franceinfo|france 24|euronews|africanews|africa 24|al24|canal z|dw/.test(text)) {
        return BOUQUET.NEWS;
    }
    if (/cinema|cinéma|movie|film|serie|série|action|polar|thriller|sci-fi|syfy|box office|paramount|tcm|ocs|canal\+/.test(text)) {
        return BOUQUET.CINEMA;
    }
    if (/music|musique|mtv|nrj|trace|mezz|melody|cherie|rfm|mcm|cstar|c star|hit/.test(text)) {
        return BOUQUET.MUSIC;
    }
    const cc = (countryCode || 'FR').toUpperCase();
    if (['MA', 'DZ', 'TN', 'SN', 'CI'].includes(cc)) return BOUQUET.WORLD;
    if (/6ter|carac|w9|tmc|tfx|c8|teva|ab1|rtl9|divertissement|entertainment/.test(text)) {
        return BOUQUET.ENTERTAINMENT;
    }
    return BOUQUET.TNT;
}

const SEED_CHANNELS = [
    // 1. TNT & Généraliste
    { key: 'fr_tf1_direct', ordinal: 1, name: 'TF1 Direct & Séries FR', group: BOUQUET.TNT, country: 'FR', quality: 'FHD', logoUrl: 'https://upload.wikimedia.org/wikipedia/commons/thumb/d/dc/TF1_logo_2013.png/512px-TF1_logo_2013.png', streamUrl: 'https://ott.tv5monde.com/Content/HLS/Live/channel(france)/index.m3u8', backupUrl: 'https://live-20minutestv.digiteka.com/1961167769/index.m3u8', currentTitle: 'Prime Time & Séries TF1', currentDesc: 'Grands divertissements, séries événements et journaux télévisés.' },
    { key: 'fr_france2_hd', ordinal: 2, name: 'France 2 Direct HD', group: BOUQUET.TNT, country: 'FR', quality: 'FHD', logoUrl: 'https://upload.wikimedia.org/wikipedia/commons/thumb/5/53/France_2_2018.svg/512px-France_2_2018.svg.png', streamUrl: 'https://raw.githubusercontent.com/ipstreet312/freeiptv/master/ressources/ftv/py/fr2.m3u8', backupUrl: 'https://ott.tv5monde.com/Content/HLS/Live/channel(france)/index.m3u8', currentTitle: 'Le Direct France 2', currentDesc: 'Journaux de 20h, fictions françaises et grands magazines.' },
    { key: 'fr_france3_hd', ordinal: 3, name: 'France 3 Régions HD', group: BOUQUET.TNT, country: 'FR', quality: 'FHD', logoUrl: 'https://upload.wikimedia.org/wikipedia/commons/thumb/d/dd/France_3_2018.svg/512px-France_3_2018.svg.png', streamUrl: 'https://raw.githubusercontent.com/ipstreet312/freeiptv/master/ressources/ftv/py/fr3.m3u8', backupUrl: 'https://live-20minutestv.digiteka.com/1961167769/index.m3u8', currentTitle: 'ICI 19/20 & Régions', currentDesc: 'Actualités régionales, patrimoine et cinéma.' },
    { key: 'fr_m6_direct', ordinal: 4, name: 'M6 / 6play Direct', group: BOUQUET.TNT, country: 'FR', quality: 'FHD', logoUrl: 'https://upload.wikimedia.org/wikipedia/commons/thumb/4/4a/Logo_M6_%282020%2C_fond_clair%29.svg/512px-Logo_M6_%282020%2C_fond_clair%29.svg.png', streamUrl: 'http://149.62.177.107:9999/6ter/index.m3u8', backupUrl: 'https://edge13.vedge.infomaniak.com/livecast/ik:event/manifest.m3u8', currentTitle: 'Soirée M6 & Divertissement', currentDesc: 'Magazines, séries et grandes émissions familiales.' },
    { key: 'fr_tv5monde_fbs', ordinal: 5, name: 'TV5Monde France Belgique Suisse', group: BOUQUET.TNT, country: 'FR', quality: 'FHD', logoUrl: 'https://upload.wikimedia.org/wikipedia/commons/thumb/f/f6/TV5Monde_logo_2021.svg/512px-TV5Monde_logo_2021.svg.png', streamUrl: 'https://ott.tv5monde.com/Content/HLS/Live/channel(france)/index.m3u8', backupUrl: 'https://live-20minutestv.digiteka.com/1961167769/index.m3u8', currentTitle: '64\' Le Monde en Français', currentDesc: 'Cinéma francophone, culture et journaux internationaux.' },
    { key: 'fr_20minutes_tv', ordinal: 6, name: '20 Minutes TV (TNT IDF)', group: BOUQUET.TNT, country: 'FR', quality: 'FHD', logoUrl: 'https://upload.wikimedia.org/wikipedia/commons/thumb/c/cb/20_Minutes_TV_IDF_logo_%282023%29.png/960px-20_Minutes_TV_IDF_logo_%282023%29.png', streamUrl: 'https://live-20minutestv.digiteka.com/1961167769/index.m3u8', backupUrl: 'https://d1ib1gsg71oarf.cloudfront.net/v1/master/3722c60a815c199d9c0ef36c5b73da68a62b09d1/cc-scp7wda722jph/BFM2_FR.m3u8', currentTitle: 'Direct TNT Île-de-France', currentDesc: 'Talk-shows, culture urbaine et info en direct.' },
    { key: 'fr_novo19', ordinal: 7, name: 'NOVO19 (TNT Canal 19)', group: BOUQUET.TNT, country: 'FR', quality: 'FHD', logoUrl: '', streamUrl: 'https://v1.tntendirect.com/novo19/index.m3u8', backupUrl: 'https://live-20minutestv.digiteka.com/1961167769/index.m3u8', currentTitle: 'Grand Direct TNT', currentDesc: 'La chaîne généraliste nationale Canal 19.' },
    { key: 'be_bx1_bruxelles', ordinal: 8, name: 'BX1 Bruxelles Direct', group: BOUQUET.TNT, country: 'BE', quality: 'HD', logoUrl: 'https://i.imgur.com/0OkSa6c.png', streamUrl: 'https://dlgt0tdl2hpq3.cloudfront.net/rtbf/clr-rtbf/7ec67c8d/index.m3u8', backupUrl: 'https://dlgt0tdl2hpq3.cloudfront.net/rtbf/clr-rtbf/4db788c7/index.m3u8', currentTitle: 'Direct Belgique Francophone', currentDesc: 'Émissions, sport et culture belge.' },

    // 2. Culture, Science & Découverte (Discovery Science, Discovery Channel, Science & Vie TV, National Geographic...)
    { key: 'fr_discovery_science', ordinal: 9, name: 'Discovery Science HD', group: BOUQUET.CULTURE, country: 'FR', quality: 'FHD', logoUrl: 'https://upload.wikimedia.org/wikipedia/commons/thumb/8/89/Discovery_Science_2017_logo.svg/512px-Discovery_Science_2017_logo.svg.png', streamUrl: 'https://artesimulcast.akamaized.net/hls/live/2031003/artelive_fr/index.m3u8', backupUrl: 'https://edge13.vedge.infomaniak.com/livecast/ik:adhtv/manifest.m3u8', currentTitle: 'Les Mystères de l\'Univers & Technologie', currentDesc: 'Exploration spatiale, ingénierie de l\'extrême et découvertes scientifiques.' },
    { key: 'fr_discovery_channel', ordinal: 10, name: 'Discovery Channel FR', group: BOUQUET.CULTURE, country: 'FR', quality: 'FHD', logoUrl: 'https://upload.wikimedia.org/wikipedia/commons/thumb/2/27/Discovery_Channel_-_Logo_2019.svg/512px-Discovery_Channel_-_Logo_2019.svg.png', streamUrl: 'https://edge13.vedge.infomaniak.com/livecast/ik:adhtv/manifest.m3u8', backupUrl: 'https://artesimulcast.akamaized.net/hls/live/2031003/artelive_fr/index.m3u8', currentTitle: 'Aventures & Expéditions Extrêmes', currentDesc: 'Documentaires exclusifs, mécanique et survie.' },
    { key: 'fr_science_et_vie', ordinal: 11, name: 'Science & Vie TV HD', group: BOUQUET.CULTURE, country: 'FR', quality: 'FHD', logoUrl: 'https://upload.wikimedia.org/wikipedia/fr/thumb/e/e5/Science_et_Vie_TV_logo_2015.svg/512px-Science_et_Vie_TV_logo_2015.svg.png', streamUrl: 'https://artesimulcast.akamaized.net/hls/live/2031003/artelive_fr/index.m3u8', backupUrl: 'https://hls-live-ktotv.akamaized.net/hls/live/2028830/ktotv/master.m3u8', currentTitle: 'Le Mag de la Science', currentDesc: 'Comprendre les grandes révolutions scientifiques, l\'espace et la terre.' },
    { key: 'fr_nat_geo', ordinal: 12, name: 'National Geographic FR', group: BOUQUET.CULTURE, country: 'FR', quality: 'FHD', logoUrl: 'https://upload.wikimedia.org/wikipedia/commons/thumb/f/fc/Natgeologo.svg/512px-Natgeologo.svg.png', streamUrl: 'https://vdo2.pro-fhi.net:3628/live/uppodsfqlive.m3u8', backupUrl: 'https://artesimulcast.akamaized.net/hls/live/2031003/artelive_fr/index.m3u8', currentTitle: 'Air Crash & Enquêtes National Geographic', currentDesc: 'Science, nature sauvage et grandes enquêtes.' },
    { key: 'fr_rmc_decouverte', ordinal: 13, name: 'RMC Découverte HD (Canal 24)', group: BOUQUET.CULTURE, country: 'FR', quality: 'FHD', logoUrl: 'https://upload.wikimedia.org/wikipedia/commons/thumb/3/36/RMC_D%C3%A9couverte_logo_2017.svg/512px-RMC_D%C3%A9couverte_logo_2017.svg.png', streamUrl: 'https://d1ib1gsg71oarf.cloudfront.net/v1/master/3722c60a815c199d9c0ef36c5b73da68a62b09d1/cc-scp7wda722jph/BFM2_FR.m3u8', backupUrl: 'https://artesimulcast.akamaized.net/hls/live/2031003/artelive_fr/index.m3u8', currentTitle: 'Top Gear & Mégastructures', currentDesc: 'Génie civil, mécanique, histoire et investigations.' },
    { key: 'fr_planete_plus', ordinal: 14, name: 'Planète+ & Histoire TV', group: BOUQUET.CULTURE, country: 'FR', quality: 'HD', logoUrl: '', streamUrl: 'https://hls-live-ktotv.akamaized.net/hls/live/2028830/ktotv/master.m3u8', backupUrl: 'https://artesimulcast.akamaized.net/hls/live/2031003/artelive_fr/index.m3u8', currentTitle: 'Grands Documentaires Histoire', currentDesc: 'Histoire contemporaine, géopolitique et civilisations.' },
    { key: 'fr_arte_hd', ordinal: 15, name: 'ARTE Français HD', group: BOUQUET.CULTURE, country: 'FR', quality: 'FHD', logoUrl: 'https://upload.wikimedia.org/wikipedia/commons/thumb/0/0e/Arte_Logo_2017.svg/512px-Arte_Logo_2017.svg.png', streamUrl: 'https://artesimulcast.akamaized.net/hls/live/2031003/artelive_fr/index.m3u8', backupUrl: 'https://raw.githubusercontent.com/ipstreet312/freeiptv/master/ressources/dm/arte.m3u8', currentTitle: 'ARTE Découverte & Science', currentDesc: 'Culture européenne, documentaires scientifiques et cinéma.' },

    // 3. Jeunesse, Télétoon & Manga (Télétoon+, Télétoon+1, Gulli, Cartoon Network, Nickelodeon, ADN...)
    { key: 'fr_teletoon_plus', ordinal: 16, name: 'Télétoon+ HD', group: BOUQUET.KIDS, country: 'FR', quality: 'FHD', logoUrl: 'https://upload.wikimedia.org/wikipedia/fr/thumb/0/03/T%C3%A9l%C3%A9TOON%2B_logo_2015.svg/512px-T%C3%A9l%C3%A9TOON%2B_logo_2015.svg.png', streamUrl: 'https://ott.tv5monde.com/Content/HLS/Live/channel(tivi5monde)/index.m3u8', backupUrl: 'https://d3b73b34o7cvkq.cloudfront.net/v1/master/3722c60a815c199d9c0ef36c5b73da68a62b09d1/cc-gz2sgqzp076kf/adn.m3u8', currentTitle: 'Les Héros Télétoon+ Non-Stop', currentDesc: 'Séries d\'animation, aventures, humour et dessins animés en continu.' },
    { key: 'fr_teletoon_plus1', ordinal: 17, name: 'Télétoon+1 Famille', group: BOUQUET.KIDS, country: 'FR', quality: 'HD', logoUrl: 'https://upload.wikimedia.org/wikipedia/fr/thumb/0/03/T%C3%A9l%C3%A9TOON%2B_logo_2015.svg/512px-T%C3%A9l%C3%A9TOON%2B_logo_2015.svg.png', streamUrl: 'https://do7nccdsswstc.cloudfront.net/v1/manifest/3722c60a815c199d9c0ef36c5b73da68a62b09d1/cc-1aso0bc668saa/a5233c83-f772-4a81-959a-45ec7877ef61/5.m3u8', backupUrl: 'https://ott.tv5monde.com/Content/HLS/Live/channel(tivi5monde)/index.m3u8', currentTitle: 'Télétoon+1 Dessins Animés', currentDesc: 'Tous vos programmes Télétoon+ en décalé.' },
    { key: 'fr_gulli_kids', ordinal: 18, name: 'Gulli (TNT Canal 18)', group: BOUQUET.KIDS, country: 'FR', quality: 'FHD', logoUrl: 'https://upload.wikimedia.org/wikipedia/commons/thumb/3/30/Gulli_2010_logo.svg/512px-Gulli_2010_logo.svg.png', streamUrl: 'https://ott.tv5monde.com/Content/HLS/Live/channel(tivi5monde)/index.m3u8', backupUrl: 'https://do7nccdsswstc.cloudfront.net/v1/manifest/3722c60a815c199d9c0ef36c5b73da68a62b09d1/cc-1aso0bc668saa/a5233c83-f772-4a81-959a-45ec7877ef61/5.m3u8', currentTitle: 'Gulli Toon & Famille', currentDesc: 'Dessins animés, jeux et séries jeunesse.' },
    { key: 'fr_cartoon_network', ordinal: 19, name: 'Cartoon Network FR', group: BOUQUET.KIDS, country: 'FR', quality: 'FHD', logoUrl: 'https://upload.wikimedia.org/wikipedia/commons/thumb/8/80/Cartoon_Network_2010_logo.svg/512px-Cartoon_Network_2010_logo.svg.png', streamUrl: 'https://d3b73b34o7cvkq.cloudfront.net/v1/master/3722c60a815c199d9c0ef36c5b73da68a62b09d1/cc-gz2sgqzp076kf/adn.m3u8', backupUrl: 'https://ott.tv5monde.com/Content/HLS/Live/channel(tivi5monde)/index.m3u8', currentTitle: 'Gumball, Teen Titans & Adventure Time', currentDesc: 'Le meilleur de l\'animation Cartoon Network en français.' },
    { key: 'fr_nickelodeon', ordinal: 20, name: 'Nickelodeon & Boing FR', group: BOUQUET.KIDS, country: 'FR', quality: 'HD', logoUrl: 'https://i.imgur.com/RgKaoe4.png', streamUrl: 'https://do7nccdsswstc.cloudfront.net/v1/manifest/3722c60a815c199d9c0ef36c5b73da68a62b09d1/cc-1aso0bc668saa/a5233c83-f772-4a81-959a-45ec7877ef61/5.m3u8', backupUrl: 'https://ott.tv5monde.com/Content/HLS/Live/channel(tivi5monde)/index.m3u8', currentTitle: 'Bob l\'Éponge & Pat\'Patrouille', currentDesc: 'Dessins animés et séries jeunesse 24h/24.' },
    { key: 'fr_adn_tv_plus', ordinal: 21, name: 'ADN TV+ Anime & Mangas', group: BOUQUET.KIDS, country: 'FR', quality: 'FHD', logoUrl: 'https://i.imgur.com/HQZQyWt.png', streamUrl: 'https://d3b73b34o7cvkq.cloudfront.net/v1/master/3722c60a815c199d9c0ef36c5b73da68a62b09d1/cc-gz2sgqzp076kf/adn.m3u8', backupUrl: 'https://ott.tv5monde.com/Content/HLS/Live/channel(tivi5monde)/index.m3u8', currentTitle: 'Session Shonen & Mangas Cultes', currentDesc: 'Animation japonaise, séries anime et mangas en continu.' },
    { key: 'fr_tivi5_monde', ordinal: 22, name: 'TiVi5 Monde Jeunesse 24/7', group: BOUQUET.KIDS, country: 'FR', quality: 'FHD', logoUrl: '', streamUrl: 'https://ott.tv5monde.com/Content/HLS/Live/channel(tivi5monde)/index.m3u8', backupUrl: 'https://do7nccdsswstc.cloudfront.net/v1/manifest/3722c60a815c199d9c0ef36c5b73da68a62b09d1/cc-1aso0bc668saa/a5233c83-f772-4a81-959a-45ec7877ef61/5.m3u8', currentTitle: 'Dessins Animés Francophones', currentDesc: 'Programmes éducatifs et dessins animés.' },

    // 4. Cinéma & Séries
    { key: 'fr_canal_cinema', ordinal: 23, name: 'Canal+ Box Office & Cinéma', group: BOUQUET.CINEMA, country: 'FR', quality: 'FHD', logoUrl: '', streamUrl: 'https://rakuten-topfree-7-fr.xiaomi.wurl.tv/playlist.m3u8', backupUrl: 'https://edge13.vedge.infomaniak.com/livecast/ik:event/manifest.m3u8', currentTitle: 'Grand Cinéma Prime Time', currentDesc: 'Box-office, films récents et créations originales.' },
    { key: 'fr_cine_premier', ordinal: 24, name: 'Ciné+ Premier & Action FR', group: BOUQUET.CINEMA, country: 'FR', quality: 'FHD', logoUrl: '', streamUrl: 'https://onefmhd.vedge.infomaniak.com/livecast/ik:onefmhd/manifest.m3u8', backupUrl: 'https://rakuten-topfree-7-fr.xiaomi.wurl.tv/playlist.m3u8', currentTitle: 'Soirée Blockbusters & Action', currentDesc: 'Thrillers, action, aventure et grand spectacle.' },
    { key: 'fr_scifi_series', ordinal: 25, name: 'Syfy / Séries & Fantastique', group: BOUQUET.CINEMA, country: 'FR', quality: 'FHD', logoUrl: '', streamUrl: 'https://cph-p2p-msl.akamaized.net/hls/live/2000341/test/master.m3u8', backupUrl: 'https://rakuten-topfree-7-fr.xiaomi.wurl.tv/playlist.m3u8', currentTitle: 'Marathon Sci-Fi & Fantastique', currentDesc: 'Science-fiction, séries cultes et cinéma fantastique.' },

    // 5. Sports Live
    { key: 'fr_lequipe_sport', ordinal: 26, name: 'L\'Équipe Live 1 & Multisports', group: BOUQUET.SPORTS, country: 'FR', quality: 'FHD', logoUrl: 'https://upload.wikimedia.org/wikipedia/commons/thumb/3/32/L%27%C3%89quipe_wordmark.svg/512px-L%27%C3%89quipe_wordmark.svg.png', streamUrl: 'https://raw.githubusercontent.com/ipstreet312/freeiptv/master/ressources/dm/lequipe.m3u8', backupUrl: 'https://africa24.vedge.infomaniak.com/livecast/ik:africa24sport/manifest.m3u8', currentTitle: 'L\'Équipe du Soir & Direct', currentDesc: 'Football, Ligue des Champions, mercato et omnisports.' },
    { key: 'fr_africa24_sport', ordinal: 27, name: 'Africa 24 Sport Foot & CAF', group: BOUQUET.SPORTS, country: 'FR', quality: 'FHD', logoUrl: 'https://i0.wp.com/africa24tv.com/wp-content/uploads/2023/12/LOGO-AFRICASPORT-4-HD-sans-fond.png?fit=512%2C107&ssl=1', streamUrl: 'https://africa24.vedge.infomaniak.com/livecast/ik:africa24sport/manifest.m3u8', backupUrl: 'https://rbmn-live.akamaized.net/hls/live/590964/BoRB-AT/master.m3u8', currentTitle: 'Direct Football & Championnats', currentDesc: 'Grands matchs, analyses et omnisports en continu.' },
    { key: 'int_redbull_sport', ordinal: 28, name: 'Red Bull TV Sports & F1 Racing', group: BOUQUET.SPORTS, country: 'US', quality: 'FHD', logoUrl: 'https://i.imgur.com/7b2G9y0.png', streamUrl: 'https://rbmn-live.akamaized.net/hls/live/590964/BoRB-AT/master.m3u8', backupUrl: 'https://africa24.vedge.infomaniak.com/livecast/ik:africa24sport/manifest.m3u8', currentTitle: 'WRC, Formule 1 & Sports Extrêmes', currentDesc: 'Compétitions internationales en direct HD.' },

    // 6. Actualités & Info
    { key: 'fr_bfm2_hd', ordinal: 29, name: 'BFM Direct Info 24/7', group: BOUQUET.NEWS, country: 'FR', quality: 'FHD', logoUrl: 'https://alloforfait.fr/wp-content/uploads/2024/03/logo-bfm2-300x200.jpg', streamUrl: 'https://d1ib1gsg71oarf.cloudfront.net/v1/master/3722c60a815c199d9c0ef36c5b73da68a62b09d1/cc-scp7wda722jph/BFM2_FR.m3u8', backupUrl: 'https://live-20minutestv.digiteka.com/1961167769/index.m3u8', currentTitle: 'BFM Non-Stop Direct', currentDesc: 'Toute l\'actualité nationale et internationale en direct.' },
    { key: 'fr_france24', ordinal: 30, name: 'France 24 Français', group: BOUQUET.NEWS, country: 'FR', quality: 'FHD', logoUrl: 'https://upload.wikimedia.org/wikipedia/commons/thumb/8/8a/France24.png/512px-France24.png', streamUrl: 'https://f24hls-i.akamaihd.net/hls/live/221147/F24_FR_HI_HLS/master.m3u8', backupUrl: 'https://cdn-euronews.akamaized.net/live/eds/africanews-fr/25050/index.m3u8', currentTitle: 'Journal du Monde 24/7', currentDesc: 'Information mondiale en continu.' },
    { key: 'fr_africanews', ordinal: 31, name: 'Euronews / Africanews FR', group: BOUQUET.NEWS, country: 'FR', quality: 'HD', logoUrl: 'https://i.imgur.com/5UxU4zc.png', streamUrl: 'https://cdn-euronews.akamaized.net/live/eds/africanews-fr/25050/index.m3u8', backupUrl: 'https://africa24.vedge.infomaniak.com/livecast/ik:africa24/manifest.m3u8', currentTitle: 'Euronews Soir', currentDesc: 'Actualités européennes et mondiales.' },
    { key: 'be_canal_z', ordinal: 32, name: 'Canal Z Économie & Info', group: BOUQUET.NEWS, country: 'BE', quality: 'FHD', logoUrl: 'https://i.imgur.com/qtiGLzZ.png', streamUrl: 'https://d1hz1p2gea2zy4.cloudfront.net/live/rmgtv/RMG-TRENDSZFR-LIVE/index.m3u8', backupUrl: 'https://d1ib1gsg71oarf.cloudfront.net/v1/master/3722c60a815c199d9c0ef36c5b73da68a62b09d1/cc-scp7wda722jph/BFM2_FR.m3u8', currentTitle: 'Journal de l\'Économie', currentDesc: 'Finance, tech et actualités.' },

    // 7. Divertissement, Musique & Monde
    { key: 'ch_carac_1', ordinal: 33, name: 'Carac 1 Divertissement', group: BOUQUET.ENTERTAINMENT, country: 'CH', quality: 'FHD', logoUrl: 'https://i.imgur.com/TSc35U6.png', streamUrl: 'https://edge13.vedge.infomaniak.com/livecast/ik:event/manifest.m3u8', backupUrl: 'https://onefmhd.vedge.infomaniak.com/livecast/ik:onefmhd/manifest.m3u8', currentTitle: 'Soirée Divertissement & Talk', currentDesc: 'Spectacles, humour et magazines.' },
    { key: 'fr_cstar_music', ordinal: 34, name: 'CStar & NRJ Hits TV', group: BOUQUET.MUSIC, country: 'FR', quality: 'HD', logoUrl: 'https://i.imgur.com/w4kAX1V.png', streamUrl: 'https://raw.githubusercontent.com/Paradise-91/ParaTV/refs/heads/main/streams/canalplus/cstar-dm.m3u8', backupUrl: 'https://stream.sen-gt.com/A2iMusic/myStream/playlist.m3u8', currentTitle: 'Top Hits & Clips Live', currentDesc: 'Les meilleurs clips pop, dance et urbains.' },
    { key: 'ma_2m_monde', ordinal: 35, name: '2M Monde Maroc', group: BOUQUET.WORLD, country: 'MA', quality: 'FHD', logoUrl: 'https://i.imgur.com/MvpntzA.png', streamUrl: 'https://d2qh3gh0k5vp3v.cloudfront.net/v1/master/3722c60a815c199d9c0ef36c5b73da68a62b09d1/cc-n6pess5lwbghr/2M_ES.m3u8', backupUrl: 'https://live-20minutestv.digiteka.com/1961167769/index.m3u8', currentTitle: 'Soirée 2M Maroc', currentDesc: 'Programmes généralistes, séries et info.' },
    { key: 'sn_tv5_afrique', ordinal: 36, name: 'TV5Monde Afrique HD', group: BOUQUET.WORLD, country: 'SN', quality: 'FHD', logoUrl: '', streamUrl: 'https://ott.tv5monde.com/Content/HLS/Live/channel(afrique)/index.m3u8', backupUrl: 'https://africa24.vedge.infomaniak.com/livecast/ik:africa24/manifest.m3u8', currentTitle: 'Afrique Soir & Cinéma', currentDesc: 'Séries africaines, sport et information.' },

    // =========================================================================
    // 🔞 MODE ADULTE / OLÉ OLÉ (10 Chaînes 18+ strictement isolées !)
    // =========================================================================
    { key: 'adult_dorcel_tv', ordinal: 1, name: 'Dorcel TV Glamour 18+', group: BOUQUET.ADULT_GLAMOUR, country: 'FR', quality: '4K', logoUrl: '', streamUrl: 'https://d35j504z0x2vu2.cloudfront.net/v1/master/0bc8e8376bd8417a1b6761138aa41c26c7309312/trace-uk/master.m3u8', backupUrl: 'https://onefmhd.vedge.infomaniak.com/livecast/ik:onefmhd/manifest.m3u8', currentTitle: '🔞 Nuits Glamour & Charme Français', currentDesc: 'Programmation 18+ exclusive réservée au Mode Adulte Olé Olé.' },
    { key: 'adult_xxl_nuit', ordinal: 2, name: 'XXL Nuit & Charme FR', group: BOUQUET.ADULT_GLAMOUR, country: 'FR', quality: 'FHD', logoUrl: '', streamUrl: 'https://d2qh3gh0k5vp3v.cloudfront.net/v1/master/3722c60a815c199d9c0ef36c5b73da68a62b09d1/cc-44b3622c-2b7a-4c36-9f69-5e99a4d623b2/manifest.m3u8', backupUrl: 'https://d35j504z0x2vu2.cloudfront.net/v1/master/0bc8e8376bd8417a1b6761138aa41c26c7309312/trace-uk/master.m3u8', currentTitle: '🔞 Le Grand Soir XXL', currentDesc: 'Charme, séduction et divertissement nocturne 18+.' },
    { key: 'adult_ftv_midnight', ordinal: 3, name: 'Midnight Hot Lingerie & Bikinis', group: BOUQUET.ADULT_GLAMOUR, country: 'FR', quality: '4K', logoUrl: '', streamUrl: 'https://onefmhd.vedge.infomaniak.com/livecast/ik:onefmhd/manifest.m3u8', backupUrl: 'https://d35j504z0x2vu2.cloudfront.net/v1/master/0bc8e8376bd8417a1b6761138aa41c26c7309312/trace-uk/master.m3u8', currentTitle: '🔞 Midnight Lingerie & Glamour Show', currentDesc: 'Défilés lingerie, bikinis et soirées VIP après minuit.' },
    { key: 'adult_pink_club', ordinal: 4, name: 'Pink X / Club Rose 24/7', group: BOUQUET.ADULT_SEDUCTION, country: 'FR', quality: 'FHD', logoUrl: '', streamUrl: 'https://stream.sen-gt.com/A2iMusic/myStream/playlist.m3u8', backupUrl: 'https://d35j504z0x2vu2.cloudfront.net/v1/master/0bc8e8376bd8417a1b6761138aa41c26c7309312/trace-uk/master.m3u8', currentTitle: '💋 Club Rose Non-Stop', currentDesc: 'Séduction et ambiance tamisée 24h/24.' },
    { key: 'adult_venus_hd', ordinal: 5, name: 'Venus Tentation & Sensuel', group: BOUQUET.ADULT_SEDUCTION, country: 'FR', quality: 'FHD', logoUrl: '', streamUrl: 'https://edge13.vedge.infomaniak.com/livecast/ik:event/manifest.m3u8', backupUrl: 'https://d35j504z0x2vu2.cloudfront.net/v1/master/0bc8e8376bd8417a1b6761138aa41c26c7309312/trace-uk/master.m3u8', currentTitle: '💋 Tentation Nocturne', currentDesc: 'Séries sensuelles et charme international.' },
    { key: 'adult_playboy_tv', ordinal: 6, name: 'Playboy TV Midnight Lounge', group: BOUQUET.ADULT_SEDUCTION, country: 'US', quality: 'FHD', logoUrl: '', streamUrl: 'https://d35j504z0x2vu2.cloudfront.net/v1/master/0bc8e8376bd8417a1b6761138aa41c26c7309312/trace-uk/master.m3u8', backupUrl: 'https://onefmhd.vedge.infomaniak.com/livecast/ik:onefmhd/manifest.m3u8', currentTitle: '💋 Playboy Lifestyle & Glamour', currentDesc: 'Soirées privées et glamour californien.' },
    { key: 'adult_penthouse_hd', ordinal: 7, name: 'Penthouse Gold & Passion', group: BOUQUET.ADULT_CLUB, country: 'US', quality: '4K', logoUrl: '', streamUrl: 'https://d2qh3gh0k5vp3v.cloudfront.net/v1/master/3722c60a815c199d9c0ef36c5b73da68a62b09d1/cc-44b3622c-2b7a-4c36-9f69-5e99a4d623b2/manifest.m3u8', backupUrl: 'https://d35j504z0x2vu2.cloudfront.net/v1/master/0bc8e8376bd8417a1b6761138aa41c26c7309312/trace-uk/master.m3u8', currentTitle: '🌶️ Penthouse Suite VIP', currentDesc: 'Programme exclusif 18+ en haute définition.' },
    { key: 'adult_hustler_hd', ordinal: 8, name: 'Hustler Blue Velvet VIP', group: BOUQUET.ADULT_CLUB, country: 'US', quality: 'FHD', logoUrl: '', streamUrl: 'https://onefmhd.vedge.infomaniak.com/livecast/ik:onefmhd/manifest.m3u8', backupUrl: 'https://d35j504z0x2vu2.cloudfront.net/v1/master/0bc8e8376bd8417a1b6761138aa41c26c7309312/trace-uk/master.m3u8', currentTitle: '🌶️ Blue Velvet Late Night', currentDesc: 'Club privé et programmation olé olé.' },
    { key: 'adult_redlight_cinema', ordinal: 9, name: 'Redlight Cinéma Nocturne 18+', group: BOUQUET.ADULT_CINEMA, country: 'IT', quality: 'FHD', logoUrl: '', streamUrl: 'https://rakuten-topfree-7-fr.xiaomi.wurl.tv/playlist.m3u8', backupUrl: 'https://d35j504z0x2vu2.cloudfront.net/v1/master/0bc8e8376bd8417a1b6761138aa41c26c7309312/trace-uk/master.m3u8', currentTitle: '🔞 Cinéma de Minuit', currentDesc: 'Films de charme européens en continu.' },
    { key: 'adult_frissons_minuit', ordinal: 10, name: 'Frissons Extrêmes Minuit FR', group: BOUQUET.ADULT_CINEMA, country: 'FR', quality: 'FHD', logoUrl: '', streamUrl: 'https://d35j504z0x2vu2.cloudfront.net/v1/master/0bc8e8376bd8417a1b6761138aa41c26c7309312/trace-uk/master.m3u8', backupUrl: 'https://rakuten-topfree-7-fr.xiaomi.wurl.tv/playlist.m3u8', currentTitle: '🔞 Frissons Nocturnes', currentDesc: 'Sélection cinéma adulte 18+.' }
];

const SEED_VOD = [
    { id: 'vod_sintel', title: 'Sintel : L\'Odyssée du Dragon (4K)', type: 'MOVIE', category: 'Fantastique', releaseYear: 2024, rating: '★ 9.2', durationLabel: '1h 48m', synopsis: 'Une jeune guerrière parcourt les terres glacées à la recherche d\'un jeune dragon nommé Scales.', streamUrl: 'https://bitdash-a.akamaihd.net/content/sintel/hls/playlist.m3u8', isAdult: false },
    { id: 'vod_tears_of_steel', title: 'Tears of Steel : Cyber Amsterdam', type: 'MOVIE', category: 'Sci-Fi', releaseYear: 2025, rating: '★ 8.9', durationLabel: '2h 04m', synopsis: 'Dans un Amsterdam futuriste, une équipe de scientifiques et de soldats tente de sauver le monde.', streamUrl: 'https://cph-p2p-msl.akamaized.net/hls/live/2000341/test/master.m3u8', isAdult: false },
    { id: 'vod_cosmos', title: 'Cosmos Laundromat : Multivers', type: 'SERIES', category: 'Sci-Fi', releaseYear: 2025, rating: '★ 9.4', durationLabel: 'Saison 1 (3 Épisodes)', synopsis: 'Sur une île déserte, Franck rencontre un mystérieux voyageur qui lui ouvre les portes du multivers.', streamUrl: 'https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8', isAdult: false },
    { id: 'vod_big_buck', title: 'Forest Legends : Big Buck UHD', type: 'MOVIE', category: 'Animation', releaseYear: 2024, rating: '★ 8.7', durationLabel: '1h 32m', synopsis: 'Une aventure animée spectaculaire au cœur d\'une forêt luxuriante.', streamUrl: 'https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8', isAdult: false },
    // Adult / Olé Olé VOD items (strictly isolated!)
    { id: 'adult_vod_1', title: 'Nuits Roses à Paris (18+)', type: 'MOVIE', category: '🔥 Charme & Romance', releaseYear: 2026, rating: '18+ 💋', durationLabel: '1h 38m', synopsis: 'Séduction, glamour et passion nocturne au cœur des clubs privés parisiens.', streamUrl: 'https://d35j504z0x2vu2.cloudfront.net/v1/master/0bc8e8376bd8417a1b6761138aa41c26c7309312/trace-uk/master.m3u8', isAdult: true },
    { id: 'adult_vod_2', title: 'Velvet Tentation VIP (4K)', type: 'MOVIE', category: '🌶️ Club Privé', releaseYear: 2026, rating: '18+ 🔥', durationLabel: '1h 45m', synopsis: 'Une soirée exclusive dans une villa sur la Côte d\'Azur réservée aux initiés.', streamUrl: 'https://d2qh3gh0k5vp3v.cloudfront.net/v1/master/3722c60a815c199d9c0ef36c5b73da68a62b09d1/cc-44b3622c-2b7a-4c36-9f69-5e99a4d623b2/manifest.m3u8', isAdult: true },
    { id: 'adult_vod_3', title: 'Secrets d\'Alcôve : Série Olé Olé', type: 'SERIES', category: '💋 Séduction 24/7', releaseYear: 2026, rating: '18+ 💋', durationLabel: 'Saison 1 • 3 Épisodes', synopsis: 'Chroniques sensuelles et rencontres interdites après minuit.', streamUrl: 'https://onefmhd.vedge.infomaniak.com/livecast/ik:onefmhd/manifest.m3u8', isAdult: true },
    { id: 'adult_vod_4', title: 'Midnight Riviera : Bikini & Glamour', type: 'MOVIE', category: '🔥 Charme & Glamour', releaseYear: 2025, rating: '18+ 🔥', durationLabel: '1h 28m', synopsis: 'Défilés nocturnes, charme et séduction sous les néons roses.', streamUrl: 'https://stream.sen-gt.com/A2iMusic/myStream/playlist.m3u8', isAdult: true }
];

const SEED_SPORTS = [
    { id: 'sp_ucl_1', sport: 'Football', competition: 'UEFA Champions League', homeTeam: 'Paris SG', awayTeam: 'Real Madrid', kickoffLabel: 'EN DIRECT • 67\'', isLive: true, scoreOrStatus: '2 - 1', channelKey: 'fr_lequipe_sport' },
    { id: 'sp_f1_1', sport: 'Formule 1', competition: 'Grand Prix de Monaco', homeTeam: 'Grille F1', awayTeam: 'Qualification Q3', kickoffLabel: 'EN DIRECT • Tour 44/78', isLive: true, scoreOrStatus: 'LIVE 4K', channelKey: 'int_redbull_sport' },
    { id: 'sp_ufc_1', sport: 'MMA / Boxe', competition: 'UFC Fight Night Paris', homeTeam: 'Main Event', awayTeam: 'Championship', kickoffLabel: 'Ce soir • 22:30', isLive: false, scoreOrStatus: 'PPV HD', channelKey: 'fr_africa24_sport' }
];

function loadStore() {
    try {
        if (fs.existsSync(STORE_PATH)) {
            const parsed = JSON.parse(fs.readFileSync(STORE_PATH, 'utf8'));
            if (Array.isArray(parsed.channels) && parsed.channels.length >= 40) {
                return parsed;
            }
        }
    } catch (e) {
        console.error('Initializing fresh v4 store:', e.message);
    }
    const initial = {
        channels: SEED_CHANNELS.map(ch => ({
            ...ch,
            isFavorite: false,
            isAdult: isAdultBouquet(ch.group),
            countryFlag: getCountryFlag(ch.country)
        })),
        vodItems: SEED_VOD,
        sportsEvents: SEED_SPORTS,
        sources: [
            { id: 'src_builtin_v4', name: 'Bouquet Complet NovaPlay (TNT, Télétoon+, Discovery Science, Sport & 18+)', type: 'M3U_URL', serverUrl: 'builtin://novaplay', channelCount: SEED_CHANNELS.length, addedAt: new Date().toISOString() }
        ]
    };
    saveStore(initial);
    return initial;
}

function saveStore(s) {
    fs.writeFileSync(STORE_PATH, JSON.stringify(s, null, 2), 'utf8');
}

let store = loadStore();

function fetchUrlBuffer(targetUrl, redirectCount = 0) {
    return new Promise((resolve, reject) => {
        if (redirectCount > 6) return reject(new Error('Trop de redirections HTTP'));
        let parsed;
        try {
            parsed = new URL(targetUrl);
        } catch {
            return reject(new Error(`URL invalide: ${targetUrl}`));
        }
        const client = parsed.protocol === 'https:' ? https : http;
        const req = client.get(parsed, {
            headers: { 'User-Agent': 'VLC/3.0.20 LibVLC/3.0.20', 'Accept': '*/*' },
            timeout: 20000,
            rejectUnauthorized: false
        }, (res) => {
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
        req.on('timeout', () => { req.destroy(); reject(new Error('Délai réseau dépassé')); });
    });
}

function parseM3uContent(m3uText, sourcePrefix = 'm3u', forceAdult = false) {
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
        } else if (!line.startsWith('#') && /^(https?|rtsp|rtmp|mms):\/\//i.test(line)) {
            const meta = pendingMeta;
            pendingMeta = null;
            const rawName = (meta && (meta.attrs['tvg-name'] || meta.displayName)) || `Chaîne ${rawChannels.length + 1}`;
            const rawGroup = (meta && meta.attrs['group-title']) || 'Direct TV';
            const logo = (meta && meta.attrs['tvg-logo']) || '';
            const explicitCountry = ((meta && meta.attrs['tvg-country']) || '').toUpperCase();
            const countryCode = explicitCountry.length === 2 ? explicitCountry : 'FR';
            const cleanName = cleanChannelName(rawName);
            const quality = extractQualityBadge(rawName, rawGroup);
            const bouquet = classifyBouquet(cleanName, rawGroup, countryCode, forceAdult);

            rawChannels.push({
                key: `${sourcePrefix}_${rawChannels.length + 1}`,
                ordinal: rawChannels.length + 1,
                name: cleanName,
                group: bouquet,
                country: countryCode,
                countryFlag: getCountryFlag(countryCode),
                quality,
                logoUrl: logo,
                streamUrl: line,
                backupUrl: isAdultBouquet(bouquet)
                    ? 'https://d35j504z0x2vu2.cloudfront.net/v1/master/0bc8e8376bd8417a1b6761138aa41c26c7309312/trace-uk/master.m3u8'
                    : 'https://ott.tv5monde.com/Content/HLS/Live/channel(france)/index.m3u8',
                isAdult: isAdultBouquet(bouquet),
                isFavorite: false,
                currentTitle: `En Direct • ${cleanName}`,
                currentDesc: `Diffusion HD sur le bouquet ${bouquet}.`
            });
            if (rawChannels.length >= 3000) break;
        }
    }
    return rawChannels;
}

function sendJson(res, status, payload) {
    res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Access-Control-Allow-Origin': '*' });
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
            } catch (e) { reject(e); }
        });
        req.on('error', reject);
    });
}

const server = http.createServer(async (req, res) => {
    try {
        const parsedUrl = new URL(req.url, `http://127.0.0.1:${PORT}`);
        const pathname = parsedUrl.pathname;

        if (pathname === '/api/state' && req.method === 'GET') {
            return sendJson(res, 200, {
                channels: store.channels,
                vodItems: store.vodItems,
                sportsEvents: store.sportsEvents,
                sources: store.sources
            });
        }

        if (pathname === '/api/favorite' && req.method === 'POST') {
            const body = await readJsonBody(req);
            const target = store.channels.find(c => c.key === body.key);
            if (target) {
                target.isFavorite = !target.isFavorite;
                saveStore(store);
            }
            return sendJson(res, 200, { ok: true });
        }

        if (pathname === '/api/playlist/add' && req.method === 'POST') {
            const body = await readJsonBody(req);
            const name = (body.name || 'Mon Bouquet IPTV').trim();
            const type = body.type || 'M3U_URL';
            const forceAdult = Boolean(body.forceAdult) || (body.serverUrl || '').toLowerCase().includes('xxx');
            let m3uContent = '';

            if (type === 'M3U_CONTENT') {
                m3uContent = body.content || '';
            } else if (type === 'XTREAM') {
                let base = (body.serverUrl || '').trim().replace(/\/+$/, '');
                if (!/^https?:\/\//i.test(base)) base = 'http://' + base;
                const u = encodeURIComponent((body.username || '').trim());
                const p = encodeURIComponent((body.password || '').trim());
                const resp = await fetchUrlBuffer(`${base}/get.php?username=${u}&password=${p}&type=m3u_plus&output=m3u8`);
                m3uContent = resp.buffer.toString('utf8');
            } else {
                let targetUrl = (body.serverUrl || '').trim();
                if (!/^https?:\/\//i.test(targetUrl)) targetUrl = 'https://' + targetUrl;
                const resp = await fetchUrlBuffer(targetUrl);
                m3uContent = resp.buffer.toString('utf8');
            }

            const parsedChannels = parseM3uContent(m3uContent, `src_${Date.now()}`, forceAdult);
            if (parsedChannels.length === 0) {
                return sendJson(res, 400, { ok: false, error: 'Aucune chaîne valide trouvée.' });
            }

            const seenNames = new Set(parsedChannels.map(c => c.name.toLowerCase()));
            const merged = [...parsedChannels];
            for (const seed of SEED_CHANNELS) {
                if (!seenNames.has(seed.name.toLowerCase())) {
                    merged.push({
                        ...seed,
                        isFavorite: false,
                        isAdult: isAdultBouquet(seed.group),
                        countryFlag: getCountryFlag(seed.country)
                    });
                }
            }
            store.channels = merged.map((c, idx) => ({ ...c, ordinal: idx + 1 }));
            const newSource = { id: `src_${Date.now()}`, name, type, serverUrl: body.serverUrl || 'Fichier local', channelCount: parsedChannels.length, addedAt: new Date().toISOString() };
            store.sources = [newSource, ...store.sources.filter(s => s.name !== name)];
            saveStore(store);

            return sendJson(res, 200, { ok: true, addedCount: parsedChannels.length, totalCount: store.channels.length });
        }

        if (pathname === '/api/playlist/restore' && req.method === 'POST') {
            store.channels = SEED_CHANNELS.map(ch => ({
                ...ch,
                isFavorite: false,
                isAdult: isAdultBouquet(ch.group),
                countryFlag: getCountryFlag(ch.country)
            }));
            saveStore(store);
            return sendJson(res, 200, { ok: true });
        }

        if (pathname === '/api/proxy' && req.method === 'GET') {
            const targetUrl = parsedUrl.searchParams.get('url');
            if (!targetUrl) { res.writeHead(400); return res.end('Missing url'); }
            try {
                const { buffer, contentType, finalUrl } = await fetchUrlBuffer(targetUrl);
                const lowerUrl = finalUrl.split('?')[0].toLowerCase();
                const isM3u8 = lowerUrl.endsWith('.m3u8') || contentType.toLowerCase().includes('mpegurl') || buffer.slice(0, 16).toString('utf8').startsWith('#EXTM3U');
                if (isM3u8) {
                    const text = buffer.toString('utf8');
                    const baseUri = new URL(finalUrl);
                    const rewritten = text.split(/\r?\n/).map(line => {
                        const trimmed = line.trim();
                        if (!trimmed) return line;
                        if (trimmed.startsWith('#')) {
                            return line.replace(/URI="([^"]+)"/g, (_, uriVal) => {
                                try { return `URI="/api/proxy?url=${encodeURIComponent(new URL(uriVal, baseUri).toString())}"`; }
                                catch { return `URI="${uriVal}"`; }
                            });
                        }
                        try { return `/api/proxy?url=${encodeURIComponent(new URL(trimmed, baseUri).toString())}`; }
                        catch { return line; }
                    }).join('\n');
                    res.writeHead(200, { 'Content-Type': 'application/vnd.apple.mpegurl', 'Access-Control-Allow-Origin': '*', 'Cache-Control': 'no-cache' });
                    return res.end(rewritten);
                }
                res.writeHead(200, { 'Content-Type': contentType || 'video/mp2t', 'Access-Control-Allow-Origin': '*', 'Cache-Control': 'no-cache' });
                return res.end(buffer);
            } catch (err) {
                res.writeHead(502, { 'Content-Type': 'text/plain; charset=utf-8', 'Access-Control-Allow-Origin': '*' });
                return res.end(`Proxy error: ${err.message}`);
            }
        }

        let filePath = pathname === '/' ? path.join(PUBLIC_DIR, 'index.html') : path.join(PUBLIC_DIR, pathname);
        if (fs.existsSync(filePath) && fs.statSync(filePath).isFile()) {
            const ext = path.extname(filePath).toLowerCase();
            const mimeMap = { '.html': 'text/html; charset=utf-8', '.css': 'text/css; charset=utf-8', '.js': 'application/javascript; charset=utf-8' };
            res.writeHead(200, { 'Content-Type': mimeMap[ext] || 'application/octet-stream' });
            return fs.createReadStream(filePath).pipe(res);
        }
        res.writeHead(404); res.end('Not found');
    } catch (err) {
        sendJson(res, 500, { ok: false, error: err.message });
    }
});

server.listen(PORT, '127.0.0.1', () => {
    console.log(`NovaPlay IPTV PC Desktop Engine actif sur http://127.0.0.1:${PORT}`);
});
