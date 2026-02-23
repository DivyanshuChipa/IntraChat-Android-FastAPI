package com.example.intra.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [ChatMessageEntity::class], version = 4, exportSchema = false) // 👈 Yahan 3 se 4
abstract class ChatDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao

    companion object {
        @Volatile
        private var INSTANCE: ChatDatabase? = null

        // 🆕 MIGRATION 2 -> 3: Add isRead column
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add new column with default value FALSE (unread)
                database.execSQL(
                    "ALTER TABLE messages ADD COLUMN isRead INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        // 🆕 MIGRATION 3 -> 4: Add options column
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add new column for options (TEXT because we store JSON string)
                database.execSQL(
                    "ALTER TABLE messages ADD COLUMN options TEXT DEFAULT NULL"
                )
            }
        }

        fun getDatabase(context: Context): ChatDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ChatDatabase::class.java,
                    "chat_db"
                )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4) // 👈 Add migration here
                    .fallbackToDestructiveMigration() // 👈 Yeh line add karo
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}
