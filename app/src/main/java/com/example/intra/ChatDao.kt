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

    @Insert(onConflict = OnConflictStrategy.IGNORE) // Agar conflict ho to ignore karo
    suspend fun insertMessageRaw(message: ChatMessageEntity): Long

    // 🔥 NEW FUNCTION: Check karega ki duplicate hai ya nahi
    @Query("SELECT COUNT(*) FROM messages WHERE sender = :sender AND timestamp = :timestamp AND text = :text")
    suspend fun countMessage(sender: String, timestamp: Long, text: String): Int

    // 🔥 Transaction wala method (Logic handle karne ke liye)
    @androidx.room.Transaction
    suspend fun insertMessage(message: ChatMessageEntity) {
        // Pehle check karo ki kya ye message exist karta hai?
        val count = countMessage(message.sender, message.timestamp, message.text)

        if (count == 0) {
            insertMessageRaw(message)
        } else {
            // Duplicate found, do nothing
            android.util.Log.d("ChatDao", "Duplicate message ignored in DB")
        }
    }
    // 🆕 STEP 1A: Last Message Time Nikalne Ke Liye
    @Query("""
        SELECT MAX(timestamp) FROM messages 
        WHERE (sender = :contactUsername AND receiver = :currentUser)
           OR (sender = :currentUser AND receiver = :contactUsername)
    """)
    suspend fun getLastMessageTime(
        contactUsername: String,
        currentUser: String
    ): Long?


    // 🆕 STEP 1B: Unread Count Nikalne Ke Liye
    @Query("""
        SELECT COUNT(*) FROM messages 
        WHERE sender = :contactUsername 
          AND receiver = :currentUser 
          AND isRead = 0
    """)
    suspend fun getUnreadCount(
        contactUsername: String,
        currentUser: String
    ): Int
    @Query("""
        UPDATE messages 
        SET isRead = 1
        WHERE sender = :contactUsername 
          AND receiver = :currentUser
          AND isRead = 0
    """)
    suspend fun markMessagesAsRead(
        contactUsername: String,
        currentUser: String
    )



    @Query("DELETE FROM messages")
    suspend fun clearAll()


}