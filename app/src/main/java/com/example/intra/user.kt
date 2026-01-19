package com.example.intra

import com.google.gson.annotations.SerializedName

data class User(
    val id: Int,
    val username: String,
    // ✅ NEW: Server se ye field aayegi
    @SerializedName("profile_photo") val profilePhoto: String? = null,
    // 🆕 STEP 2: Add these 2 fields
    var lastMessageTime: Long = 0L,    // Default 0 (no messages yet)
    var unreadCount: Int = 0           // Default 0 (all read)
)

data class UsersResponse(
    val success: Boolean,
    val users: List<User>
)