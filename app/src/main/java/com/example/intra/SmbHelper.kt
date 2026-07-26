package com.example.intra

import android.content.Context
import android.net.Uri
import android.net.wifi.WifiManager
import android.provider.OpenableColumns
import android.util.Log
import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.Collections
import java.util.Properties

data class SmbAuth(
    val isAnonymous: Boolean,
    val username: String = "",
    val password: String = ""
)

data class SmbFileItem(
    val name: String,
    val isDirectory: Boolean,
    val path: String, // Relative path inside share
    val size: Long
)

object SmbHelper {
    private const val TAG = "SmbHelper"

    // Singleton CIFSContext configured for modern SMB2/3
    private val cifsContext: CIFSContext by lazy {
        val props = Properties().apply {
            setProperty("jcifs.smb.client.minVersion", "SMB202")
            setProperty("jcifs.smb.client.maxVersion", "SMB311")
            setProperty("jcifs.smb.client.dfs.disabled", "true") // Disable DFS for performance
            setProperty("jcifs.resolveOrder", "DNS") // Faster resolution on local LAN
        }
        val config = PropertyConfiguration(props)
        BaseContext(config)
    }

    // Active connection state
    private var currentCifsContext: CIFSContext? = null
    var currentConnectedIp: String? = null
        private set
    var currentConnectedAuth: SmbAuth? = null
        private set

    // Clean connection details
    fun disconnect() {
        currentCifsContext = null
        currentConnectedIp = null
        currentConnectedAuth = null
        Log.d(TAG, "Disconnected from SMB server")
    }

    // Connect to server (validates credentials by trying to list root shares)
    suspend fun connect(ip: String, auth: SmbAuth): Boolean = withContext(Dispatchers.IO) {
        try {
            disconnect()
            Log.d(TAG, "Testing connection to SMB server at $ip (Anonymous: ${auth.isAnonymous})")

            val testContext = if (auth.isAnonymous) {
                cifsContext.withAnonymousCredentials()
            } else {
                val authenticator = NtlmPasswordAuthenticator("", auth.username, auth.password)
                cifsContext.withCredentials(authenticator)
            }
            val serverUrl = "smb://$ip/"

            // Try listing shares to verify credentials
            val smbFile = SmbFile(serverUrl, testContext)
            smbFile.listFiles() // This will throw exception if connection or credentials fail

            currentCifsContext = testContext
            currentConnectedIp = ip
            currentConnectedAuth = auth
            Log.d(TAG, "Successfully connected and authenticated with $ip")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect/authenticate with SMB server at $ip", e)
            disconnect()
            false
        }
    }

    // Get list of non-hidden disk shares
    suspend fun listShares(): List<String> = withContext(Dispatchers.IO) {
        val context = currentCifsContext ?: return@withContext emptyList()
        val ip = currentConnectedIp ?: return@withContext emptyList()
        try {
            val serverUrl = "smb://$ip/"
            val smbFile = SmbFile(serverUrl, context)
            smbFile.listFiles()
                .filter { file ->
                    val name = file.name
                    // Filter out hidden/system shares like C$, IPC$, ADMIN$ and only list disk directories
                    name.endsWith("/") && !name.startsWith("IPC$") && !name.startsWith("ADMIN$") && !name.contains("$")
                }
                .map { file ->
                    // jcifs-ng returns share names with trailing slash, e.g. "Data/"
                    file.name.removeSuffix("/")
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error listing shares for $ip", e)
            emptyList()
        }
    }

    // List contents of a folder inside a share
    // path is relative to the share, e.g. "" for root, "Folder1/Subfolder"
    suspend fun listDirectory(shareName: String, path: String): List<SmbFileItem> = withContext(Dispatchers.IO) {
        val context = currentCifsContext ?: return@withContext emptyList()
        val ip = currentConnectedIp ?: return@withContext emptyList()
        try {
            val formattedPath = if (path.isEmpty()) "" else if (path.endsWith("/")) path else "$path/"
            val folderUrl = "smb://$ip/$shareName/$formattedPath"

            val smbFolder = SmbFile(folderUrl, context)
            smbFolder.listFiles()
                .filter { file ->
                    val name = file.name
                    name != "./" && name != "../" && name != "." && name != ".."
                }
                .map { file ->
                    val cleanName = file.name.removeSuffix("/")
                    val relativePath = if (path.isEmpty()) cleanName else "$path/$cleanName"
                    SmbFileItem(
                        name = cleanName,
                        isDirectory = file.isDirectory,
                        path = relativePath,
                        size = file.length()
                    )
                }
                .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        } catch (e: Exception) {
            Log.e(TAG, "Error listing directory: share=$shareName, path=$path", e)
            emptyList()
        }
    }

    // Download a file with progress callback
    suspend fun downloadFile(
        shareName: String,
        filePath: String,
        destFile: File,
        totalSize: Long,
        onProgress: (Float) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val context = currentCifsContext ?: return@withContext false
        val ip = currentConnectedIp ?: return@withContext false
        try {
            val fileUrl = "smb://$ip/$shareName/$filePath"
            val smbFile = SmbFile(fileUrl, context)

            val inputStream = smbFile.inputStream
            val outputStream = FileOutputStream(destFile)
            val buffer = ByteArray(64 * 1024) // 64KB buffer
            var bytesRead: Int
            var totalRead: Long = 0

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalRead += bytesRead
                if (totalSize > 0) {
                    onProgress(totalRead.toFloat() / totalSize)
                }
            }
            outputStream.flush()
            outputStream.close()
            inputStream.close()
            Log.d(TAG, "File downloaded successfully from SMB: ${destFile.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading SMB file: $filePath", e)
            try { destFile.delete() } catch (ex: Exception) {}
            false
        }
    }

    // Create a new folder on the SMB share
    suspend fun createFolder(shareName: String, parentPath: String, folderName: String): Boolean = withContext(Dispatchers.IO) {
        val context = currentCifsContext ?: return@withContext false
        val ip = currentConnectedIp ?: return@withContext false
        try {
            val formattedParent = if (parentPath.isEmpty()) "" else if (parentPath.endsWith("/")) parentPath else "$parentPath/"
            val folderUrl = "smb://$ip/$shareName/$formattedParent$folderName/"
            val smbFolder = SmbFile(folderUrl, context)
            if (!smbFolder.exists()) {
                smbFolder.mkdir()
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error creating SMB folder: $folderName in $parentPath", e)
            false
        }
    }

    // Upload a file from a local Android Uri to the SMB share
    suspend fun uploadFile(
        shareName: String,
        targetPath: String,
        fileName: String,
        context: Context,
        fileUri: Uri,
        onProgress: (Float) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val cifsCtx = currentCifsContext ?: return@withContext false
        val ip = currentConnectedIp ?: return@withContext false
        try {
            val formattedPath = if (targetPath.isEmpty()) "" else if (targetPath.endsWith("/")) targetPath else "$targetPath/"
            val fileUrl = "smb://$ip/$shareName/$formattedPath$fileName"
            val smbFile = SmbFile(fileUrl, cifsCtx)

            val inputStream = context.contentResolver.openInputStream(fileUri) ?: return@withContext false
            val totalSize = context.contentResolver.query(fileUri, null, null, null, null)?.use { cursor ->
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex >= 0 && cursor.moveToFirst()) {
                    cursor.getLong(sizeIndex)
                } else {
                    -1L
                }
            } ?: -1L

            val outputStream = smbFile.outputStream
            val buffer = ByteArray(64 * 1024)
            var bytesRead: Int
            var totalWritten: Long = 0

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalWritten += bytesRead
                if (totalSize > 0) {
                    onProgress(totalWritten.toFloat() / totalSize)
                }
            }
            outputStream.flush()
            outputStream.close()
            inputStream.close()
            Log.d(TAG, "File uploaded successfully to SMB: $fileUrl")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading file to SMB: $fileName", e)
            false
        }
    }

    // Network Scanner Logic
    fun getLocalIpAddress(context: Context): String? {
        try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val connectionInfo = wm.connectionInfo
            val ipAddress = connectionInfo.ipAddress
            if (ipAddress != 0) {
                return String.format(
                    "%d.%d.%d.%d",
                    ipAddress and 0xff,
                    ipAddress shr 8 and 0xff,
                    ipAddress shr 16 and 0xff,
                    ipAddress shr 24 and 0xff
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting IP via WifiManager", e)
        }

        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress) {
                        val sAddr = addr.hostAddress
                        val isIPv4 = sAddr.indexOf(':') < 0
                        if (isIPv4) return sAddr
                    }
                }
            }
        } catch (ex: Exception) {
            Log.e(TAG, "Error getting IP via NetworkInterface", ex)
        }
        return null
    }

    private suspend fun isSmbPortOpen(ip: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val socket = Socket()
            socket.connect(InetSocketAddress(ip, 445), 180) // 180ms timeout
            socket.close()
            Log.d(TAG, "Found active SMB port on IP: $ip")
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun scanSubnetForSmb(context: Context): List<String> = coroutineScope {
        val localIp = getLocalIpAddress(context) ?: return@coroutineScope emptyList()
        Log.d(TAG, "Local IP detected: $localIp")
        val parts = localIp.split(".")
        if (parts.size != 4) return@coroutineScope emptyList()

        val subnet = "${parts[0]}.${parts[1]}.${parts[2]}"

        val deferreds = (1..254).map { host ->
            async(Dispatchers.IO) {
                val ip = "$subnet.$host"
                if (ip == localIp) return@async null
                if (isSmbPortOpen(ip)) ip else null
            }
        }
        deferreds.awaitAll().filterNotNull()
    }
}
