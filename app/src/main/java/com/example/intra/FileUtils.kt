package com.example.intra.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import java.io.File
import java.io.FileOutputStream

object FileUtils {

    fun uriToTempFile(context: Context, uri: Uri): File? {
        val resolver = context.contentResolver
        var fileName: String? = null

        resolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) {
                    fileName = cursor.getString(index)
                }
            }
        }

        if (fileName == null) {
            fileName = "upload_${System.currentTimeMillis()}"
        }

        val tempFile = File(context.cacheDir, fileName!!)
        return try {
            resolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
            tempFile
        } catch (e: Exception) {
            Log.e("FileUtils", "File error", e)
            null
        }
    }
}
