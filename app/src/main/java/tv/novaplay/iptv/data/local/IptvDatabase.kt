package tv.novaplay.iptv.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import tv.novaplay.iptv.data.model.ChannelSortMode
import tv.novaplay.iptv.data.model.LiveChannel
import tv.novaplay.iptv.data.model.PlaylistSource
import tv.novaplay.iptv.data.model.SourceType
import tv.novaplay.iptv.data.model.WatchProgress
import tv.novaplay.iptv.data.parser.M3uParser

/**
 * Native SQLite database with structured bouquet ordering, quality badges, multi-mode sorting,
 * and strict isolation between Normal Mode and "Mode Adulte / Olé Olé".
 */
class IptvDatabase(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS catalog (
                id INTEGER PRIMARY KEY CHECK(id=1),
                generation INTEGER,
                source TEXT,
                saved INTEGER,
                count INTEGER
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS channels (
                generation INTEGER,
                ordinal INTEGER,
                name TEXT,
                grp TEXT,
                key TEXT,
                url TEXT,
                backup_url TEXT,
                logo TEXT,
                country TEXT,
                quality TEXT DEFAULT 'HD',
                ppv INTEGER,
                priority INTEGER DEFAULT 0,
                search TEXT
            )
            """.trimIndent()
        )

        db.execSQL("CREATE TABLE IF NOT EXISTS hidden_channels (key TEXT PRIMARY KEY)")
        db.execSQL("CREATE TABLE IF NOT EXISTS hidden_categories (category TEXT PRIMARY KEY)")

        db.execSQL("CREATE INDEX IF NOT EXISTS channel_key ON channels(generation, key)")
        db.execSQL("CREATE INDEX IF NOT EXISTS channel_ordinal ON channels(generation, ordinal)")
        db.execSQL("CREATE INDEX IF NOT EXISTS channel_priority ON channels(generation, priority, ordinal)")
        db.execSQL("CREATE INDEX IF NOT EXISTS country_priority ON channels(generation, country, priority, ordinal)")
        db.execSQL("CREATE INDEX IF NOT EXISTS category_priority ON channels(generation, country, grp, priority, ordinal)")

        db.execSQL("CREATE TABLE IF NOT EXISTS favorites (item_key TEXT PRIMARY KEY, kind TEXT NOT NULL, added_at INTEGER NOT NULL)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS watch_progress (
                progress_key TEXT PRIMARY KEY,
                content_id TEXT NOT NULL,
                episode_id TEXT,
                title TEXT NOT NULL,
                subtitle TEXT NOT NULL,
                position_ms INTEGER NOT NULL,
                duration_ms INTEGER NOT NULL,
                updated_at_ms INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS playlist_sources (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                type TEXT NOT NULL,
                server_url TEXT NOT NULL,
                username TEXT NOT NULL,
                password TEXT NOT NULL,
                epg_url TEXT NOT NULL,
                last_sync_ms INTEGER NOT NULL,
                channel_count INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 4) {
            db.execSQL("DROP TABLE IF EXISTS channels")
            db.execSQL("DROP TABLE IF EXISTS catalog")
            db.execSQL("DELETE FROM playlist_sources WHERE id LIKE 'builtin%'")
        }
        onCreate(db)
    }

    fun replaceCatalogChannels(sourceId: String, channels: List<LiveChannel>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val nextGen = getCurrentGeneration(db) + 1
            for (channel in channels) {
                val searchNormalized = "${channel.name} ${channel.group} ${channel.country} ${channel.qualityLabel}".lowercase()
                val values = ContentValues().apply {
                    put("generation", nextGen)
                    put("ordinal", channel.ordinal)
                    put("name", channel.name)
                    put("grp", channel.group)
                    put("key", channel.key)
                    put("url", channel.streamUrl)
                    put("backup_url", channel.backupStreamUrl ?: "")
                    put("logo", channel.logoUrl)
                    put("country", channel.country)
                    put("quality", channel.qualityLabel)
                    put("ppv", if (channel.isPpv) 1 else 0)
                    put("priority", channel.priority)
                    put("search", searchNormalized)
                }
                db.insert("channels", null, values)
            }

            val catalogValues = ContentValues().apply {
                put("id", 1)
                put("generation", nextGen)
                put("source", sourceId)
                put("saved", System.currentTimeMillis())
                put("count", channels.size)
            }
            db.insertWithOnConflict("catalog", null, catalogValues, SQLiteDatabase.CONFLICT_REPLACE)

            db.execSQL("DELETE FROM channels WHERE generation != ?", arrayOf(nextGen))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun queryVisibleChannels(
        countryFilter: String? = null,
        categoryFilter: String? = null,
        searchQuery: String = "",
        sortMode: ChannelSortMode = ChannelSortMode.BY_GROUP,
        adultMode: Boolean = false
    ): List<LiveChannel> {
        val db = readableDatabase
        val gen = getCurrentGeneration(db)
        if (gen <= 0) return emptyList()

        val favorites = getFavoriteKeys("channel")
        val args = mutableListOf(gen.toString())
        val whereClause = StringBuilder(
            """
            generation = ?
            AND NOT EXISTS (SELECT 1 FROM hidden_channels h WHERE h.key = channels.key)
            AND NOT EXISTS (SELECT 1 FROM hidden_categories g WHERE g.category = channels.grp)
            """.trimIndent()
        )

        if (!countryFilter.isNullOrBlank() && countryFilter != "TOUS") {
            whereClause.append(" AND country = ?")
            args.add(countryFilter)
        }
        val onlyFavorites = categoryFilter == "⭐ Favoris"
        if (!categoryFilter.isNullOrBlank() && categoryFilter != "Toutes" && !onlyFavorites) {
            whereClause.append(" AND grp = ?")
            args.add(categoryFilter)
        }
        if (searchQuery.isNotBlank()) {
            whereClause.append(" AND instr(search, ?) > 0")
            args.add(searchQuery.trim().lowercase())
        }

        val orderBy = when (sortMode) {
            ChannelSortMode.BY_NUMBER -> "ordinal ASC"
            ChannelSortMode.BY_NAME -> "name COLLATE NOCASE ASC"
            ChannelSortMode.BY_QUALITY -> "priority DESC, ordinal ASC"
            ChannelSortMode.BY_GROUP -> "ordinal ASC"
        }

        val sql = """
            SELECT key, generation, ordinal, name, grp, url, backup_url, logo, country, quality, ppv, priority
            FROM channels
            WHERE $whereClause
            ORDER BY $orderBy
            LIMIT 4000
        """.trimIndent()

        val result = mutableListOf<LiveChannel>()
        db.rawQuery(sql, args.toTypedArray()).use { cursor ->
            while (cursor.moveToNext()) {
                val grp = cursor.getString(4) ?: ""
                val isAdultChannel = M3uParser.isAdultBouquet(grp)
                // Strict isolation: Adult Mode shows ONLY adult channels; Normal Mode shows ONLY normal channels
                if (adultMode != isAdultChannel) continue

                val key = cursor.getString(0)
                val isFav = favorites.contains(key)
                if (onlyFavorites && !isFav) continue
                result.add(
                    LiveChannel(
                        key = key,
                        generation = cursor.getInt(1),
                        ordinal = cursor.getInt(2),
                        name = cursor.getString(3),
                        group = grp,
                        streamUrl = cursor.getString(5),
                        backupStreamUrl = cursor.getString(6)?.takeIf { it.isNotBlank() },
                        logoUrl = cursor.getString(7) ?: "",
                        country = cursor.getString(8) ?: "FR",
                        qualityLabel = cursor.getString(9) ?: "HD",
                        isPpv = cursor.getInt(10) == 1,
                        priority = cursor.getInt(11),
                        isFavorite = isFav
                    )
                )
            }
        }

        return if (sortMode == ChannelSortMode.BY_GROUP) {
            result.sortedWith(
                compareBy<LiveChannel> { M3uParser.bouquetOrderIndex(it.group) }
                    .thenBy { it.ordinal }
            )
        } else {
            result
        }
    }

    fun queryCategoryCounts(adultMode: Boolean = false): Map<String, Int> {
        val db = readableDatabase
        val gen = getCurrentGeneration(db)
        if (gen <= 0) return emptyMap()
        val counts = mutableMapOf<String, Int>()
        var total = 0
        db.rawQuery(
            """
            SELECT grp, COUNT(*) FROM channels
            WHERE generation = ?
            AND NOT EXISTS (SELECT 1 FROM hidden_categories g WHERE g.category = channels.grp)
            GROUP BY grp
            """.trimIndent(),
            arrayOf(gen.toString())
        ).use { c ->
            while (c.moveToNext()) {
                val grp = c.getString(0) ?: continue
                if (M3uParser.isAdultBouquet(grp) != adultMode) continue
                val cnt = c.getInt(1)
                counts[grp] = cnt
                total += cnt
            }
        }
        counts["Toutes"] = total
        val favKeys = getFavoriteKeys("channel")
        val visibleFavCount = queryVisibleChannels(categoryFilter = "⭐ Favoris", adultMode = adultMode).size
        counts["⭐ Favoris"] = visibleFavCount.coerceAtMost(favKeys.size)
        return counts
    }

    fun queryAvailableCountries(adultMode: Boolean = false): List<String> {
        val db = readableDatabase
        val gen = getCurrentGeneration(db)
        if (gen <= 0) return listOf("TOUS", "FR")
        val list = mutableListOf("TOUS")
        db.rawQuery(
            "SELECT country, grp, COUNT(*) as cnt FROM channels WHERE generation = ? AND country IS NOT NULL AND country != '' GROUP BY country, grp ORDER BY cnt DESC",
            arrayOf(gen.toString())
        ).use { c ->
            while (c.moveToNext()) {
                val code = c.getString(0)
                val grp = c.getString(1) ?: ""
                if (M3uParser.isAdultBouquet(grp) != adultMode) continue
                if (!code.isNullOrBlank() && !list.contains(code) && list.size < 12) {
                    list.add(code)
                }
            }
        }
        return list
    }

    fun toggleFavorite(itemKey: String, kind: String): Boolean {
        val db = writableDatabase
        val exists = getFavoriteKeys(kind).contains(itemKey)
        if (exists) {
            db.delete("favorites", "item_key = ?", arrayOf(itemKey))
            return false
        } else {
            val values = ContentValues().apply {
                put("item_key", itemKey)
                put("kind", kind)
                put("added_at", System.currentTimeMillis())
            }
            db.insertWithOnConflict("favorites", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            return true
        }
    }

    fun getFavoriteKeys(kind: String): Set<String> {
        val db = readableDatabase
        val keys = mutableSetOf<String>()
        db.rawQuery("SELECT item_key FROM favorites WHERE kind = ?", arrayOf(kind)).use { c ->
            while (c.moveToNext()) {
                keys.add(c.getString(0))
            }
        }
        return keys
    }

    fun setCategoryHidden(category: String, hidden: Boolean) {
        val db = writableDatabase
        if (hidden) {
            val values = ContentValues().apply { put("category", category) }
            db.insertWithOnConflict("hidden_categories", null, values, SQLiteDatabase.CONFLICT_IGNORE)
        } else {
            db.delete("hidden_categories", "category = ?", arrayOf(category))
        }
    }

    fun getHiddenCategories(): Set<String> {
        val db = readableDatabase
        val result = mutableSetOf<String>()
        db.rawQuery("SELECT category FROM hidden_categories", null).use { c ->
            while (c.moveToNext()) {
                result.add(c.getString(0))
            }
        }
        return result
    }

    fun saveWatchProgress(progress: WatchProgress) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("progress_key", progress.progressKey)
            put("content_id", progress.contentId)
            put("episode_id", progress.episodeId)
            put("title", progress.title)
            put("subtitle", progress.subtitle)
            put("position_ms", progress.positionMs)
            put("duration_ms", progress.durationMs)
            put("updated_at_ms", progress.updatedAtMs)
        }
        db.insertWithOnConflict("watch_progress", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getWatchProgressList(): List<WatchProgress> {
        val db = readableDatabase
        val list = mutableListOf<WatchProgress>()
        db.rawQuery(
            "SELECT progress_key, content_id, episode_id, title, subtitle, position_ms, duration_ms, updated_at_ms FROM watch_progress ORDER BY updated_at_ms DESC LIMIT 20",
            null
        ).use { c ->
            while (c.moveToNext()) {
                list.add(
                    WatchProgress(
                        progressKey = c.getString(0),
                        contentId = c.getString(1),
                        episodeId = c.getString(2),
                        title = c.getString(3),
                        subtitle = c.getString(4),
                        positionMs = c.getLong(5),
                        durationMs = c.getLong(6),
                        updatedAtMs = c.getLong(7)
                    )
                )
            }
        }
        return list
    }

    fun savePlaylistSource(source: PlaylistSource) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("id", source.id)
            put("name", source.name)
            put("type", source.type.name)
            put("server_url", source.serverUrl)
            put("username", source.username)
            put("password", source.password)
            put("epg_url", source.epgUrl)
            put("last_sync_ms", source.lastSyncTimeMs)
            put("channel_count", source.channelCount)
        }
        db.insertWithOnConflict("playlist_sources", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun deletePlaylistSource(sourceId: String) {
        val db = writableDatabase
        db.delete("playlist_sources", "id = ?", arrayOf(sourceId))
    }

    fun getPlaylistSources(): List<PlaylistSource> {
        val db = readableDatabase
        val list = mutableListOf<PlaylistSource>()
        db.rawQuery(
            "SELECT id, name, type, server_url, username, password, epg_url, last_sync_ms, channel_count FROM playlist_sources ORDER BY last_sync_ms DESC",
            null
        ).use { c ->
            while (c.moveToNext()) {
                list.add(
                    PlaylistSource(
                        id = c.getString(0),
                        name = c.getString(1),
                        type = SourceType.valueOf(c.getString(2)),
                        serverUrl = c.getString(3),
                        username = c.getString(4),
                        password = c.getString(5),
                        epgUrl = c.getString(6),
                        lastSyncTimeMs = c.getLong(7),
                        channelCount = c.getInt(8)
                    )
                )
            }
        }
        return list
    }

    private fun getCurrentGeneration(db: SQLiteDatabase): Int {
        db.rawQuery("SELECT generation FROM catalog WHERE id = 1", null).use { c ->
            return if (c.moveToFirst()) c.getInt(0) else 0
        }
    }

    companion object {
        private const val DB_NAME = "novaplay_iptv.db"
        private const val DB_VERSION = 4
    }
}
