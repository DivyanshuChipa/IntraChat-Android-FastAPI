package com.example.intra

data class User(
    val id: Int,
    val username: String
)

data class UsersResponse(
    val success: Boolean,
    val users: List<User>
)