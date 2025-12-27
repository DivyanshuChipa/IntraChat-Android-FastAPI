package com.example.intra

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object ApiClient {

    // Cache variables (taaki baar baar naya connection na banaye)
    private var retrofit: Retrofit? = null
    private var lastBaseUrl: String? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    // 🔥 FIX: 'fun getApiService()' hata diya.
    // Saara logic 'val apiService' ke getter me daal diya.
    // Ab baki files (AuthViewModel, etc.) me koi error nahi aayega.
    val apiService: ApiService
        get() {
            // 1. Settings se current URL nikalo
            val context = MyApplication.instance
            val settings = SettingsManager(context)
            val currentUrl = settings.getBaseUrl()

            // 2. Check karo: Agar Retrofit nahi bana hai, ya IP change ho gayi hai
            if (retrofit == null || lastBaseUrl != currentUrl) {
                retrofit = Retrofit.Builder()
                    .baseUrl(currentUrl)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()

                // Save new URL reference
                lastBaseUrl = currentUrl
            }

            // 3. Return API service
            return retrofit!!.create(ApiService::class.java)
        }

    // WebSocket URL helper (Same as before)
    fun getWsUrl(username: String): String {
        val context = MyApplication.instance
        val settings = SettingsManager(context)
        val ip = settings.getServerIp()
        val port = settings.getServerPort()

        return "ws://$ip:$port/ws/$username"
    }
}