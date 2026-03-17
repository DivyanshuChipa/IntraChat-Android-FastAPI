package com.example.intra

import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import org.json.JSONObject
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody

class AuthViewModel : ViewModel() {

    private val TAG = "AuthViewModel"

    private val settingsManager = SettingsManager(MyApplication.instance)

    // UI State
    val usernameInput = mutableStateOf("")
    val passwordInput = mutableStateOf("")
    val isLoading = mutableStateOf(false)
    val errorMessage = mutableStateOf<String?>(null)

    // App Navigation state
    val isAuthenticated = mutableStateOf(settingsManager.isLoggedIn())

    fun clearError() {
        errorMessage.value = null
    }

    // 🔑 Login Logic
    fun login() {
        clearError()
        if (isLoading.value) return

        val username = usernameInput.value.trim()
        val password = passwordInput.value.trim()

        if (username.isEmpty() || password.isEmpty()) {
            errorMessage.value = "Username and Password cannot be empty."
            return
        }

        isLoading.value = true
        viewModelScope.launch {
            try {
                val request = AuthRequest(username, password)
                val response = ApiClient.apiService.loginUser(request)

                if (response.isSuccessful && response.body()?.success == true) {
                    val body = response.body()!!
                    body.token?.let { token ->
                        body.username?.let { user ->
                            settingsManager.saveAuthDetails(user, token)
                            Log.d(TAG, "✅ Login success: username=$user")
                            isAuthenticated.value = true
                        }
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    errorMessage.value = extractErrorMessage(errorBody ?: "Login failed.")
                }
            } catch (e: Exception) {
                errorMessage.value = handleNetworkError(e)
            } finally {
                isLoading.value = false
            }
        }
    }

    // 📩 Register Logic
    fun register() {
        clearError()
        if (isLoading.value) return

        val username = usernameInput.value.trim()
        val password = passwordInput.value.trim()

        if (username.isEmpty() || password.isEmpty()) {
            errorMessage.value = "Username and Password required for registration."
            return
        }

        isLoading.value = true
        viewModelScope.launch {
            try {
                val request = AuthRequest(username, password)
                val response = ApiClient.apiService.registerUser(request)

                if (response.isSuccessful && response.body()?.success == true) {
                    errorMessage.value = "Registration Successful! Logging in..."
                    login()
                } else {
                    val errorBody = response.errorBody()?.string()
                    errorMessage.value = extractErrorMessage(errorBody ?: "Registration failed.")
                }
            } catch (e: Exception) {
                errorMessage.value = "Registration error: ${e.message}"
            } finally {
                isLoading.value = false
            }
        }
    }

    // 💀 NEW: Delete Account Logic Integrated
    fun deleteAccount(onSuccess: () -> Unit) {
        clearError()
        if (isLoading.value) return

        val username = settingsManager.getUsername()
        val password = passwordInput.value.trim() // Confirm karne ke liye UI se password lega

        if (username == null || password.isEmpty()) {
            errorMessage.value = "Please enter your password to confirm deletion."
            return
        }

        isLoading.value = true
        viewModelScope.launch {
            try {
                // Same AuthRequest use kar rahe hain (username + password)
                val request = AuthRequest(username, password)
                val response = ApiClient.apiService.deleteAccount(request)

                if (response.isSuccessful && response.body()?.success == true) {
                    Log.d(TAG, "💀 Account Deleted: $username")
                    logout() // Local data saaf karo
                    onSuccess() // Screen navigate karo
                } else {
                    val errorBody = response.errorBody()?.string()
                    errorMessage.value = extractErrorMessage(errorBody ?: "Failed to delete account.")
                }
            } catch (e: Exception) {
                errorMessage.value = "Error: ${e.message}"
            } finally {
                isLoading.value = false
            }
        }
    }

    // 🚪 Log Out
    fun logout() {
        settingsManager.clearAuthDetails()
        isAuthenticated.value = false
        usernameInput.value = ""
        passwordInput.value = ""
    }

    // 📸 UPLOAD PROFILE PHOTO
    fun uploadProfilePhoto(file: java.io.File, onResult: (Boolean) -> Unit) {
        if (isLoading.value) return

        val currentUser = settingsManager.getUsername() ?: return
        isLoading.value = true

        viewModelScope.launch {
            try {
                val usernamePart = okhttp3.RequestBody.create(
                    okhttp3.MultipartBody.FORM,
                    currentUser
                )

                val filePart = okhttp3.MultipartBody.Part.createFormData(
                    "file",
                    file.name,
                    file.asRequestBody("image/*".toMediaTypeOrNull())
                )

                val response = ApiClient.apiService.uploadProfilePhoto(usernamePart, filePart)

                if (response.isSuccessful && response.body()?.success == true) {
                    val newPhotoUrl = response.body()?.profilePhoto
                    val timestamp = System.currentTimeMillis()
                    val urlWithTime = "$newPhotoUrl?t=$timestamp"

                    settingsManager.saveMyPhoto(urlWithTime)
                    Log.d(TAG, "✅ Profile Photo Uploaded: $urlWithTime")
                    onResult(true)
                } else {
                    errorMessage.value = "Failed to upload photo"
                    onResult(false)
                }
            } catch (e: Exception) {
                errorMessage.value = "Error: ${e.message}"
                onResult(false)
            } finally {
                isLoading.value = false
            }
        }
    }

    private fun extractErrorMessage(jsonString: String): String {
        return try {
            val json = JSONObject(jsonString)
            json.optString("message", "Request failed.")
        } catch (e: Exception) {
            jsonString
        }
    }

    private fun handleNetworkError(e: Exception): String {
        return when(e) {
            is HttpException -> "Server error: ${e.code()}"
            is IOException -> "Connection failed. Check server IP/Port."
            else -> "An unknown error occurred."
        }
    }
}