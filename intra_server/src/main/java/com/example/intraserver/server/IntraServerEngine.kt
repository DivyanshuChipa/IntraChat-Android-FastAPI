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
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class IntraServerEngine(private val context: Context, private val port: Int = 8000) {

    private var engine: CIOApplicationEngine? = null
    private val db = ServerDatabase.getInstance(context)
    private val uploadsDir = File(context.filesDir, "uploads").apply { mkdirs() }

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

                        for (u in users) {
                            val userObj = JSONObject().apply {
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

                    // 6. File Upload
                    post("/upload") {
                        val multipart = call.receiveMultipart()
                        var savedFileName: String? = null

                        multipart.forEachPart { part ->
                            if (part is PartData.FileItem) {
                                val originalName = part.originalFileName ?: "file_${System.currentTimeMillis()}"
                                val ext = originalName.substringAfterLast(".", "")
                                val uniqueName = "${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.${if (ext.isNotEmpty()) ext else "bin"}"
                                val destFile = File(uploadsDir, uniqueName)

                                part.streamProvider().use { input ->
                                    FileOutputStream(destFile).use { output ->
                                        input.copyTo(output)
                                    }
                                }
                                savedFileName = uniqueName
                                ConnectionManager.log("📁 File uploaded: $uniqueName (${destFile.length()} bytes)")
                            }
                            part.dispose()
                        }

                        if (savedFileName != null) {
                            val res = JSONObject().apply {
                                put("success", true)
                                put("file_url", "/uploads/$savedFileName")
                                put("filename", savedFileName)
                            }
                            call.respondText(res.toString(), ContentType.Application.Json, HttpStatusCode.OK)
                        } else {
                            val res = JSONObject().apply {
                                put("success", false)
                                put("message", "No file uploaded")
                            }
                            call.respondText(res.toString(), ContentType.Application.Json, HttpStatusCode.BadRequest)
                        }
                    }

                    // 7. Profile Photo Upload
                    post("/profile/upload_photo") {
                        val multipart = call.receiveMultipart()
                        var savedFileName: String? = null
                        var targetUsername: String? = null

                        multipart.forEachPart { part ->
                            when (part) {
                                is PartData.FormItem -> {
                                    if (part.name == "username") {
                                        targetUsername = part.value.trim()
                                    }
                                }
                                is PartData.FileItem -> {
                                    val originalName = part.originalFileName ?: "avatar.jpg"
                                    val ext = originalName.substringAfterLast(".", "jpg")
                                    val uniqueName = "avatar_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.$ext"
                                    val destFile = File(uploadsDir, uniqueName)

                                    part.streamProvider().use { input ->
                                        FileOutputStream(destFile).use { output ->
                                            input.copyTo(output)
                                        }
                                    }
                                    savedFileName = uniqueName
                                }
                                else -> {}
                            }
                            part.dispose()
                        }

                        if (savedFileName != null) {
                            val photoUrl = "/uploads/$savedFileName"
                            if (!targetUsername.isNullOrEmpty()) {
                                db.updateProfilePhoto(targetUsername!!, photoUrl)
                                ConnectionManager.log("🖼️ Updated avatar for '$targetUsername'")
                            }

                            val res = JSONObject().apply {
                                put("success", true)
                                put("photo_url", photoUrl)
                            }
                            call.respondText(res.toString(), ContentType.Application.Json, HttpStatusCode.OK)
                        } else {
                            val res = JSONObject().apply {
                                put("success", false)
                                put("message", "Avatar upload failed")
                            }
                            call.respondText(res.toString(), ContentType.Application.Json, HttpStatusCode.BadRequest)
                        }
                    }

                    // 8. Static file serving (/uploads/{filename})
                    get("/uploads/{filename}") {
                        val fileName = call.parameters["filename"] ?: ""
                        val file = File(uploadsDir, fileName)
                        if (file.exists() && file.isFile) {
                            call.respondFile(file)
                        } else {
                            call.respondText("File not found", status = HttpStatusCode.NotFound)
                        }
                    }

                    // 9. Root Welcome Page
                    get("/") {
                        val html = """
                            <!DOCTYPE html>
                            <html>
                            <head>
                                <meta name="viewport" content="width=device-width, initial-scale=1">
                                <title>IntraServer</title>
                                <style>
                                    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #0f172a; color: #f8fafc; display: flex; flex-direction: column; align-items: center; justify-content: center; height: 100vh; margin: 0; }
                                    .card { background: #1e293b; padding: 2rem; border-radius: 1rem; box-shadow: 0 10px 25px -5px rgba(0, 0, 0, 0.5); text-align: center; max-width: 400px; }
                                    h1 { color: #818cf8; margin-bottom: 0.5rem; }
                                    .badge { background: #10b981; color: white; padding: 4px 12px; border-radius: 9999px; font-size: 0.875rem; font-weight: bold; display: inline-block; margin-bottom: 1rem; }
                                    p { color: #94a3b8; line-height: 1.5; }
                                </style>
                            </head>
                            <body>
                                <div class="card">
                                    <h1>⚡ IntraServer</h1>
                                    <div class="badge">ONLINE (Android)</div>
                                    <p>Intra Local LAN Chat & Media Server is running actively on this device.</p>
                                    <p>Connect using the Intra Android app by setting Server IP to this device's address.</p>
                                </div>
                            </body>
                            </html>
                        """.trimIndent()
                        call.respondText(html, ContentType.Text.Html)
                    }

                    // 10. WebSockets: Real-time Chat, Presence, and WebRTC Calling
                    webSocket("/ws/chat/{username}") {
                        val username = call.parameters["username"]?.trim() ?: ""
                        if (username.isEmpty()) {
                            close(CloseReason(CloseReason.Codes.NORMAL, "Username required"))
                            return@webSocket
                        }

                        ConnectionManager.connect(username, this)

                        try {
                            for (frame in incoming) {
                                if (frame is Frame.Text) {
                                    val text = frame.readText()
                                    handleWebSocketMessage(username, text)
                                }
                            }
                        } catch (e: Exception) {
                            ConnectionManager.log("⚠️ WebSocket error on '$username': ${e.message}")
                        } finally {
                            ConnectionManager.disconnect(username)
                        }
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

    private suspend fun handleWebSocketMessage(sender: String, rawMessage: String) {
        val json = try { JSONObject(rawMessage) } catch (e: Exception) { return }
        val type = json.optString("type", "message")
        val receiver = json.optString("receiver", "").trim()

        when (type) {
            "message" -> {
                val content = json.optString("content", "")
                val mediaUrl = if (json.has("media_url") && !json.isNull("media_url")) json.optString("media_url") else null
                val timestamp = json.optString("timestamp", "")

                // Save to database
                db.saveMessage(sender, receiver, content, mediaUrl, timestamp)
                ConnectionManager.log("💬 Message from '$sender' -> '$receiver': ${content.take(30)}")

                // Forward to receiver if online
                if (receiver.isNotEmpty()) {
                    ConnectionManager.sendToUser(receiver, rawMessage)
                }
            }

            "typing" -> {
                // Forward typing indicator directly to receiver
                if (receiver.isNotEmpty()) {
                    ConnectionManager.sendToUser(receiver, rawMessage)
                }
            }

            // WebRTC Call Signaling (call_request, call_accepted, call_rejected, ice_candidate, call_ended)
            "call_request", "call_accepted", "call_rejected", "call_ended", "ice_candidate", "call_busy" -> {
                ConnectionManager.log("📞 WebRTC Signal ($type) from '$sender' -> '$receiver'")
                if (receiver.isNotEmpty()) {
                    val delivered = ConnectionManager.sendToUser(receiver, rawMessage)
                    if (!delivered && type == "call_request") {
                        // Notify sender that receiver is offline
                        val offlineRes = JSONObject().apply {
                            put("type", "call_rejected")
                            put("sender", receiver)
                            put("receiver", sender)
                            put("reason", "offline")
                        }.toString()
                        ConnectionManager.sendToUser(sender, offlineRes)
                    }
                }
            }

            else -> {
                // Forward any other message types to receiver
                if (receiver.isNotEmpty()) {
                    ConnectionManager.sendToUser(receiver, rawMessage)
                }
            }
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
