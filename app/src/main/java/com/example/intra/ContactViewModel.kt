package com.example.intra

import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.intra.database.ChatDatabase
import kotlinx.coroutines.launch

class ContactViewModel : ViewModel() {

    // Refresh state for Pull-to-Refresh
    var isRefreshing by mutableStateOf(false)
        private set

    // 🆕 STEP 3A: ChatDao ka reference lo
    private val chatDao = ChatDatabase
        .getDatabase(MyApplication.instance)
        .chatDao()

    // 🆕 STEP 3B: Current user ka username lo
    private val settingsManager = SettingsManager(MyApplication.instance)
    private val currentUsername: String
        get() = settingsManager.getUsername() ?: "Guest"

    // UI के लिए कॉन्टैक्ट्स की लिस्ट
    val contacts = mutableStateListOf<User>()

    init {
        fetchContacts()
    }

    fun fetchContacts() {
        viewModelScope.launch {
            isRefreshing = true
            try {
                // ✅ STEP 3C: Server se users fetch karo (same as before)
                val response = ApiClient.apiService.getUsers()

                if (response.isSuccessful && response.body()?.success == true) {
                    val usersList = response.body()?.users ?: emptyList()

                    // 🆕 STEP 3D: Har user ke liye DB se data nikalo
                    usersList.forEach { user ->
                        // Last message ka time nikalo
                        user.lastMessageTime = chatDao.getLastMessageTime(
                            contactUsername = user.username,
                            currentUser = currentUsername
                        ) ?: 0L  // Agar null aaye toh 0L set karo

                        // Unread count nikalo
                        user.unreadCount = chatDao.getUnreadCount(
                            contactUsername = user.username,
                            currentUser = currentUsername
                        )

                        Log.d("ContactVM", "${user.username}: time=${user.lastMessageTime}, unread=${user.unreadCount}")
                    }

                    // 🆕 STEP 3E: Sort karo - Recent messages wale upar
                    val sortedUsers = usersList.sortedByDescending { it.lastMessageTime }

                    // 🆕 STEP 3F: UI list update karo
                    contacts.clear()
                    contacts.addAll(sortedUsers)

                    Log.d("ContactVM", "✅ Contacts sorted by recent activity")
                }

            } catch (e: Exception) {
                Log.e("ContactVM", "Error fetching contacts", e)
                e.printStackTrace()
            } finally {
                isRefreshing = false
            }
        }
    }
}