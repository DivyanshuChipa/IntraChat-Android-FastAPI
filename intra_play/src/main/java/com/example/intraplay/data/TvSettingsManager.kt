package com.example.intraplay.data

import android.content.Context
import android.content.SharedPreferences

class TvSettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREF_NAME = "intraplay_tv_prefs"
        const val KEY_PLAYLIST_URL = "iptv_playlist_url"
        const val DEFAULT_PLAYLIST_URL = "https://iptv-org.github.io/iptv/countries/in.m3u"
        const val KEY_LAST_CHANNEL_URL = "last_played_channel_url"
        const val KEY_FAVORITES = "favorite_channel_ids"
    }

    fun getPlaylistUrl(): String {
        return prefs.getString(KEY_PLAYLIST_URL, DEFAULT_PLAYLIST_URL) ?: DEFAULT_PLAYLIST_URL
    }

    fun setPlaylistUrl(url: String) {
        prefs.edit().putString(KEY_PLAYLIST_URL, url.trim()).apply()
    }

    fun resetPlaylistUrl() {
        prefs.edit().putString(KEY_PLAYLIST_URL, DEFAULT_PLAYLIST_URL).apply()
    }

    fun getLastChannelUrl(): String? {
        return prefs.getString(KEY_LAST_CHANNEL_URL, null)
    }

    fun setLastChannelUrl(url: String) {
        prefs.edit().putString(KEY_LAST_CHANNEL_URL, url).apply()
    }

    fun getFavorites(): Set<String> {
        return prefs.getStringSet(KEY_FAVORITES, emptySet()) ?: emptySet()
    }

    fun toggleFavorite(channelId: String) {
        val current = getFavorites().toMutableSet()
        if (current.contains(channelId)) {
            current.remove(channelId)
        } else {
            current.add(channelId)
        }
        prefs.edit().putStringSet(KEY_FAVORITES, current).apply()
    }

    fun isFavorite(channelId: String): Boolean {
        return getFavorites().contains(channelId)
    }
}
