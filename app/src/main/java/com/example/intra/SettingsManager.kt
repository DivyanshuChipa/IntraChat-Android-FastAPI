package com.example.intra

import android.content.Context
import android.content.SharedPreferences

// JWT टोकन और Username को डिवाइस पर स्टोर करने का टूल
class SettingsManager(context: Context) {

    // SharedPreferences फ़ाइल का नाम
    private val prefs: SharedPreferences = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)

    companion object {
        const val KEY_USERNAME = "current_username"
        const val KEY_AUTH_TOKEN = "jwt_auth_token"
    }

    // JWT टोकन प्राप्त करें
    fun getToken(): String? {
        return prefs.getString(KEY_AUTH_TOKEN, null)
    }

    // यूज़र नेम प्राप्त करें
    fun getUsername(): String? {
        return prefs.getString(KEY_USERNAME, null)
    }

    // लॉगिन सफल होने पर Token और Username दोनों को सेव करें
    fun saveAuthDetails(username: String, token: String) {
        prefs.edit()
            .putString(KEY_USERNAME, username)
            .putString(KEY_AUTH_TOKEN, token)
            .apply()
    }

    // लॉगआउट के लिए
    fun clearAuthDetails() {
        prefs.edit()
            .remove(KEY_USERNAME)
            .remove(KEY_AUTH_TOKEN)
            .apply()
    }

    // चेक करें कि यूज़र लॉग इन है या नहीं
    fun isLoggedIn(): Boolean {
        return !getUsername().isNullOrEmpty() && !getToken().isNullOrEmpty()
    }
}