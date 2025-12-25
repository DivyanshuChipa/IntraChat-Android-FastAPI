package com.example.intra

import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import com.example.intra.database.ChatMessageEntity // इसे रखा है, भले ही यह अभी उपयोग न हो

// ===================== DATA MODELS (JSON) =====================

// 1. Login/Register के लिए रिक्वेस्ट बॉडी (Username/Password)
data class AuthRequest(
    val username: String,
    val password: String
)

// 2. Login/Register के लिए रिस्पांस बॉडी (Token/Username)
data class AuthResponse(
    val success: Boolean,
    val token: String?, // JWT Token
    val username: String?,
    val message: String? // Error message
)

// 3. सर्वर से हिस्ट्री सिंक करने के लिए मैसेज का मॉडल (Future Use)
data class ServerMessage(
    val text: String,
    val type: String,
    val fileUrl: String?,
    val fileName: String?,
    val timestamp: Long,
    val sender: String? = "Unknown"
)

// ===================== API INTERFACE =====================

interface ApiService {

    // 📩 Existing: File Upload Endpoint
    @Multipart
    @POST("upload")
    suspend fun uploadFile(
        @Part file: MultipartBody.Part
    ): Response<ResponseBody>

    // 🔑 New: User Registration Endpoint
    @POST("register")
    suspend fun registerUser(@Body request: AuthRequest): Response<AuthResponse>

    // 🔑 New: User Login Endpoint
    @POST("login")
    suspend fun loginUser(@Body request: AuthRequest): Response<AuthResponse>

    // 💬 Future: Server history sync endpoint
    // Note: इसे बाद में ऑथेंटिकेशन टोकन की ज़रूरत होगी
    @GET("messages")
    suspend fun getRecentMessages(): Response<List<ServerMessage>>

    @GET("users")
    suspend fun getUsers(): Response<UsersResponse>
}