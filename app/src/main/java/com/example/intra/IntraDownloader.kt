package com.example.intra

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

object IntraDownloader {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .build()

    fun downloadMedia(context: Context, url: String, isVideo: Boolean = false) {
        Toast.makeText(
            context,
            if (isVideo) "Downloading video..." else "Downloading image...",
            Toast.LENGTH_SHORT
        ).show()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val cleanUrl = url.substringBefore('?')
                val extension = if (isVideo) {
                    cleanUrl.substringAfterLast('.', "mp4")
                } else {
                    cleanUrl.substringAfterLast('.', "jpg")
                }

                val prefix = if (isVideo) "Intra_Video_" else "Intra_Image_"
                val fileName = "$prefix${System.currentTimeMillis()}.$extension"

                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()

                if (!response.isSuccessful || response.body == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            "Download failed (Server ${response.code})",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    return@launch
                }

                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val intraDir = File(downloadsDir, "Intra")
                if (!intraDir.exists()) {
                    intraDir.mkdirs()
                }

                val destFile = File(intraDir, fileName)
                destFile.outputStream().use { output ->
                    response.body!!.byteStream().use { input ->
                        input.copyTo(output)
                    }
                }

                // Index in Gallery / Files app immediately
                val mimeType = if (isVideo) "video/*" else "image/*"
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(destFile.absolutePath),
                    arrayOf(mimeType),
                    null
                )

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "Saved to Downloads/Intra/$fileName",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "Download failed: ${e.localizedMessage ?: "Unknown error"}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
}
