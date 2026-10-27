package com.example.intra

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 🗄️ WebDavHelper - Modular WebDAV / IntraDrive Client for IntraChat
 *
 * Provides connection, file listing, upload, download, folder creation,
 * and deletion over standard HTTP / WebDAV protocols without third-party heavy dependencies.
 */
object WebDavHelper {
    private const val TAG = "WebDavHelper"

    var currentConnectedHost: String? = null
        private set
    var currentConnectedPort: Int = 8000
        private set
    var currentConnectedAuth: SmbAuth? = null
        private set

    fun getBaseUrl(): String {
        val host = currentConnectedHost ?: "127.0.0.1"
        return "http://$host:$currentConnectedPort/drive"
    }

    fun disconnect() {
        currentConnectedHost = null
        currentConnectedPort = 8000
        currentConnectedAuth = null
        Log.d(TAG, "Disconnected from WebDAV server")
    }

    /**
     * Connects to a WebDAV / IntraDrive server by testing a quick GET / PROPFIND request.
     */
    suspend fun connect(host: String, port: Int, auth: SmbAuth): Boolean = withContext(Dispatchers.IO) {
        try {
            disconnect()
            Log.d(TAG, "Testing connection to WebDAV at http://$host:$port/drive")
            val url = URL("http://$host:$port/drive?format=json")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 5000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                if (!auth.isAnonymous && auth.username.isNotEmpty()) {
                    val userPass = "${auth.username}:${auth.password}"
                    val encoded = Base64.encodeToString(userPass.toByteArray(), Base64.NO_WRAP)
                    setRequestProperty("Authorization", "Basic $encoded")
                }
            }

            val code = conn.responseCode
            conn.disconnect()

            if (code in 200..299 || code == 207) {
                currentConnectedHost = host
                currentConnectedPort = port
                currentConnectedAuth = auth
                Log.d(TAG, "Successfully connected to WebDAV server at $host:$port")
                true
            } else {
                Log.e(TAG, "WebDAV connection returned HTTP code: $code")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "WebDAV connection failed: ${e.message}", e)
            disconnect()
            false
        }
    }

    /**
     * Lists contents of a directory in WebDAV.
     * Path is relative, e.g. "" for root, "Folder/Subfolder".
     */
    suspend fun listDirectory(path: String): List<SmbFileItem> = withContext(Dispatchers.IO) {
        val host = currentConnectedHost ?: return@withContext emptyList()
        val port = currentConnectedPort
        val auth = currentConnectedAuth ?: SmbAuth(true)

        val cleanPath = path.trim().trimStart('/').trimEnd('/')
        val encodedPath = if (cleanPath.isEmpty()) "" else "/" + cleanPath.split("/").joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
        val targetUrl = "http://$host:$port/drive$encodedPath?format=json"

        try {
            val url = URL(targetUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                if (!auth.isAnonymous && auth.username.isNotEmpty()) {
                    val userPass = "${auth.username}:${auth.password}"
                    val encoded = Base64.encodeToString(userPass.toByteArray(), Base64.NO_WRAP)
                    setRequestProperty("Authorization", "Basic $encoded")
                }
            }

            if (conn.responseCode in 200..299) {
                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()

                val jsonArray = JSONArray(jsonStr)
                val items = mutableListOf<SmbFileItem>()

                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val name = obj.getString("name")
                    val isDir = obj.getBoolean("isDirectory")
                    val size = obj.optLong("size", 0L)
                    val relativePath = if (cleanPath.isEmpty()) name else "$cleanPath/$name"

                    items.add(
                        SmbFileItem(
                            name = name,
                            isDirectory = isDir,
                            path = relativePath,
                            size = size
                        )
                    )
                }

                items.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            } else {
                Log.e(TAG, "WebDAV listDirectory HTTP ${conn.responseCode}")
                conn.disconnect()
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error listing WebDAV directory at $path", e)
            emptyList()
        }
    }

    /**
     * Creates a new folder on the WebDAV server (MKCOL).
     */
    suspend fun createFolder(parentPath: String, folderName: String): Boolean = withContext(Dispatchers.IO) {
        val host = currentConnectedHost ?: return@withContext false
        val port = currentConnectedPort
        val auth = currentConnectedAuth ?: SmbAuth(true)

        val cleanParent = parentPath.trim().trimStart('/').trimEnd('/')
        val combined = if (cleanParent.isEmpty()) folderName else "$cleanParent/$folderName"
        val encodedPath = "/" + combined.split("/").joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
        val targetUrl = "http://$host:$port/drive$encodedPath"

        try {
            val url = URL(targetUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                requestMethod = "MKCOL"
                if (!auth.isAnonymous && auth.username.isNotEmpty()) {
                    val userPass = "${auth.username}:${auth.password}"
                    val encoded = Base64.encodeToString(userPass.toByteArray(), Base64.NO_WRAP)
                    setRequestProperty("Authorization", "Basic $encoded")
                }
            }

            val code = conn.responseCode
            conn.disconnect()
            code == 201 || code == 200
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create folder $folderName on WebDAV", e)
            false
        }
    }

    /**
     * Uploads a file to WebDAV server (PUT).
     */
    suspend fun uploadFile(
        targetPath: String,
        fileName: String,
        context: Context,
        fileUri: Uri,
        onProgress: (Float) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val host = currentConnectedHost ?: return@withContext false
        val port = currentConnectedPort
        val auth = currentConnectedAuth ?: SmbAuth(true)

        val cleanPath = targetPath.trim().trimStart('/').trimEnd('/')
        val combined = if (cleanPath.isEmpty()) fileName else "$cleanPath/$fileName"
        val encodedPath = "/" + combined.split("/").joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
        val targetUrl = "http://$host:$port/drive$encodedPath"

        var input: InputStream? = null
        var output: OutputStream? = null

        try {
            var totalBytes = 0L
            context.contentResolver.query(fileUri, null, null, null, null)?.use { cursor ->
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex >= 0 && cursor.moveToFirst()) {
                    totalBytes = cursor.getLong(sizeIndex)
                }
            }

            input = context.contentResolver.openInputStream(fileUri) ?: return@withContext false

            val url = URL(targetUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 60000
                requestMethod = "PUT"
                doOutput = true
                if (totalBytes > 0) setFixedLengthStreamingMode(totalBytes) else setChunkedStreamingMode(8192)
                if (!auth.isAnonymous && auth.username.isNotEmpty()) {
                    val userPass = "${auth.username}:${auth.password}"
                    val encoded = Base64.encodeToString(userPass.toByteArray(), Base64.NO_WRAP)
                    setRequestProperty("Authorization", "Basic $encoded")
                }
            }

            output = conn.outputStream
            val buffer = ByteArray(16384)
            var bytesRead: Int
            var totalTransferred = 0L

            while (input.read(buffer).also { bytesRead = it } != -1) {
                output.write(buffer, 0, bytesRead)
                totalTransferred += bytesRead
                if (totalBytes > 0) {
                    val progress = (totalTransferred.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                    withContext(Dispatchers.Main) { onProgress(progress) }
                }
            }
            output.flush()

            val code = conn.responseCode
            conn.disconnect()
            code in 200..299
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading to WebDAV: ${e.message}", e)
            false
        } finally {
            try { input?.close() } catch (_: Exception) {}
            try { output?.close() } catch (_: Exception) {}
        }
    }

    /**
     * Downloads a file from WebDAV server.
     */
    suspend fun downloadFile(
        filePath: String,
        destFile: File,
        onProgress: (Float) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val host = currentConnectedHost ?: return@withContext false
        val port = currentConnectedPort
        val auth = currentConnectedAuth ?: SmbAuth(true)

        val cleanPath = filePath.trim().trimStart('/')
        val encodedPath = "/" + cleanPath.split("/").joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
        val targetUrl = "http://$host:$port/drive$encodedPath"

        try {
            val url = URL(targetUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000
                readTimeout = 60000
                requestMethod = "GET"
                if (!auth.isAnonymous && auth.username.isNotEmpty()) {
                    val userPass = "${auth.username}:${auth.password}"
                    val encoded = Base64.encodeToString(userPass.toByteArray(), Base64.NO_WRAP)
                    setRequestProperty("Authorization", "Basic $encoded")
                }
            }

            if (conn.responseCode !in 200..299) {
                conn.disconnect()
                return@withContext false
            }

            val totalBytes = conn.contentLength.toLong()
            destFile.parentFile?.mkdirs()

            conn.inputStream.use { input ->
                FileOutputStream(destFile).use { output ->
                    val buffer = ByteArray(16384)
                    var bytesRead: Int
                    var totalTransferred = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalTransferred += bytesRead
                        if (totalBytes > 0) {
                            val progress = (totalTransferred.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                            withContext(Dispatchers.Main) { onProgress(progress) }
                        }
                    }
                    output.flush()
                }
            }
            conn.disconnect()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading from WebDAV: ${e.message}", e)
            false
        }
    }
}
