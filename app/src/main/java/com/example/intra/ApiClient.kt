package com.example.intra

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object ApiClient {

    // Cache variables to avoid redundant connection creation
    @Volatile
    private var retrofit: Retrofit? = null
    @Volatile
    private var lastBaseUrl: String? = null
    @Volatile
    private var apiServiceInstance: ApiService? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .writeTimeout(5, TimeUnit.MINUTES)
        .build()

    /**
     * Provides the ApiService instance.
     * Uses a getter property with a synchronized block for thread-safe initialization.
     * Recreates the Retrofit instance if the base URL changes in settings.
     */
    val apiService: ApiService
        get() {
            val context = MyApplication.instance
            val settings = SettingsManager(context)
            val currentUrl = settings.getBaseUrl()

            // Double-checked locking for thread-safety and performance
            if (retrofit == null || lastBaseUrl != currentUrl || apiServiceInstance == null) {
                synchronized(this) {
                    if (retrofit == null || lastBaseUrl != currentUrl || apiServiceInstance == null) {
                        retrofit = Retrofit.Builder()
                            .baseUrl(currentUrl)
                            .client(client)
                            .addConverterFactory(GsonConverterFactory.create())
                            .build()

                        // Save new URL reference
                        lastBaseUrl = currentUrl
                        apiServiceInstance = retrofit!!.create(ApiService::class.java)
                    }
                }
            }

            return apiServiceInstance!!
        }

    /**
     * Constructs the WebSocket URL based on current server settings.
     */
    fun getWsUrl(username: String): String {
        val context = MyApplication.instance
        val settings = SettingsManager(context)
        val ip = settings.getServerIp()
        val port = settings.getServerPort()

        return "ws://$ip:$port/ws/$username"
    }
}
