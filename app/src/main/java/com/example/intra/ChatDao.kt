package com.example.intra.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ChatDao {

    // 👇 YEH PURANA - RAKHO
    @Query("SELECT * FROM messages ORDER BY timestamp ASC")
    suspend fun getAllMessages(): List<ChatMessageEntity>

    // 👇 YEH 2 NAYE ADD KARO
    @Query("""
        SELECT * FROM messages 
        WHERE (sender = :username AND receiver = :currentUser)
           OR (sender = :currentUser AND receiver = :username)
        ORDER BY timestamp ASC
    """)
    suspend fun getMessagesForUser(username: String, currentUser: String): List<ChatMessageEntity>

    @Query("""
        SELECT * FROM messages 
        WHERE receiver = 'Family Group'
        ORDER BY timestamp ASC
    """)
    suspend fun getFamilyGroupMessages(): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity)

    @Query("DELETE FROM messages")
    suspend fun clearAll()
}