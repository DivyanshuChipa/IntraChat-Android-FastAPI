package com.example.intra.util

import android.util.Log
import jcifs.context.SingletonContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SmbManager {
    private const val TAG = "SmbManager"

    // 🌐 Tera The Tank ka IP aur Share ka naam
    // Format: smb://<IP>/<SHARE_NAME>/
    private const val BASE_SMB_URL = "smb://192.168.31.104/share_karo/IntraDrive/"

    // 🔓 Guest Authentication (Bina Password)
    // JCIFS mein Anonymous login ke liye blank strings pass karte hain
    private val auth = NtlmPasswordAuthenticator("", "", "")

    // Create Context with Guest Auth
    private val smbContext = SingletonContext.getInstance().withCredentials(auth)

    /**
     * Ye function check karega ki user ka folder exist karta hai ya nahi.
     * Agar nahi hai, toh naya bana dega.
     */
    suspend fun createUserFolder(username: String): Boolean = withContext(Dispatchers.IO) {
        try {
            // e.g., smb://192.168.31.104/share_karo/IntraDrive/diya/
            val userFolderPath = "$BASE_SMB_URL$username/"
            val userFolder = SmbFile(userFolderPath, smbContext)

            if (!userFolder.exists()) {
                userFolder.mkdirs() // Auto-create folder
                Log.d(TAG, "📁 Created new NAS folder for: $username")
            } else {
                Log.d(TAG, "✅ NAS folder already exists for: $username")
            }

            // Uske andar sub-folders bhi bana dete hain auto-sorting ke liye!
            val subDirs = listOf("Images", "Videos", "Documents")
            for (dir in subDirs) {
                val subFolder = SmbFile("$userFolderPath$dir/", smbContext)
                if (!subFolder.exists()) subFolder.mkdirs()
            }
            return@withContext true

        } catch (e: Exception) {
            Log.e(TAG, "❌ NAS Error: ${e.message}", e)
            return@withContext false
        }
    }
}