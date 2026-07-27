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
import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

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
                // ✅ Server se fresh users list fetch karo
                val response = ApiClient.apiService.getUsers()

                if (response.isSuccessful && response.body()?.success == true) {
                    val usersList = response.body()?.users ?: emptyList()

                    // 🆕 Users list ko JSON me convert karke SharedPrefs me cache karo
                    try {
                        val json = Gson().toJson(usersList)
                        val sharedPrefs = MyApplication.instance.getSharedPreferences("contacts_cache", Context.MODE_PRIVATE)
                        sharedPrefs.edit().putString("cached_users", json).apply()
                        Log.d("ContactVM", "✅ Cached contacts JSON locally")
                    } catch (cacheEx: Exception) {
                        Log.e("ContactVM", "Failed to cache contacts to SharedPreferences", cacheEx)
                    }

                    // Process and display fresh list
                    processAndSetContacts(usersList)
                }
            } catch (e: Exception) {
                Log.e("ContactVM", "Error fetching contacts, trying cache...", e)
                e.printStackTrace()

                // 🆕 Offline Fallback: SharedPreferences se cached users list load karo
                try {
                    val sharedPrefs = MyApplication.instance.getSharedPreferences("contacts_cache", Context.MODE_PRIVATE)
                    val cachedJson = sharedPrefs.getString("cached_users", null)
                    if (!cachedJson.isNullOrEmpty()) {
                        val type = object : TypeToken<List<User>>() {}.type
                        val cachedUsers: List<User> = Gson().fromJson(cachedJson, type)
                        
                        // Process and display cached list
                        processAndSetContacts(cachedUsers)
                        Log.d("ContactVM", "✅ Loaded cached contacts list offline successfully")
                    }
                } catch (cacheEx: Exception) {
                    Log.e("ContactVM", "Error loading cached contacts offline", cacheEx)
                }
            } finally {
                isRefreshing = false
            }
        }
    }

    // 🆕 Helper function to process DB values (unread counts, last message times) and update UI state
    // Note: Added 'suspend' modifier because chatDao functions are suspend functions
    private suspend fun processAndSetContacts(usersList: List<User>) {
        usersList.forEach { user ->
            // Last message ka time nikalo
            user.lastMessageTime = chatDao.getLastMessageTime(
                contactUsername = user.username,
                currentUser = currentUsername
            ) ?: 0L

            // Unread count nikalo
            user.unreadCount = chatDao.getUnreadCount(
                contactUsername = user.username,
                currentUser = currentUsername
            )
        }

        // Sort contacts - Recent messages wale upar
        val sortedUsers = usersList.sortedByDescending { it.lastMessageTime }

        // Restore current user's profile photo in settings
        usersList.find { it.username == currentUsername }?.profilePhoto?.let { photoPath ->
            settingsManager.saveMyPhoto(photoPath)
        }

        // UI state list update karo
        contacts.clear()
        contacts.addAll(sortedUsers)
        Log.d("ContactVM", "✅ UI state updated with ${usersList.size} contacts")
    }
}