package com.example.intra

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.intra.database.ChatDao

class ChatViewModelFactory(
    private val chatDao: ChatDao,
    private val settingsManager: SettingsManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ChatViewModel(chatDao, settingsManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}