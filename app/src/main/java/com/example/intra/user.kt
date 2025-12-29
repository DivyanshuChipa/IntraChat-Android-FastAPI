package com.example.intra

import com.google.gson.annotations.SerializedName

data class User(
    val id: Int,
    val username: String,
    // ✅ NEW: Server se ye field aayegi
    @SerializedName("profile_photo") val profilePhoto: String? = null
)

data class UsersResponse(
    val success: Boolean,
    val users: List<User>
)