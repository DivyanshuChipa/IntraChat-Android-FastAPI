package com.example.intraserver.server

import android.content.Context
import com.example.intraserver.db.ServerDatabase
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.request.receiveMultipart
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondFile
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

class IntraServerEngine(private val context: Context, private val port: Int = 8000) {

    private var engine: CIOApplicationEngine? = null
    private val db = ServerDatabase.getInstance(context)
    // Uploads main folder (chat files, voice notes, etc.)
    private val uploadsDir = File(context.filesDir, "uploads").apply { mkdirs() }
    // Profiles sub-folder (user avatars 256x256 PNGs)
    private val profilesDir = File(uploadsDir, "profiles").apply { mkdirs() }

    @Volatile
    var isRunning = false
        private set

    fun start(onStarted: () -> Unit = {}, onError: (Throwable) -> Unit = {}) {
        if (isRunning) return

        try {
            engine = embeddedServer(CIO, port = port, host = "0.0.0.0") {
                // Configure CORS
                install(CORS) {
                    anyHost()
                    allowHeader(HttpHeaders.ContentType)
                    allowHeader(HttpHeaders.Authorization)
                    allowHeader("X-Admin-Key")
                    allowMethod(HttpMethod.Options)
                    allowMethod(HttpMethod.Put)
                    allowMethod(HttpMethod.Patch)
                    allowMethod(HttpMethod.Delete)
                }

                // Configure WebSockets
                install(WebSockets) {
                    pingPeriodMillis = 15000L
                    timeoutMillis = 30000L
                    maxFrameSize = Long.MAX_VALUE
                    masking = false
                }

                // Routing
                routing {
                    // 1. Health check & Discovery
                    get("/api/ping") {
                        val json = JSONObject().apply {
                            put("status", "ok")
                            put("app", "intra_chat")
                            put("name", "Intra Server (Android)")
                        }
                        call.respondText(json.toString(), ContentType.Application.Json)
                    }

                    // 2. Auth: Register
                    post("/register") {
                        val body = call.receiveText()
                        val req = try { JSONObject(body) } catch (e: Exception) { null }
                        val username = req?.optString("username", "")?.trim() ?: ""
                        val password = req?.optString("password", "") ?: ""

                        if (username.isEmpty() || password.isEmpty()) {
                            val res = JSONObject().apply {
                                put("success", false)
                                put("message", "Username and password required")
                            }
                            call.respondText(res.toString(), ContentType.Application.Json, HttpStatusCode.BadRequest)
                            return@post
                        }

                        val (success, message) = db.registerUser(username, password)
                        val res = JSONObject().apply {
                            put("success", success)
                            put("message", message)
                        }

                        if (success) {
                            ConnectionManager.log("✨ Registered new user: '$username'")
                            call.respondText(res.toString(), ContentType.Application.Json, HttpStatusCode.Created)
                        } else {
                            call.respondText(res.toString(), ContentType.Application.Json, HttpStatusCode.Conflict)
                        }
                    }

                    // 3. Auth: Login
                    post("/login") {
                        val body = call.receiveText()
                        val req = try { JSONObject(body) } catch (e: Exception) { null }
                        val username = req?.optString("username", "")?.trim() ?: ""
                        val password = req?.optString("password", "") ?: ""

                        if (username.isEmpty() || password.isEmpty()) {
                            val res = JSONObject().apply {
                                put("success", false)
                                put("message", "Username and password required")
                            }
                            call.respondText(res.toString(), ContentType.Application.Json, HttpStatusCode.BadRequest)
                            return@post
                        }

                        val status = db.verifyUser(username, password)
                        when (status) {
                            "OK" -> {
                                val token = "intra_token_${UUID.randomUUID()}"
                                val res = JSONObject().apply {
                                    put("success", true)
                                    put("token", token)
                                    put("username", username)
                                }
                                ConnectionManager.log("🔑 User '$username' logged in successfully")
                                call.respondText(res.toString(), ContentType.Application.Json, HttpStatusCode.OK)
                            }
                            "PENDING" -> {
                                val res = JSONObject().apply {
                                    put("success", false)
                                    put("message", "Account pending approval from Admin.")
                                }
                                call.respondText(res.toString(), ContentType.Application.Json, HttpStatusCode.Forbidden)
                            }
                            else -> {
                                val res = JSONObject().apply {
                                    put("success", false)
                                    put("message", "Invalid credentials")
                                }
                                call.respondText(res.toString(), ContentType.Application.Json, HttpStatusCode.Unauthorized)
                            }
                        }
                    }

                    // 4. Users list
                    get("/users") {
                        val users = db.getAllUsers()
                        val usersArray = JSONArray()

                        for ((index, u) in users.withIndex()) {
                            val userObj = JSONObject().apply {
                                put("id", index + 1)
                                put("username", u.username)
                                put("profile_photo", u.profilePhoto)
                                put("created_at", u.createdAt)
                                put("status", u.status)
                                put("is_online", ConnectionManager.isUserOnline(u.username))
                            }
                            usersArray.put(userObj)
                        }

                        val res = JSONObject().apply {
                            put("success", true)
                            put("users", usersArray)
                        }
                        call.respondText(res.toString(), ContentType.Application.Json, HttpStatusCode.OK)
                    }

                    // 5. Chat History
                    get("/messages") {
                        val messages = db.getRecentMessages(limit = 100)
                        val msgsArray = JSONArray()

                        for (m in messages) {
                            val msgObj = JSONObject().apply {
                                put("id", m.id)
                                put("sender", m.sender)
                                put("receiver", m.receiver)
                                put("content", m.content)
                                put("media_url", m.mediaUrl)
                                put("timestamp", m.timestamp)
                                put("is_read", m.isRead)
                            }
                            msgsArray.put(msgObj)
                        }
                        call.respondText(msgsArray.toString(), ContentType.Application.Json, HttpStatusCode.OK)
                    }

                    // ==============================================================
                    // 6. CHAT FILE & MEDIA UPLOAD (FastAPI "files.py" ka Desi Kotlin Bhai)
                    // ==============================================================
                    // Ye route chat me bheje jane wale attachments (Photos, Videos, Audio Voice Notes, Documents) ko receive karta hai:
                    // 1. Multipart stream se file ko 'filesDir/uploads/' folder me directly save karta hai.
                    // 2. Client ChatViewModel.kt "url" aur "filename" expect karta hai.
                    // 3. Response me "url", "file_url", "filename" aur "size" return karta hai.
                    post("/upload") {
                        val multipart = call.receiveMultipart()
                        var savedFileName: String? = null
                        var savedFileSize = 0L

                        // Stream me aane wale parts ko read karo
                        multipart.forEachPart { part ->
                            if (part is PartData.FileItem) {
                                val originalName = part.originalFileName ?: "file_${System.currentTimeMillis()}"
                                val ext = originalName.substringAfterLast(".", "")
                                // Unique filename banate hain taaki file name collisions na ho
                                val uniqueName = "${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.${if (ext.isNotEmpty()) ext else "bin"}"
                                val destFile = File(uploadsDir, uniqueName)

                                // Direct Stream copy: Low RAM use karta hai aur badi videos/audios ko bina freeze hue save karta hai
                                part.streamProvider().use { input ->
                                    FileOutputStream(destFile).use { output ->
                                        input.copyTo(output)
                                    }
                                }
                                savedFileName = uniqueName
                                savedFileSize = destFile.length()
                                ConnectionManager.log("📁 File uploaded: $uniqueName (${savedFileSize / 1024} KB)")
                            }
                            part.dispose() // Stream cleanup
                        }

                        // Response bhejo
                        if (savedFileName != null) {
                            val relativeUrl = "/uploads/$savedFileName"
                            val res = JSONObject().apply {
                                put("success", true)
                                put("url", relativeUrl)         // Client ChatViewModel.kt isko padhta hai
                                put("file_url", relativeUrl)    // Backward compatibility
                                put("filename", savedFileName)  // File ka naam
                                put("size", savedFileSize)      // File size in bytes
                            }
                            call.respondText(res.toString(), ContentType.Application.Json, HttpStatusCode.OK)
                        } else {
                            val res = JSONObject().apply {
                                put("success", false)
                                put("message", "No file uploaded or file was empty")
                            }
                            call.respondText(res.toString(), ContentType.Application.Json, HttpStatusCode.BadRequest)
                        }
                    }

                    // ==============================================================
                    // 7. PROFILE PHOTO UPLOAD (FastAPI "profiles.py" ka Desi Kotlin Bhai)
                    // ==============================================================
                    // Ye function client se aane wali profile photo ko handle karta hai:
                    // 1. Multipart form-data se photo aur username receive karta hai.
                    // 2. Android ke native Skia Engine (Bitmap) se photo ko 1:1 center-crop karta hai (Pillow ke ImageOps.fit jaisa).
                    // 3. Exactly 256x256 me smooth resize karke 'uploads/profiles/<username>.png' me save karta hai.
                    // 4. SQLite database me user ka photo path update karta hai.
                    suspend fun handleProfileUpload(call: io.ktor.server.application.ApplicationCall) {
                        val multipart = call.receiveMultipart()
                        var targetUsername: String? = null
                        var tempSavedFile: File? = null

                        // Form ke har part ko check karo (Text ya File)
                        multipart.forEachPart { part ->
                            when (part) {
                                is PartData.FormItem -> {
                                    // Username field pakdo
                                    if (part.name == "username") {
                                        targetUsername = part.value.trim()
                                    }
                                }
                                is PartData.FileItem -> {
                                    // Pehle temporary file me image process karke save kar lo
                                    val tempFile = File(profilesDir, "temp_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.png")
                                    val success = part.streamProvider().use { input ->
                                        processAndSaveAvatar(input, tempFile)
                                    }
                                    if (success) {
                                        tempSavedFile = tempFile
                                    }
                                }
                                else -> {}
                            }
                            part.dispose() // Memory leak se bachne ke liye dispose karo
                        }

                        // Agar username aur processed file dono mil gaye
                        if (tempSavedFile != null && !targetUsername.isNullOrEmpty()) {
                            // Standard filename format: <username>.png (same as Python profiles.py)
                            val finalFile = File(profilesDir, "${targetUsername}.png")
                            if (finalFile.exists()) {
                                finalFile.delete() // Purani photo replace kar do
                            }
                            tempSavedFile?.renameTo(finalFile)

                            // Database me store hone wala relative URL
                            val dbUrl = "/uploads/profiles/${finalFile.name}"

                            // 💾 SQLite database me user ka profile_photo update karo
                            db.updateProfilePhoto(targetUsername!!, dbUrl)
                            ConnectionManager.log("🖼️ Successfully updated 256x256 avatar for '$targetUsername'")

                            // Client ko response bhejo: dono keys support karega (profile_photo aur photo_url)
                            val res = JSONObject().apply {
                                put("success", true)
                                put("profile_photo", dbUrl)
                                put("photo_url", dbUrl)
                            }
                            call.respondText(res.toString(), ContentType.Application.Json, HttpStatusCode.OK)
                        } else {
                            tempSavedFile?.delete()
                            val res = JSONObject().apply {
                                put("success", false)
                                put("message", "Profile upload failed: username or image missing")
                            }
                            call.respondText(res.toString(), ContentType.Application.Json, HttpStatusCode.BadRequest)
                        }
                    }

                    // 📸 Client Primary endpoint (Android app ApiService.kt)
                    post("/profile/upload_profile") {
                        handleProfileUpload(call)
                    }

                    // 📸 Alias endpoint (Backward compatibility)
                    post("/profile/upload_photo") {
                        handleProfileUpload(call)
                    }

                    // ==============================================================
                    // 8. STATIC FILE SERVING (/uploads/{path...})
                    // ==============================================================
                    // Yeh route single file (/uploads/abc.jpg) aur nested sub-folders
                    // (/uploads/profiles/rahul.png) dono ko full speed par stream karta hai!
                    get("/uploads/{path...}") {
                        val pathSegments = call.parameters.getAll("path") ?: emptyList()
                        val relativePath = pathSegments.joinToString(File.separator)
                        val file = File(uploadsDir, relativePath)

                        if (file.exists() && file.isFile) {
                            call.respondFile(file)
                        } else {
                            call.respondText("File not found", status = HttpStatusCode.NotFound)
                        }
                    }

                    // ==============================================================
                    // 9. EMBEDDED WEB CLIENT (Laptop/PC Browser Support)
                    // ==============================================================
                    // Ye helper function Android app ke 'assets/web/' folder se web files serve karta hai:
                    // Laptop ya desktop par bina kisi app ke seedha browser se chat chalegi!
                    suspend fun serveWebAsset(call: io.ktor.server.application.ApplicationCall, path: String) {
                        val assetPath = "web/$path"
                        try {
                            val bytes = context.assets.open(assetPath).use { it.readBytes() }
                            val contentType = when {
                                path.endsWith(".html") -> ContentType.Text.Html
                                path.endsWith(".css") -> ContentType.Text.CSS
                                path.endsWith(".js") -> ContentType.Application.JavaScript
                                path.endsWith(".svg") -> ContentType.Image.SVG
                                path.endsWith(".png") -> ContentType.Image.PNG
                                path.endsWith(".jpg") || path.endsWith(".jpeg") -> ContentType.Image.JPEG
                                path.endsWith(".json") -> ContentType.Application.Json
                                else -> ContentType.Application.OctetStream
                            }
                            call.respondBytes(bytes, contentType)
                        } catch (_: Exception) {
                            call.respondText("404: Not Found", status = HttpStatusCode.NotFound)
                        }
                    }

                    // Root: Laptop browser me http://<ip>:8000/ kholte hi Login page load hoga!
                    get("/") {
                        serveWebAsset(call, "index.html")
                    }

                    get("/index.html") {
                        serveWebAsset(call, "index.html")
                    }

                    // Main chat room page
                    get("/chat.html") {
                        serveWebAsset(call, "chat.html")
                    }

                    // Web Client logic script
                    get("/app.js") {
                        serveWebAsset(call, "app.js")
                    }

                    // Web Client stylesheet
                    get("/style.css") {
                        serveWebAsset(call, "style.css")
                    }

                    // Web icons & SVGs (assets/family_group.svg, assets/lumir5.svg, etc.)
                    get("/assets/{path...}") {
                        val pathSegments = call.parameters.getAll("path") ?: emptyList()
                        val assetSubPath = "assets/" + pathSegments.joinToString("/")
                        serveWebAsset(call, assetSubPath)
                    }

                    // 10. WebSockets: Real-time Chat, Presence, and WebRTC Calling
                    suspend fun io.ktor.server.websocket.DefaultWebSocketServerSession.handleClientSession(username: String) {
                        ConnectionManager.connect(username, this)

                        // Send connection status confirmation immediately
                        send(Frame.Text(JSONObject().apply {
                            put("type", "status")
                            put("text", "Connected")
                            put("user", username)
                        }.toString()))

                        // Deliver pending offline messages
                        try {
                            val pending = db.getUndeliveredMessages(username)
                            if (pending.isNotEmpty()) {
                                ConnectionManager.log("📬 Delivering ${pending.size} offline messages to '$username'")
                                for (msg in pending) {
                                    val offlineData = JSONObject().apply {
                                        put("type", if (!msg.mediaUrl.isNullOrEmpty()) "file" else "text")
                                        put("text", msg.content)
                                        put("sender", msg.sender)
                                        put("receiver", msg.receiver)
                                        put("timestamp", msg.timestamp.toLongOrNull() ?: System.currentTimeMillis())
                                        if (!msg.mediaUrl.isNullOrEmpty()) {
                                            put("url", msg.mediaUrl)
                                            put("filename", msg.mediaUrl.substringAfterLast("/"))
                                        }
                                    }
                                    send(Frame.Text(offlineData.toString()))
                                    db.markMessageAsRead(msg.id)
                                }
                            }
                        } catch (e: Exception) {
                            ConnectionManager.log("⚠️ Offline message delivery error for '$username': ${e.message}")
                        }

                        try {
                            for (frame in incoming) {
                                if (frame is Frame.Text) {
                                    val text = frame.readText()
                                    handleWebSocketMessage(username, text, this)
                                }
                            }
                        } catch (e: Exception) {
                            ConnectionManager.log("⚠️ WebSocket error on '$username': ${e.message}")
                        } finally {
                            ConnectionManager.disconnect(username, this)
                        }
                    }

                    // Primary endpoint used by IntraChat Android app
                    webSocket("/ws/{username}") {
                        val username = call.parameters["username"]?.trim() ?: ""
                        if (username.isEmpty()) {
                            close(CloseReason(CloseReason.Codes.NORMAL, "Username required"))
                            return@webSocket
                        }
                        handleClientSession(username)
                    }

                    // Alias endpoint
                    webSocket("/ws/chat/{username}") {
                        val username = call.parameters["username"]?.trim() ?: ""
                        if (username.isEmpty()) {
                            close(CloseReason(CloseReason.Codes.NORMAL, "Username required"))
                            return@webSocket
                        }
                        handleClientSession(username)
                    }
                }
            }

            engine?.start(wait = false)
            isRunning = true
            ConnectionManager.log("🚀 IntraServer running on port $port")
            onStarted()
        } catch (t: Throwable) {
            isRunning = false
            ConnectionManager.log("❌ Server start failed: ${t.message}")
            onError(t)
        }
    }

    private suspend fun handleWebSocketMessage(
        sender: String,
        rawMessage: String,
        currentSession: io.ktor.server.websocket.DefaultWebSocketServerSession
    ) {
        val parsed = try { JSONObject(rawMessage) } catch (e: Exception) { return }
        val type = parsed.optString("type", "text")
        parsed.put("sender", sender)
        if (!parsed.has("timestamp") || parsed.optLong("timestamp", 0L) == 0L) {
            parsed.put("timestamp", System.currentTimeMillis())
        }
        val receiver = parsed.optString("receiver", "").trim()
        val finalRaw = parsed.toString()

        when (type) {
            "typing" -> {
                // Forward typing indicator directly to receiver
                if (receiver.isNotEmpty()) {
                    ConnectionManager.log("⌨️ Typing indicator: '$sender' -> '$receiver'")
                    ConnectionManager.sendToUser(receiver, finalRaw, excludeSession = currentSession)
                }
            }

            // WebRTC Call Signaling (call_request, call_accepted, call_accept, call_rejected, ice_candidate, call_ended)
            "call_request", "call_accepted", "call_accept", "call_rejected", "call_ended", "ice_candidate", "call_busy", "webrtc_offer", "webrtc_answer" -> {
                ConnectionManager.log("📞 WebRTC Signal ($type) from '$sender' -> '$receiver'")
                if (receiver.isNotEmpty()) {
                    val delivered = ConnectionManager.sendToUser(receiver, finalRaw, excludeSession = currentSession)
                    if (!delivered && type == "call_request") {
                        val offlineRes = JSONObject().apply {
                            put("type", "call_rejected")
                            put("sender", receiver)
                            put("receiver", sender)
                            put("reason", "offline")
                        }.toString()
                        currentSession.send(Frame.Text(offlineRes))
                    }
                }
            }

            else -> {
                // ==========================================
                // 💬 TEXT & FILE CHAT MESSAGE LOGIC
                // ==========================================
                val fileUrl = if (parsed.has("url") && !parsed.isNull("url")) parsed.optString("url")
                              else if (parsed.has("file_url") && !parsed.isNull("file_url")) parsed.optString("file_url")
                              else null
                val fileName = if (parsed.has("filename") && !parsed.isNull("filename")) parsed.optString("filename") else null

                // File message ke liye clean description text set karo
                val textContent = if (parsed.has("text") && parsed.optString("text").isNotEmpty()) {
                    parsed.optString("text")
                } else if (type == "file") {
                    "Shared File: ${fileName ?: "Attachment"}"
                } else {
                    parsed.optString("content", "")
                }

                // Packet me bhi standard text aur url ensure karo
                if (!parsed.has("text")) parsed.put("text", textContent)
                if (fileUrl != null && !parsed.has("url")) parsed.put("url", fileUrl)

                val ts = parsed.optLong("timestamp", System.currentTimeMillis()).toString()

                // 💾 SQLite Database me chat message save karo
                val msgId = db.saveMessage(sender, receiver, textContent, fileUrl, ts)
                ConnectionManager.log("💬 Message ($type) '$sender' -> '$receiver': ${textContent.take(30)}")

                val messageToSend = parsed.toString()

                if (receiver == "Family Group") {
                    // Broadcast to all users
                    ConnectionManager.broadcast(messageToSend, excludeSession = currentSession)
                } else if (receiver.isNotEmpty()) {
                    // Send to receiver
                    val delivered = ConnectionManager.sendToUser(receiver, messageToSend)
                    if (delivered) {
                        db.markMessageAsRead(msgId)
                    }
                }
                // Sync back to sender's other sessions if any
                ConnectionManager.sendToUser(sender, messageToSend, excludeSession = currentSession)
            }
        }
    }

    /**
     * 🖼️ Profile Photo Processor (Pillow / ImageOps.fit ka Desi Android Equivalent)
     *
     * 1. Photo ko decode karta hai memory me (BitmapFactory).
     * 2. Center se 1:1 square crop karta hai (taaki photo pichke na / no squeeze).
     * 3. Exact 256x256 pixel me smooth scale karta hai (Bilinear filtering = true).
     * 4. High-quality PNG format me save karta hai.
     * 5. Purane phones par RAM crash na ho isliye Bitmaps ko recycle() karta hai.
     */
    private fun processAndSaveAvatar(input: InputStream, outputFile: File): Boolean {
        return try {
            // Step 1: Image ko memory me Bitmap ki tarah decode karo
            val original = BitmapFactory.decodeStream(input) ?: return false

            // Step 2: Center-crop dimensions calculate karo
            // Agar photo rectangle hai toh uske center se square kaatna hai
            val size = minOf(original.width, original.height)
            val startX = (original.width - size) / 2
            val startY = (original.height - size) / 2

            // Step 3: Exact 1:1 square crop (No distortion, no squeezing)
            val square = Bitmap.createBitmap(original, startX, startY, size, size)

            // Step 4: Exactly 256x256 pixel me high-quality resize
            val scaled = Bitmap.createScaledBitmap(square, 256, 256, true)

            // Step 5: File me PNG format me compress karke likh do
            FileOutputStream(outputFile).use { out ->
                scaled.compress(Bitmap.CompressFormat.PNG, 95, out)
            }

            // Step 6: Memory Cleanup (Android 5.0+ stability ke liye)
            if (original != square && !original.isRecycled) original.recycle()
            if (square != scaled && !square.isRecycled) square.recycle()
            if (!scaled.isRecycled) scaled.recycle()

            true
        } catch (e: Exception) {
            ConnectionManager.log("❌ Avatar processing error: ${e.message}")
            false
        }
    }

    fun stop() {
        if (!isRunning) return
        try {
            engine?.stop(1000, 2000)
            engine = null
            isRunning = false
            ConnectionManager.clearAll()
            ConnectionManager.log("🛑 IntraServer stopped")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
