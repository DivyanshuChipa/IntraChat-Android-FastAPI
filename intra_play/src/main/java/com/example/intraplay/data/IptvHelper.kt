package com.example.intraplay.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

object IptvHelper {
    private const val TAG = "IptvHelper"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun loadPlaylist(context: Context, playlistUrl: String, forceRefresh: Boolean = false): List<IptvChannel> {
        return withContext(Dispatchers.IO) {
            val cacheFile = File(context.cacheDir, "iptv_cache.m3u")
            var m3uContent: String? = null

            // 1. Try local cache first
            if (!forceRefresh && cacheFile.exists() && cacheFile.length() > 0) {
                try {
                    m3uContent = cacheFile.readText()
                    Log.d(TAG, "Loaded IPTV playlist from local cache (${cacheFile.length()} bytes)")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to read cache file: ${e.message}")
                }
            }

            // 2. Fetch from network
            if (m3uContent == null) {
                try {
                    Log.d(TAG, "Fetching IPTV playlist from: $playlistUrl")
                    val request = Request.Builder().url(playlistUrl).build()
                    val response = client.newCall(request).execute()

                    if (response.isSuccessful && response.body != null) {
                        val bodyString = response.body!!.string()
                        if (bodyString.contains("#EXTM3U") || bodyString.contains("#EXTINF")) {
                            m3uContent = bodyString
                            cacheFile.writeText(bodyString)
                            Log.d(TAG, "IPTV playlist fetched & cached successfully (${bodyString.length} chars)")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Network error fetching playlist: ${e.message}")
                }
            }

            // 3. Fallback to cache if network failed
            if (m3uContent == null && cacheFile.exists() && cacheFile.length() > 0) {
                try {
                    m3uContent = cacheFile.readText()
                } catch (_: Exception) {}
            }

            // 4. Parse content
            if (!m3uContent.isNullOrBlank()) {
                val parsed = parseM3u(m3uContent)
                if (parsed.isNotEmpty()) return@withContext parsed
            }

            Log.w(TAG, "Falling back to default curated FTA channels")
            getFallbackChannels()
        }
    }

    fun parseM3u(content: String): List<IptvChannel> {
        val channels = mutableListOf<IptvChannel>()
        val lines = content.lines()

        var currentName: String? = null
        var currentLogo: String? = null
        var currentGroup = "General"
        var currentId = ""

        val logoRegex = """tvg-logo="([^"]*)"""".toRegex()
        val groupRegex = """group-title="([^"]*)"""".toRegex()
        val idRegex = """tvg-id="([^"]*)"""".toRegex()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("#EXTINF:")) {
                currentId = idRegex.find(trimmed)?.groupValues?.get(1) ?: ""
                currentLogo = logoRegex.find(trimmed)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
                currentGroup = groupRegex.find(trimmed)?.groupValues?.get(1)?.takeIf { it.isNotBlank() } ?: "General"

                val commaIndex = trimmed.lastIndexOf(',')
                currentName = if (commaIndex != -1 && commaIndex < trimmed.length - 1) {
                    trimmed.substring(commaIndex + 1).trim()
                } else {
                    "Channel ${channels.size + 1}"
                }
            } else if (trimmed.isNotBlank() && !trimmed.startsWith("#") && currentName != null) {
                if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                    channels.add(
                        IptvChannel(
                            id = if (currentId.isNotBlank()) currentId else "ch_${channels.size + 1}",
                            name = currentName,
                            streamUrl = trimmed,
                            logoUrl = currentLogo,
                            group = currentGroup
                        )
                    )
                }
                currentName = null
                currentLogo = null
                currentGroup = "General"
                currentId = ""
            }
        }
        return channels
    }

    fun getFallbackChannels(): List<IptvChannel> {
        return listOf(
            IptvChannel(
                id = "dd_news",
                name = "DD News HD",
                streamUrl = "https://d35j504z0x2vu2.cloudfront.net/v1/master/0bc8e83ba5ca846b4503bc73b71e00e4708c3527/DDNews/DDNews.m3u8",
                logoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/e/e0/DD_News_Logo.svg/200px-DD_News_Logo.svg.png",
                group = "News"
            ),
            IptvChannel(
                id = "dd_india",
                name = "DD India HD",
                streamUrl = "https://d35j504z0x2vu2.cloudfront.net/v1/master/0bc8e83ba5ca846b4503bc73b71e00e4708c3527/DDIndia/DDIndia.m3u8",
                logoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/3/30/DD_India_logo.png/200px-DD_India_logo.png",
                group = "News"
            ),
            IptvChannel(
                id = "sansad_tv",
                name = "Sansad TV HD",
                streamUrl = "https://d35j504z0x2vu2.cloudfront.net/v1/master/0bc8e83ba5ca846b4503bc73b71e00e4708c3527/SansadTV1/SansadTV1.m3u8",
                logoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/9/9f/Sansad_TV_Logo.png/200px-Sansad_TV_Logo.png",
                group = "News"
            ),
            IptvChannel(
                id = "dd_sports",
                name = "DD Sports",
                streamUrl = "https://d35j504z0x2vu2.cloudfront.net/v1/master/0bc8e83ba5ca846b4503bc73b71e00e4708c3527/DDSports/DDSports.m3u8",
                logoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/6/60/DD_Sports_logo.png/200px-DD_Sports_logo.png",
                group = "Sports"
            ),
            IptvChannel(
                id = "redbull_tv",
                name = "Red Bull TV",
                streamUrl = "https://rbmn-live.akamaized.net/hls/live/590964/BoRB-AT/master.m3u8",
                logoUrl = "https://resources.redbull.com/logos/redbull-tv-logo.png",
                group = "Sports"
            )
        )
    }
}
