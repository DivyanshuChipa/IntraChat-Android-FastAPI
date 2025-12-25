package com.example.intra

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object ApiClient {
    // #यहाँ वही IP डालें जिस पर तुम्हारा सर्वर चल रहा है
    private const val BASE_URL = "http://192.168.31.104:8000/"

    // #OkHttpClient को कॉन्फ़िगर करें
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    // Retrofit Instance
    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            // #GsonConverterFactory की ज़रूरत है अगर तुम server JSON response को parse करना चाहते हो
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    // #ApiService को एक्सेस करने के लिए पब्लिक मेथड
    val apiService: ApiService by lazy {
        retrofit.create(ApiService::class.java)
    }

    // 💡 New function to get dynamic WS URL
    fun getWsUrl(username: String): String {
        // 💡 ध्यान दें: अब URL में /ws/{username} आ रहा है
        return "${BASE_URL.replace("http", "ws")}ws/$username"
    }
}