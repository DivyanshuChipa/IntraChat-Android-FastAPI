package com.example.intraserver.server

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receiveMultipart
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 🚀 WebDropRoutes.kt - Modular Zero-Friction LAN Share Endpoints for IntraServer (Android)
 *
 * Parity with Python backend/webdrop.py:
 * - Direct Drag & Drop file transfers without account/login
 * - Instant LAN Clipboard (Text/Link drop)
 * - Auto-cleanup for files older than 10 days
 */
fun Route.webDropRoutes(
    uploadsDir: File,
    serveWebAsset: suspend (ApplicationCall, String) -> Unit
) {
    val dropDir = File(uploadsDir, "webdrop").apply { mkdirs() }
    val sharedTexts = CopyOnWriteArrayList<JSONObject>()

    fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f KB", bytes / 1024f)
            bytes < 1024 * 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB", bytes / (1024f * 1024f))
            else -> String.format(java.util.Locale.US, "%.2f GB", bytes / (1024f * 1024f * 1024f))
        }
    }

    fun getFileCategory(filename: String): String {
        val ext = filename.substringAfterLast(".", "").lowercase()
        return when (ext) {
            "jpg", "jpeg", "png", "webp", "gif", "svg", "bmp" -> "image"
            "mp4", "mkv", "avi", "mov", "webm", "3gp" -> "video"
            "mp3", "wav", "m4a", "ogg", "aac", "flac" -> "audio"
            "pdf", "doc", "docx", "txt", "xlsx", "pptx", "csv" -> "document"
            "zip", "rar", "7z", "tar", "gz" -> "archive"
            else -> "other"
        }
    }

    // 1. WebDrop Web Page
    get("/drop") {
        serveWebAsset(call, "drop.html")
    }

    get("/drop.html") {
        serveWebAsset(call, "drop.html")
    }

    // 2. List all dropped files
    get("/api/drop/files") {
        val fileArray = JSONArray()
        val allFiles = dropDir.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() } ?: emptyList()

        for (f in allFiles) {
            val obj = JSONObject().apply {
                put("name", f.name)
                put("size", f.length())
                put("formatted_size", formatSize(f.length()))
                put("url", "/uploads/webdrop/${f.name}")
                put("uploaded_at", f.lastModified())
                put("category", getFileCategory(f.name))
            }
            fileArray.put(obj)
        }

        val res = JSONObject().apply {
            put("success", true)
            put("count", allFiles.size)
            put("files", fileArray)
        }
        call.respondText(res.toString(), ContentType.Application.Json, HttpStatusCode.OK)
    }

    // 3. Multi-file upload stream
    post("/api/drop/upload") {
        val multipart = call.receiveMultipart()
        val savedList = JSONArray()

        multipart.forEachPart { part ->
            if (part is PartData.FileItem) {
                val rawName = part.originalFileName?.trim()?.removeSurrounding("\"")
                    ?.substringAfterLast("/")?.substringAfterLast("\\")
                    ?: "drop_${System.currentTimeMillis()}.bin"

                var dest = File(dropDir, rawName)
                var finalName = rawName

                // Avoid overwriting by appending timestamp if collision
                if (dest.exists()) {
                    val namePart = rawName.substringBeforeLast(".", rawName)
                    val extPart = rawName.substringAfterLast(".", "")
                    finalName = if (extPart.isNotEmpty() && extPart != rawName) {
                        "${namePart}_${System.currentTimeMillis()}.$extPart"
                    } else {
                        "${rawName}_${System.currentTimeMillis()}"
                    }
                    dest = File(dropDir, finalName)
                }

                part.streamProvider().use { input ->
                    FileOutputStream(dest).use { output ->
                        input.copyTo(output)
                    }
                }

                val savedObj = JSONObject().apply {
                    put("name", finalName)
                    put("size", dest.length())
                    put("formatted_size", formatSize(dest.length()))
                    put("url", "/uploads/webdrop/$finalName")
                    put("category", getFileCategory(finalName))
                }
                savedList.put(savedObj)
                ConnectionManager.log("🚀 [WebDrop] File received: $finalName (${formatSize(dest.length())})")
            }
            part.dispose()
        }

        val res = JSONObject().apply {
            put("success", true)
            put("message", "${savedList.length()} file(s) dropped successfully!")
            put("files", savedList)
        }
        call.respondText(res.toString(), ContentType.Application.Json, HttpStatusCode.OK)
    }

    // 4. Delete file
    delete("/api/drop/file/{filename}") {
        val filename = call.parameters["filename"]?.trim() ?: ""
        val target = File(dropDir, filename)
        if (target.exists() && target.isFile) {
            target.delete()
            ConnectionManager.log("🗑️ [WebDrop] File deleted: $filename")
            val res = JSONObject().apply {
                put("success", true)
                put("message", "Deleted $filename")
            }
            call.respondText(res.toString(), ContentType.Application.Json, HttpStatusCode.OK)
        } else {
            val res = JSONObject().apply {
                put("success", false)
                put("message", "File not found")
            }
            call.respondText(res.toString(), ContentType.Application.Json, HttpStatusCode.NotFound)
        }
    }

    // 5. In-memory LAN Clipboard
    get("/api/drop/texts") {
        val arr = JSONArray()
        sharedTexts.forEach { arr.put(it) }
        val res = JSONObject().apply {
            put("success", true)
            put("texts", arr)
        }
        call.respondText(res.toString(), ContentType.Application.Json, HttpStatusCode.OK)
    }

    post("/api/drop/text") {
        val raw = call.receiveText()
        val json = try { JSONObject(raw) } catch (_: Exception) { JSONObject() }
        val text = json.optString("text", "").trim()
        val sender = json.optString("sender", "Guest")

        if (text.isNotEmpty()) {
            val entry = JSONObject().apply {
                put("id", System.currentTimeMillis())
                put("text", text)
                put("sender", sender)
                put("timestamp", System.currentTimeMillis())
            }
            sharedTexts.add(0, entry)
            if (sharedTexts.size > 20) {
                sharedTexts.removeAt(sharedTexts.size - 1)
            }
            ConnectionManager.log("📋 [WebDrop] Shared text snippet from $sender")
            val res = JSONObject().apply {
                put("success", true)
                put("entry", entry)
            }
            call.respondText(res.toString(), ContentType.Application.Json, HttpStatusCode.OK)
        } else {
            val res = JSONObject().apply {
                put("success", false)
                put("message", "Text cannot be empty")
            }
            call.respondText(res.toString(), ContentType.Application.Json, HttpStatusCode.BadRequest)
        }
    }
}

/**
 * 🧹 Auto-delete WebDrop files older than [days] days
 */
fun cleanupOldWebDropFiles(uploadsDir: File, days: Int = 10): Int {
    val dropDir = File(uploadsDir, "webdrop")
    if (!dropDir.exists()) return 0

    val maxAgeMs = days * 24L * 3600L * 1000L
    val now = System.currentTimeMillis()
    var deletedCount = 0

    dropDir.listFiles()?.forEach { file ->
        if (file.isFile && (now - file.lastModified()) > maxAgeMs) {
            if (file.delete()) {
                deletedCount++
                ConnectionManager.log("🧹 [WebDrop] Auto-deleted expired file: ${file.name}")
            }
        }
    }
    return deletedCount
}
