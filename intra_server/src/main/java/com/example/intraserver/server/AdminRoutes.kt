package com.example.intraserver.server

import android.content.Context
import com.example.intraserver.db.ServerDatabase
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.header
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 👑 AdminRoutes.kt - Modular Admin Endpoints for IntraServer
 * FastAPI /admin parity: PySide Desktop Admin Panel & Android Admin Screen dono isse baat kar sakte hain.
 */
fun Route.adminRoutes(db: ServerDatabase, context: Context, uploadDir: File) {

    // Helper: Verify X-Admin-Key header or query parameter
    fun verifyAdminAuth(call: ApplicationCall): Boolean {
        val headerKey = call.request.header("X-Admin-Key") 
            ?: call.request.header("X-ADMIN-KEY") 
            ?: call.request.header("Admin-Key")
            ?: call.request.queryParameters["admin_key"]

        return db.verifyAdminKey(headerKey)
    }

    suspend fun ApplicationCall.respondAdminForbidden() {
        val json = JSONObject().apply {
            put("success", false)
            put("detail", "Admin access denied")
        }
        respondText(json.toString(), ContentType.Application.Json, HttpStatusCode.Forbidden)
    }

    route("/admin") {

        // 1. 👥 Get All Users + Online Status
        get("/users") {
            if (!verifyAdminAuth(call)) {
                call.respondAdminForbidden()
                return@get
            }

            val users = db.getAllUsers()
            val onlineUsers = ConnectionManager.getOnlineUsers()

            val array = JSONArray()
            for (u in users) {
                val isOnline = onlineUsers.any { it.equals(u.username, ignoreCase = true) }
                val obj = JSONObject().apply {
                    put("username", u.username)
                    put("profile_photo", u.profilePhoto ?: "")
                    put("created_at", u.createdAt)
                    put("status", u.status)
                    put("is_online", isOnline)
                }
                array.put(obj)
            }

            val res = JSONObject().apply {
                put("success", true)
                put("users", array)
            }
            call.respondText(res.toString(), ContentType.Application.Json, HttpStatusCode.OK)
        }

        // 2. ✅ Approve User
        post("/approve_user") {
            if (!verifyAdminAuth(call)) {
                call.respondAdminForbidden()
                return@post
            }

            val username = call.request.queryParameters["username"]
                ?: runCatching { JSONObject(call.receiveText()).optString("username") }.getOrNull()
                ?: ""

            if (username.isBlank()) {
                call.respondText(
                    JSONObject().apply { put("success", false); put("message", "Username is required") }.toString(),
                    ContentType.Application.Json, HttpStatusCode.BadRequest
                )
                return@post
            }

            val success = db.approveUser(username)
            if (success) {
                ConnectionManager.log("👑 Admin approved user: '$username'")
                call.respondText(
                    JSONObject().apply { put("success", true); put("message", "User $username approved") }.toString(),
                    ContentType.Application.Json, HttpStatusCode.OK
                )
            } else {
                call.respondText(
                    JSONObject().apply { put("success", false); put("message", "Failed to approve user $username") }.toString(),
                    ContentType.Application.Json, HttpStatusCode.NotFound
                )
            }
        }

        // 3. 🔑 Reset User Password
        post("/reset_password") {
            if (!verifyAdminAuth(call)) {
                call.respondAdminForbidden()
                return@post
            }

            var username = call.request.queryParameters["username"] ?: ""
            var newPass = call.request.queryParameters["new_pass"] ?: ""

            if (username.isBlank() || newPass.isBlank()) {
                val body = runCatching { JSONObject(call.receiveText()) }.getOrNull()
                if (body != null) {
                    if (username.isBlank()) username = body.optString("username", "")
                    if (newPass.isBlank()) newPass = body.optString("new_pass", "")
                }
            }

            if (username.isBlank() || newPass.isBlank()) {
                call.respondText(
                    JSONObject().apply { put("success", false); put("message", "Username and new_pass are required") }.toString(),
                    ContentType.Application.Json, HttpStatusCode.BadRequest
                )
                return@post
            }

            val success = db.resetPassword(username, newPass)
            if (success) {
                ConnectionManager.log("👑 Admin reset password for user: '$username'")
                call.respondText(
                    JSONObject().apply { put("success", true); put("message", "Password reset for $username") }.toString(),
                    ContentType.Application.Json, HttpStatusCode.OK
                )
            } else {
                call.respondText(
                    JSONObject().apply { put("success", false); put("message", "User not found") }.toString(),
                    ContentType.Application.Json, HttpStatusCode.NotFound
                )
            }
        }

        // 4. 🗑️ Delete User
        post("/delete_user") {
            if (!verifyAdminAuth(call)) {
                call.respondAdminForbidden()
                return@post
            }

            val username = call.request.queryParameters["username"]
                ?: runCatching { JSONObject(call.receiveText()).optString("username") }.getOrNull()
                ?: ""

            if (username.isBlank()) {
                call.respondText(
                    JSONObject().apply { put("success", false); put("message", "Username is required") }.toString(),
                    ContentType.Application.Json, HttpStatusCode.BadRequest
                )
                return@post
            }

            // Pehle active session disconnect karo
            ConnectionManager.kickUser(username, "Your account was deleted by Admin.")

            val success = db.deleteUser(username)
            if (success) {
                ConnectionManager.log("👑 Admin deleted user: '$username'")
                call.respondText(
                    JSONObject().apply { put("success", true); put("message", "User $username deleted successfully") }.toString(),
                    ContentType.Application.Json, HttpStatusCode.OK
                )
            } else {
                call.respondText(
                    JSONObject().apply { put("success", false); put("message", "User not found") }.toString(),
                    ContentType.Application.Json, HttpStatusCode.NotFound
                )
            }
        }

        // 5. ⚙️ Get Server Settings
        get("/settings") {
            if (!verifyAdminAuth(call)) {
                call.respondAdminForbidden()
                return@get
            }

            val res = JSONObject().apply {
                put("success", true)
                put("require_approval", db.getRequireApproval())
                put("discovery_enabled", db.getDiscoveryEnabled())
            }
            call.respondText(res.toString(), ContentType.Application.Json, HttpStatusCode.OK)
        }

        // 6. 🛡️ Toggle Approval Setting
        post("/toggle_approval") {
            if (!verifyAdminAuth(call)) {
                call.respondAdminForbidden()
                return@post
            }

            val enabledStr = call.request.queryParameters["enabled"]
                ?: runCatching { JSONObject(call.receiveText()).optString("enabled") }.getOrNull()
                ?: "false"

            val enabled = enabledStr.toBoolean()
            db.setRequireApproval(enabled)
            ConnectionManager.log("👑 Admin set Require Approval: $enabled")

            val res = JSONObject().apply {
                put("success", true)
                put("message", "Settings updated")
                put("require_approval", enabled)
            }
            call.respondText(res.toString(), ContentType.Application.Json, HttpStatusCode.OK)
        }

        // 7. 📡 Toggle Discovery Setting
        post("/toggle_discovery") {
            if (!verifyAdminAuth(call)) {
                call.respondAdminForbidden()
                return@post
            }

            val enabledStr = call.request.queryParameters["enabled"]
                ?: runCatching { JSONObject(call.receiveText()).optString("enabled") }.getOrNull()
                ?: "true"

            val enabled = enabledStr.toBoolean()
            db.setDiscoveryEnabled(enabled)
            ConnectionManager.log("👑 Admin set Server Discovery: $enabled")

            val res = JSONObject().apply {
                put("success", true)
                put("message", "Server discovery set to ${if (enabled) "ON" else "OFF (Stealth)"}")
                put("discovery_enabled", enabled)
            }
            call.respondText(res.toString(), ContentType.Application.Json, HttpStatusCode.OK)
        }

        // 8. 💬 Cleanup Old Messages
        post("/cleanup") {
            if (!verifyAdminAuth(call)) {
                call.respondAdminForbidden()
                return@post
            }

            val days = (call.request.queryParameters["days"]?.toIntOrNull()
                ?: runCatching { JSONObject(call.receiveText()).optInt("days", 30) }.getOrDefault(30))

            val deleted = db.cleanupOldMessages(days)
            ConnectionManager.log("👑 Admin cleaned up $deleted messages older than $days days")

            val res = JSONObject().apply {
                put("success", true)
                put("deleted_messages", deleted)
                put("message", "Deleted $deleted messages older than $days days")
            }
            call.respondText(res.toString(), ContentType.Application.Json, HttpStatusCode.OK)
        }

        // 9. 📁 Cleanup Uploaded Files
        post("/cleanup_files") {
            if (!verifyAdminAuth(call)) {
                call.respondAdminForbidden()
                return@post
            }

            val days = (call.request.queryParameters["days"]?.toIntOrNull()
                ?: runCatching { JSONObject(call.receiveText()).optInt("days", 30) }.getOrDefault(30))

            var deletedCount = 0
            var freedBytes = 0L
            val cutoffTime = System.currentTimeMillis() - (days.toLong() * 86400L * 1000L)

            val files = uploadDir.listFiles()
            if (files != null) {
                for (file in files) {
                    if (file.isFile && file.lastModified() < cutoffTime) {
                        val size = file.length()
                        if (file.delete()) {
                            deletedCount++
                            freedBytes += size
                        }
                    }
                }
            }

            val freedMb = String.format(java.util.Locale.US, "%.2f", freedBytes / (1024.0 * 1024.0))
            ConnectionManager.log("👑 Admin cleaned up $deletedCount files, freed ${freedMb}MB")

            val res = JSONObject().apply {
                put("success", true)
                put("deleted_count", deletedCount)
                put("freed_mb", freedMb)
                put("message", "Cleaned $deletedCount files older than $days days (Freed: ${freedMb}MB)")
            }
            call.respondText(res.toString(), ContentType.Application.Json, HttpStatusCode.OK)
        }

        // 10. 📊 Server Stats
        get("/stats") {
            if (!verifyAdminAuth(call)) {
                call.respondAdminForbidden()
                return@get
            }

            val users = db.getAllUsers()
            val onlineCount = ConnectionManager.getOnlineCount()
            val files = uploadDir.listFiles() ?: emptyArray()
            val totalBytes = files.filter { it.isFile }.sumOf { it.length() }
            val storageMb = String.format(java.util.Locale.US, "%.2f", totalBytes / (1024.0 * 1024.0))

            val res = JSONObject().apply {
                put("success", true)
                put("total_users", users.size)
                put("online_users", onlineCount)
                put("uploads_count", files.size)
                put("uploads_size_mb", storageMb)
                put("admin_key", db.getAdminKey())
                put("require_approval", db.getRequireApproval())
                put("discovery_enabled", db.getDiscoveryEnabled())
            }
            call.respondText(res.toString(), ContentType.Application.Json, HttpStatusCode.OK)
        }

        // 11. 🤖 Get AI Settings (PySide & AdminScreen parity)
        get("/ai_settings") {
            if (!verifyAdminAuth(call)) {
                call.respondAdminForbidden()
                return@get
            }

            val cfg = db.getAiConfig()
            val configObj = JSONObject().apply {
                put("ai_enabled", cfg.aiEnabled)
                put("ai_model", cfg.aiModel)
                put("ollama_url", cfg.ollamaUrl)
                put("ai_vision_model", cfg.aiVisionModel)
                put("ai_fallback", cfg.aiFallback)
            }

            val res = JSONObject().apply {
                put("success", true)
                put("config", configObj)
            }
            call.respondText(res.toString(), ContentType.Application.Json, HttpStatusCode.OK)
        }

        // 12. 🤖 Save AI Settings
        post("/ai_settings") {
            if (!verifyAdminAuth(call)) {
                call.respondAdminForbidden()
                return@post
            }

            val params = call.request.queryParameters
            val body = runCatching { JSONObject(call.receiveText()) }.getOrNull()

            val enabled = params["enabled"]?.toBoolean() ?: body?.optBoolean("enabled", false) ?: false
            val model = params["model"] ?: body?.optString("model", "gemma2:2b") ?: "gemma2:2b"
            val url = params["url"] ?: body?.optString("url", "http://192.168.1.35:11434") ?: "http://192.168.1.35:11434"
            val vision = params["vision"] ?: body?.optString("vision", "gemma:27b-cloud") ?: "gemma:27b-cloud"
            val fallback = params["fallback"] ?: body?.optString("fallback", "gemma3:270m") ?: "gemma3:270m"

            db.setAiConfig(enabled, model, url, vision, fallback)
            ConnectionManager.log("🤖 Admin updated AI settings: Enabled=$enabled, Model=$model, URL=$url")

            val res = JSONObject().apply {
                put("success", true)
                put("message", "AI configuration updated successfully")
            }
            call.respondText(res.toString(), ContentType.Application.Json, HttpStatusCode.OK)
        }
    }
}
