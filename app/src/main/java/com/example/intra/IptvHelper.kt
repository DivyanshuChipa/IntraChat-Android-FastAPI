package com.example.intra

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

data class IptvChannel(
    val id: String,
    val name: String,
    val streamUrl: String,
    val logoUrl: String? = null,
    val group: String = "General"
)

object IptvHelper {
    private const val TAG = "IptvHelper"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Loads the M3U playlist from cache or network, parses it, and returns the channel list.
     */
    suspend fun loadPlaylist(context: Context, playlistUrl: String, forceRefresh: Boolean = false): List<IptvChannel> {
        return withContext(Dispatchers.IO) {
            val cacheFile = File(context.cacheDir, "iptv_cache.m3u")

            var m3uContent: String? = null

            // 1. Try cache if not forcing refresh
            if (!forceRefresh && cacheFile.exists() && cacheFile.length() > 0) {
                try {
                    m3uContent = cacheFile.readText()
                    Log.d(TAG, "Loaded playlist from local cache (${cacheFile.length()} bytes)")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to read cache file: ${e.message}")
                }
            }

            // 2. Fetch from network if cache is absent or refresh requested
            if (m3uContent == null) {
                try {
                    Log.d(TAG, "Fetching playlist from: $playlistUrl")
                    val request = Request.Builder().url(playlistUrl).build()
                    val response = client.newCall(request).execute()

                    if (response.isSuccessful && response.body != null) {
                        val bodyString = response.body!!.string()
                        if (bodyString.contains("#EXTM3U") || bodyString.contains("#EXTINF")) {
                            m3uContent = bodyString
                            cacheFile.writeText(bodyString)
                            Log.d(TAG, "Playlist fetched & cached successfully (${bodyString.length} chars)")
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

            // 4. Parse content or return curated fallback
            if (!m3uContent.isNullOrBlank()) {
                val parsed = parseM3u(m3uContent)
                if (parsed.isNotEmpty()) return@withContext parsed
            }

            Log.w(TAG, "Falling back to default curated FTA channels")
            getFallbackChannels()
        }
    }

    /**
     * Parses M3U / M3U8 string content into a list of IptvChannel objects.
     */
    fun parseM3u(content: String): List<IptvChannel> {
        val channels = mutableListOf<IptvChannel>()
        val lines = content.lines()

        var currentName = ""
        var currentLogo: String? = null
        var currentGroup = "General"

        val logoRegex = Regex("""tvg-logo="([^"]+)"""")
        val groupRegex = Regex("""group-title="([^"]+)"""")

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            if (line.startsWith("#EXTINF")) {
                // Parse Channel Name (after last comma)
                currentName = line.substringAfterLast(",", "Channel").trim()

                // Parse Logo
                val logoMatch = logoRegex.find(line)
                currentLogo = logoMatch?.groupValues?.get(1)?.trim()

                // Parse Group
                val groupMatch = groupRegex.find(line)
                currentGroup = groupMatch?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() } ?: "General"

            } else if (!line.startsWith("#") && (line.startsWith("http://") || line.startsWith("https://"))) {
                if (currentName.isNotEmpty()) {
                    val id = "ch_${channels.size}_${currentName.hashCode()}"
                    channels.add(
                        IptvChannel(
                            id = id,
                            name = currentName,
                            streamUrl = line,
                            logoUrl = currentLogo,
                            group = currentGroup
                        )
                    )
                }
                // Reset for next channel
                currentName = ""
                currentLogo = null
                currentGroup = "General"
            }
        }

        return channels
    }

    /**
     * High-reliability fallback Free-To-Air channels in case of DNS or connection failure.
     */
    fun getFallbackChannels(): List<IptvChannel> {
        return listOf(
            IptvChannel(
                id = "9xm",
                name = "9XM",
                streamUrl = "https://9xjio.wiseplayout.com/9XM/master.m3u8",
                logoUrl = "https://xstreamcp-assets-msp.streamready.in/assets/LIVETV/LIVECHANNEL/LIVETV_LIVETVCHANNEL_9XM/images/LOGO_HD/image.png",
                group = "Music"
            ),
            IptvChannel(
                id = "9x_jalwa",
                name = "9X Jalwa",
                streamUrl = "https://wiselp.wiseplayout.com/9X_Jalwa/master.m3u8",
                logoUrl = "https://xstreamcp-assets-msp.streamready.in/assets/LIVETV/LIVECHANNEL/LIVETV_LIVETVCHANNEL_9X_JALWA/images/LOGO_HD/image.png",
                group = "Music"
            ),
            IptvChannel(
                id = "4tv_news",
                name = "4TV News",
                streamUrl = "https://cdn.pishow.tv/ott/live/1007/master.m3u8",
                logoUrl = "https://jiotvimages.cdn.jio.com/dare_images/images/4_TV.png",
                group = "News"
            ),
            IptvChannel(
                id = "4_sides_tv",
                name = "4 SiDES TV",
                streamUrl = "https://stream.ottlive.co.in/4sidestv/index.m3u8",
                logoUrl = "https://jiotvimages.cdn.jio.com/dare_images/images/4sites.png",
                group = "News"
            )
        )
    }
}
