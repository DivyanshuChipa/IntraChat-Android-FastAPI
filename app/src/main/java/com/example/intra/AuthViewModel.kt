package com.example.intra

import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import org.json.JSONObject

class AuthViewModel : ViewModel() {

    private val TAG = "AuthViewModel"

    // सुनिश्चित करें कि यह MyApplication.instance का उपयोग कर रहा है
    private val settingsManager = SettingsManager(MyApplication.instance)
    private val apiService = ApiClient.apiService

    // UI State
    val usernameInput = mutableStateOf("")
    val passwordInput = mutableStateOf("")
    val isLoading = mutableStateOf(false)
    val errorMessage = mutableStateOf<String?>(null)

    // App Navigation के लिए मुख्य State
    val isAuthenticated = mutableStateOf(settingsManager.isLoggedIn())

    // --- Public Functions ---

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
                val response = apiService.loginUser(request)

                if (response.isSuccessful && response.body()?.success == true) {
                    val body = response.body()!!
                    body.token?.let { token ->
                        body.username?.let { user ->
                            settingsManager.saveAuthDetails(user, token)

                            // ✅ Debug log
                            Log.d(TAG, "✅ Login success: username=$user, token=${token.take(10)}...")

                            isAuthenticated.value = true
                        }
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    val msg = errorBody ?: "Login failed. Check server status."
                    errorMessage.value = extractErrorMessage(msg)
                }
            } catch (e: Exception) {
                val msg = when(e) {
                    is HttpException -> "Login Failed. Server error: ${e.code()}"
                    is IOException -> "Connection failed. Check server IP/Port."
                    else -> "An unknown error occurred."
                }
                errorMessage.value = msg
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
                val response = apiService.registerUser(request)

                if (response.isSuccessful && response.body()?.success == true) {
                    errorMessage.value = "Registration Successful! Logging in..."
                    login() // Auto login after successful registration
                } else {
                    val errorBody = response.errorBody()?.string()
                    val msg = errorBody ?: "Registration failed."
                    errorMessage.value = extractErrorMessage(msg)
                }
            } catch (e: Exception) {
                errorMessage.value = "Registration error: ${e.message}"
            } finally {
                isLoading.value = false
            }
        }
    }

    // Server से JSON Error message निकालने के लिए
    private fun extractErrorMessage(jsonString: String): String {
        return try {
            val json = JSONObject(jsonString)
            json.optString("message", "Request failed.")
        } catch (e: Exception) {
            jsonString
        }
    }

    // 🚪 Log Out (बाद में Chat Screen से उपयोग होगा)
    fun logout() {
        settingsManager.clearAuthDetails()
        isAuthenticated.value = false
        usernameInput.value = ""
        passwordInput.value = ""
    }
}