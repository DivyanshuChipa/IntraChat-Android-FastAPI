package com.example.intra

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object ApiClient {
    private var BASE_URL = "http://192.168.31.104:8000/"

    fun updateBaseUrl(settingsManager: SettingsManager) {
        val ip = settingsManager.getServerIp()
        val port = settingsManager.getServerPort()
        BASE_URL = "http://$ip:$port/"
        rebuildRetrofit()
    }

    // #OkHttpClient को कॉन्फ़िगर करें
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    // Retrofit Instance
    private lateinit var retrofit: Retrofit

    init {
        rebuildRetrofit()
    }

    fun rebuildRetrofit() {
        retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    // #ApiService को एक्सेस करने के लिए पब्लिक मेथड
    val apiService: ApiService
        get() = retrofit.create(ApiService::class.java)

    // 💡 New function to get dynamic WS URL
    fun getWsUrl(username: String): String {
        // 💡 ध्यान दें: अब URL में /ws/{username} आ रहा है
        return "${BASE_URL.replace("http", "ws")}ws/$username"
    }
}