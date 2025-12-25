package com.example.intra

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class ContactViewModel : ViewModel() {

    private val apiService = ApiClient.apiService

    // UI के लिए कॉन्टैक्ट्स की लिस्ट
    val contacts = mutableStateListOf<User>()

    init {
        fetchContacts()
    }

    fun fetchContacts() {
        viewModelScope.launch {
            try {
                val response = apiService.getUsers()
                if (response.isSuccessful && response.body()?.success == true) {
                    val usersList = response.body()?.users ?: emptyList()

                    contacts.clear()
                    contacts.addAll(usersList)
                }
            } catch (e: Exception) {
                // Error handling (Silent for now)
                e.printStackTrace()
            }
        }
    }
}