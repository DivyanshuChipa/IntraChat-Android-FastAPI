package com.example.intra

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)

    companion object {
        const val KEY_USERNAME = "current_username"
        const val KEY_AUTH_TOKEN = "jwt_auth_token"

        // 🆕 NEW: Server Config Keys
        const val KEY_SERVER_IP = "server_ip"
        const val KEY_SERVER_PORT = "server_port"
    }

    // --- EXISTING AUTH METHODS ---
    fun getToken(): String? = prefs.getString(KEY_AUTH_TOKEN, null)
    fun getUsername(): String? = prefs.getString(KEY_USERNAME, null)

    fun saveAuthDetails(username: String, token: String) {
        prefs.edit()
            .putString(KEY_USERNAME, username)
            .putString(KEY_AUTH_TOKEN, token)
            .apply()
    }

    fun clearAuthDetails() {
        prefs.edit()
            .remove(KEY_USERNAME)
            .remove(KEY_AUTH_TOKEN)
            .apply()
    }

    fun isLoggedIn(): Boolean {
        return !getUsername().isNullOrEmpty() && !getToken().isNullOrEmpty()
    }

    // --- 🆕 NEW: SERVER CONFIG METHODS ---

    // Default IP wahi rakhenge jo abhi tera hai
    fun getServerIp(): String {
        return prefs.getString(KEY_SERVER_IP, "192.168.31.104") ?: "192.168.31.104"
    }

    fun getServerPort(): String {
        return prefs.getString(KEY_SERVER_PORT, "8000") ?: "8000"
    }

    // IP aur Port save karne ka function
    fun saveServerConfig(ip: String, port: String) {
        prefs.edit()
            .putString(KEY_SERVER_IP, ip.trim())
            .putString(KEY_SERVER_PORT, port.trim())
            .apply()
    }

    // Helper to get full Base URL (http://192.168.x.x:8000/)
    fun getBaseUrl(): String {
        val ip = getServerIp()
        val port = getServerPort()
        return "http://$ip:$port/"
    }
}