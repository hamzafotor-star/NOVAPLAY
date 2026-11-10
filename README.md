# 📺 NovaPlay IPTV (Android / Google TV / Smartphone & Tablette)

Application IPTV moderne et ultra-réactive développée en **Kotlin & Jetpack Compose**, combinant un double moteur vidéo hybride **AndroidX Media3 (ExoPlayer)** + **VideoLAN LibVLC** avec bascule automatique.

---

## ✨ Fonctionnalités Principales

### 🖥️ Interface Adaptative Multi-Écrans
- **Smartphone Portrait** : Lecteur `16:9` épinglé en haut, barre EPG/Zapping compacte sur une ligne, barre de recherche repliable et liste des chaînes maximisée.
- **Smartphone Paysage** : Affichage automatique en **2 colonnes côte à côte** (Lecteur + Zapping à gauche, Bouquets + Chaînes à droite).
- **Tablette & Android TV (Leanback)** : Navigation latérale compatible télécommande D-Pad (`CH+` / `CH-`, haut/bas en plein écran) et guide EPG détaillé.
- **Vrai Plein Écran Immersif & Picture-in-Picture (PiP)** : Masquage des barres système Android, rotation paysage automatique, et **tiroir latéral des chaînes en plein écran**.
- **Formats d'Écran Vidéo** : Bascule instantanée entre `16:9 (Ajusté)`, `Zoom Plein Écran (20:9 sans bords noirs)`, `Étirer 100%` et `4:3`.

### 🗂️ Organisation Intelligente des Chaînes & Bouquets
- **Nettoyage & Classification Automatique M3U** : Suppression des balises parasites, détection des badges de résolution (`4K`, `FHD`, `HD`, `SD`) et affichage des drapeaux par pays (`🇫🇷`, `🇧🇪`, `🇨🇭`, `🇲🇦`, `🇩🇿`, `🇹🇳`, `🇸🇳`, `🇨🇦`...).
- **Bouquets Thématiques avec Compteurs** :
  - `🇫🇷 TNT & Généraliste`
  - `📰 Actualités & Info`
  - `⚽ Sports Live`
  - `🎬 Cinéma & Séries`
  - `🎭 Divertissement`
  - `🧒 Jeunesse & Manga`
  - `🎵 Musique & Clips`
  - `🌍 Monde & Afrique`
  - `📚 Culture & Découverte`
- **Tri & Modes d'Affichage** : Tri par Bouquet, par Numéro, Alphabétique (`A–Z`) ou Qualité (`4K/FHD`), et bascule **Vue Liste / Vue Grille**.

### ⚡ Moteur Vidéo Hybride & Connectivité IPTV
- **Double Décodeur Media3 + LibVLC** : Zapping rapide (`800 ms`) et **Watchdog automatique (7,5 s)** qui bascule vers le flux de secours ou LibVLC en cas de blocage réseau.
- **Compatibilité M3U / M3U8 & Xtream Codes API** : Supporte les redirections HTTP ↔ HTTPS, les certificats auto-signés des panneaux IPTV, et l'import en 1 clic.

---

## 🛠️ Compilation de l'APK

```bash
./gradlew assembleDebug
```

L'APK compilé est généré dans `app/build/outputs/apk/debug/app-debug.apk`.
