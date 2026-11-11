package tv.novaplay.iptv.data.network

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import tv.novaplay.iptv.data.model.LiveChannel
import tv.novaplay.iptv.data.parser.M3uParser
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Xtream Codes API (`player_api.php` / `get.php`) & remote M3U downloader.
 * Uses an IPTV-optimized OkHttpClient supporting self-signed SSL certificates and cross-protocol redirects.
 */
class XtreamCodesClient {

    val httpClient: OkHttpClient = buildIptvHttpClient()

    suspend fun fetchM3uChannels(m3uInput: String, forceAdult: Boolean = false): List<LiveChannel> = withContext(Dispatchers.IO) {
        val trimmed = m3uInput.trim()
        val isAdultUrl = forceAdult || trimmed.contains("/xxx", true) || trimmed.contains("adult", true)

        // If user pasted raw #EXTM3U content directly into the field
        if (trimmed.startsWith("#EXTM3U", ignoreCase = true) || trimmed.contains("#EXTINF:", ignoreCase = true)) {
            return@withContext M3uParser.parse(trimmed, forceAdultBouquet = isAdultUrl)
        }

        val normalizedUrl = if (!trimmed.startsWith("http://", true) && !trimmed.startsWith("https://", true)) {
            "https://$trimmed"
        } else {
            trimmed
        }

        val request = Request.Builder()
            .url(normalizedUrl)
            .header("User-Agent", "VLC/3.0.20 LibVLC/3.0.20")
            .header("Accept", "*/*")
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Le serveur IPTV a répondu HTTP ${response.code}")
            }
            val body = response.body?.string().orEmpty()
            M3uParser.parse(body, forceAdultBouquet = isAdultUrl)
        }
    }

    suspend fun fetchXtreamLiveChannels(
        serverUrl: String,
        username: String,
        password: String
    ): List<LiveChannel> = withContext(Dispatchers.IO) {
        val rawUrl = serverUrl.trim()

        if (rawUrl.contains("get.php", ignoreCase = true) || rawUrl.endsWith(".m3u", true) || rawUrl.endsWith(".m3u8", true)) {
            return@withContext fetchM3uChannels(rawUrl)
        }

        val baseUrl = rawUrl.trimEnd('/').let {
            if (!it.startsWith("http://", true) && !it.startsWith("https://", true)) "http://$it" else it
        }
        val u = Uri.encode(username.trim())
        val p = Uri.encode(password.trim())

        val categoriesUrl = "$baseUrl/player_api.php?username=$u&password=$p&action=get_live_categories"
        val streamsUrl = "$baseUrl/player_api.php?username=$u&password=$p&action=get_live_streams"

        val categoryMap = mutableMapOf<String, String>()
        runCatching {
            val catReq = Request.Builder()
                .url(categoriesUrl)
                .header("User-Agent", "VLC/3.0.20 LibVLC/3.0.20")
                .build()
            httpClient.newCall(catReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val arr = JSONArray(resp.body?.string().orEmpty())
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        categoryMap[obj.optString("category_id")] = obj.optString("category_name", "Live TV")
                    }
                }
            }
        }

        val streamReq = Request.Builder()
            .url(streamsUrl)
            .header("User-Agent", "VLC/3.0.20 LibVLC/3.0.20")
            .build()

        val channels = runCatching {
            httpClient.newCall(streamReq).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IllegalStateException("Serveur Xtream HTTP ${resp.code}")
                }
                val arr = JSONArray(resp.body?.string().orEmpty())
                val list = mutableListOf<LiveChannel>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val streamId = obj.optInt("stream_id", i + 1)
                    val rawName = obj.optString("name", "Chaîne #$streamId")
                    val cleanName = M3uParser.cleanChannelName(rawName).ifEmpty { rawName }
                    val categoryId = obj.optString("category_id")
                    val rawGroup = categoryMap[categoryId] ?: "Live TV"
                    val country = when {
                        rawName.contains("UK", true) || rawGroup.contains("UK", true) -> "UK"
                        rawName.contains("US", true) || rawGroup.contains("US", true) -> "US"
                        rawName.contains("BE", true) || rawGroup.contains("BELG", true) -> "BE"
                        rawName.contains("CH", true) || rawGroup.contains("SUISSE", true) -> "CH"
                        rawName.contains("MA", true) || rawGroup.contains("MAROC", true) -> "MA"
                        else -> "FR"
                    }
                    val classifiedGroup = M3uParser.classifyBouquet(rawGroup, cleanName, country)
                    val quality = M3uParser.extractQualityBadge(rawName, rawGroup)
                    val icon = obj.optString("stream_icon", "")
                    val epgId = obj.optString("epg_channel_id", "xtream_$streamId")
                    val hlsUrl = "$baseUrl/live/$u/$p/$streamId.m3u8"
                    val tsUrl = "$baseUrl/live/$u/$p/$streamId.ts"

                    list.add(
                        LiveChannel(
                            key = "xtream_$streamId",
                            ordinal = i + 1,
                            name = cleanName,
                            group = classifiedGroup,
                            streamUrl = hlsUrl,
                            backupStreamUrl = tsUrl,
                            logoUrl = icon,
                            country = country,
                            qualityLabel = quality,
                            isPpv = classifiedGroup.contains("Sport", true),
                            priority = if (quality == "4K") 3 else if (quality == "FHD") 2 else 1,
                            epgChannelId = epgId
                        )
                    )
                }
                list
            }
        }.getOrElse {
            val fallbackM3uUrl = "$baseUrl/get.php?username=$u&password=$p&type=m3u_plus&output=ts"
            fetchM3uChannels(fallbackM3uUrl)
        }

        channels
    }

    companion object {
        fun buildIptvHttpClient(): OkHttpClient {
            val builder = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(25, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)

            runCatching {
                val trustAllCerts = arrayOf<TrustManager>(
                    object : X509TrustManager {
                        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
                        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
                        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
                    }
                )
                val sslContext = SSLContext.getInstance("TLS")
                sslContext.init(null, trustAllCerts, SecureRandom())
                builder.sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
                builder.hostnameVerifier { _, _ -> true }
            }

            return builder.build()
        }
    }
}
