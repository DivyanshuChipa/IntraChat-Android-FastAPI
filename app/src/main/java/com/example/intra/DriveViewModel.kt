package com.example.intra

import android.net.Uri
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.intra.util.SmbManager
import jcifs.context.SingletonContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DriveFileItem(
    val name: String,
    val isDirectory: Boolean,
    val path: String
)

class DriveViewModel(private val settingsManager: SettingsManager) : ViewModel() {

    private val TAG = "DriveViewModel"

    // UI States
    val files = mutableStateListOf<DriveFileItem>()
    val isLoading = mutableStateOf(false)
    val errorMessage = mutableStateOf<String?>(null)

    // Current Folder Path (e.g. smb://192.168.31.104/share_karo/IntraDrive/diya/)
    var currentPath = mutableStateOf("")

    private val auth = NtlmPasswordAuthenticator("", "", "")
    private val smbContext = SingletonContext.getInstance().withCredentials(auth)

    fun initializeUserDrive() {
        val username = settingsManager.getUsername() ?: "Guest"
        val serverIp = settingsManager.getServerIp()

        // 🌐 Base URL for user's folder
        val userRootPath = "smb://$serverIp/share_karo/IntraDrive/$username/"
        currentPath.value = userRootPath

        viewModelScope.launch {
            isLoading.value = true
            // Pehle ensure karo ki user ka folder server pe ban gaya hai
            val isReady = SmbManager.createUserFolder(username)
            if (isReady) {
                loadFilesFromPath(userRootPath)
            } else {
                errorMessage.value = "Failed to connect to Intra Drive."
            }
            isLoading.value = false
        }
    }

    fun loadFilesFromPath(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) { isLoading.value = true }

                val folder = SmbFile(path, smbContext)
                val smbFiles = folder.listFiles()

                val fileList = smbFiles?.map {
                    DriveFileItem(
                        name = it.name.removeSuffix("/"),
                        isDirectory = it.isDirectory,
                        path = it.path
                    )
                }?.sortedByDescending { it.isDirectory } ?: emptyList()

                withContext(Dispatchers.Main) {
                    files.clear()
                    files.addAll(fileList)
                    currentPath.value = path
                    isLoading.value = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading files", e)
                withContext(Dispatchers.Main) {
                    errorMessage.value = "Error loading files: ${e.message}"
                    isLoading.value = false
                }
            }
        }
    }

    fun navigateUp() {
        val current = currentPath.value
        val username = settingsManager.getUsername() ?: "Guest"
        val userRootPath = "smb://${settingsManager.getServerIp()}/share_karo/IntraDrive/$username/"

        // Agar user already root folder mein hai, toh aur upar mat jane do
        if (current == userRootPath) return

        // Upar wale folder ka path nikalo
        val parentPath = current.removeSuffix("/").substringBeforeLast("/") + "/"
        loadFilesFromPath(parentPath)
    }
}