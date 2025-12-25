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

        setContent {
            IntraTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {

                    val authViewModel: AuthViewModel = viewModel()
                    // ChatViewModel is scoped to MainActivity, so it stays alive
                    val chatViewModel: ChatViewModel = viewModel(factory = chatViewModelFactory)

                    val isAuthenticated by authViewModel.isAuthenticated
                    var currentChatReceiver by remember { mutableStateOf<String?>(null) }

                    if (!isAuthenticated) {
                        // 1. LOGIN SCREEN
                        AuthScreen(
                            viewModel = authViewModel,
                            onAuthenticated = { /* AuthVM state will update isAuthenticated */ }
                        )
                    } else {
                        // User is logged in
                        if (currentChatReceiver == null) {

                            // 2. CONTACT LIST SCREEN (Home)
                            ContactListScreen(
                                username = chatViewModel.currentUsername,
                                // 🔥 FIX: Pass live typing statuses map here
                                typingStatuses = chatViewModel.typingStatuses,
                                onChatClick = { selectedUser ->
                                    currentChatReceiver = selectedUser
                                },
                                onLogout = {
                                    authViewModel.logout()
                                    currentChatReceiver = null
                                }
                            )
                        } else {
                            // 3. CHAT SCREEN (Conversation)
                            ChatScreen(
                                viewModel = chatViewModel,
                                receiverName = currentChatReceiver!!,
                                onAttachClick = {
                                    currentUploadViewModel = chatViewModel
                                    currentUploadReceiver = currentChatReceiver
                                    filePickerLauncher.launch("*/*")
                                },
                                onBackClick = {
                                    chatViewModel.closeChat()
                                    currentChatReceiver = null
                                }
                            )
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