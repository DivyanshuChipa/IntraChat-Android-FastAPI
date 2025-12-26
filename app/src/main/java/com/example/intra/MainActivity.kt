package com.example.intra

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.intra.database.ChatDatabase
import com.example.intra.ui.theme.IntraTheme
import java.io.File
import java.io.FileOutputStream
import android.net.Uri
import android.content.Context
import android.provider.OpenableColumns
import android.util.Log

sealed class NavigationState {
    object ContactList : NavigationState()
    data class Chat(val receiver: String) : NavigationState()
    object Settings : NavigationState()
    object ChangeIpAddress : NavigationState()
}

class MainActivity : ComponentActivity() {

    // File Upload Variables
    private var currentUploadViewModel: ChatViewModel? = null
    private var currentUploadReceiver: String? = null

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val file = uriToTempFile(this, it)
            if (file != null && currentUploadViewModel != null && currentUploadReceiver != null) {
                currentUploadViewModel?.uploadFile(file, currentUploadReceiver!!)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val chatDao = ChatDatabase.getDatabase(MyApplication.instance).chatDao()
        val settingsManager = SettingsManager(this)
        val chatViewModelFactory = ChatViewModelFactory(chatDao, settingsManager)

        ApiClient.updateBaseUrl(settingsManager)

        setContent {
            IntraTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val authViewModel: AuthViewModel = viewModel()
                    val chatViewModel: ChatViewModel = viewModel(factory = chatViewModelFactory)
                    val isAuthenticated by authViewModel.isAuthenticated
                    var navigationState by remember { mutableStateOf<NavigationState>(NavigationState.ContactList) }

                    if (!isAuthenticated) {
                        AuthScreen(
                            viewModel = authViewModel,
                            onAuthenticated = {
                                ApiClient.updateBaseUrl(settingsManager)
                                chatViewModel.updateWsManagerDetails(settingsManager)
                            }
                        )
                    } else {
                        when (val state = navigationState) {
                            is NavigationState.ContactList -> {
                                ContactListScreen(
                                    username = chatViewModel.currentUsername,
                                    typingStatuses = chatViewModel.typingStatuses,
                                    onChatClick = { selectedUser ->
                                        navigationState = NavigationState.Chat(selectedUser)
                                    },
                                    onSettingsClick = {
                                        navigationState = NavigationState.Settings
                                    }
                                )
                            }
                            is NavigationState.Chat -> {
                                ChatScreen(
                                    viewModel = chatViewModel,
                                    receiverName = state.receiver,
                                    onAttachClick = {
                                        currentUploadViewModel = chatViewModel
                                        currentUploadReceiver = state.receiver
                                        filePickerLauncher.launch("*/*")
                                    },
                                    onBackClick = {
                                        chatViewModel.closeChat()
                                        navigationState = NavigationState.ContactList
                                    }
                                )
                            }
                            is NavigationState.Settings -> {
                                SettingsScreen(
                                    onLogout = {
                                        authViewModel.logout()
                                        navigationState = NavigationState.ContactList
                                    },
                                    onChangeServerIp = {
                                        navigationState = NavigationState.ChangeIpAddress
                                    },
                                    onBack = {
                                        navigationState = NavigationState.ContactList
                                    }
                                )
                            }
                            is NavigationState.ChangeIpAddress -> {
                                ChangeIpAddressScreen(
                                    settingsManager = settingsManager,
                                    onIpAddressChanged = {
                                        ApiClient.updateBaseUrl(settingsManager)
                                        chatViewModel.updateWsManagerDetails(settingsManager)
                                        navigationState = NavigationState.Settings
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // uriToTempFile remains unchanged...
    fun uriToTempFile(context: Context, uri: Uri): File? {
        val contentResolver = context.contentResolver
        var fileName: String? = null
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) fileName = cursor.getString(index)
            }
        }
        if (fileName == null) fileName = "upload_${System.currentTimeMillis()}"
        val tempFile = File(context.cacheDir, fileName!!)
        return try {
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
            tempFile
        } catch (e: Exception) { Log.e("MainActivity", "File error", e); null }
    }
}