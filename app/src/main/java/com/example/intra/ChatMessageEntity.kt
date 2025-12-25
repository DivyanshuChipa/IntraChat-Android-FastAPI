package com.example.intra.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val isSelf: Boolean,
    val type: String,
    val fileUrl: String?,
    val fileName: String?,
    val timestamp: Long = System.currentTimeMillis(),
    val senderName: String = "User",
    val sender: String,      // ✅ Default value mat do
    val receiver: String     // ✅ Default value mat do
)
