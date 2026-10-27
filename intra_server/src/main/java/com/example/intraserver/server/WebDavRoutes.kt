package com.example.intraserver.server

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.header
import io.ktor.server.request.httpMethod
import io.ktor.server.request.receiveStream
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import java.io.File
import java.io.FileOutputStream
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/**
 * 🗄️ WebDavRoutes.kt - Native WebDAV (Network Drive) Engine for IntraServer (Android)
 *
 * Allows any Windows 10/11 PC ("Map Network Drive" as Z:\), macOS Finder (Cmd + K),
 * or Linux client to mount the phone's internal storage directly over Wi-Fi
 * on the standard port 8000 without requiring root or port 445 hacks.
 */
fun Route.webDavRoutes(
    driveDir: File,
    isEnabled: () -> Boolean
) {
    driveDir.mkdirs()

    // 1. Root /drive
    route("/drive") {
        handle {
            handleWebDavRequest(call, driveDir, "", isEnabled)
        }
    }

    // 2. Nested /drive/{path...}
    route("/drive/{path...}") {
        handle {
            val pathSegments = call.parameters.getAll("path") ?: emptyList()
            val relativePath = pathSegments.joinToString(File.separator)
            handleWebDavRequest(call, driveDir, relativePath, isEnabled)
        }
    }
}

private suspend fun handleWebDavRequest(
    call: ApplicationCall,
    driveDir: File,
    rawRelativePath: String,
    isEnabled: () -> Boolean
) {
    // 🛡️ Security Check: Ensure IntraDrive is toggled ON by Admin
    if (!isEnabled()) {
        val forbiddenXml = """<?xml version="1.0" encoding="utf-8"?>
<D:error xmlns:D="DAV:">
  <D:need-privileges>IntraDrive is disabled by administrator.</D:need-privileges>
</D:error>"""
        call.respondText(
            forbiddenXml,
            ContentType.parse("application/xml; charset=utf-8"),
            HttpStatusCode.Forbidden
        )
        return
    }

    val method = call.request.httpMethod.value.uppercase()
    val decodedPath = try {
        URLDecoder.decode(rawRelativePath, "UTF-8").trim().removePrefix("/").removePrefix("\\")
    } catch (_: Exception) {
        rawRelativePath.trim().removePrefix("/").removePrefix("\\")
    }

    // Prevent directory traversal attacks
    val targetFile = if (decodedPath.isEmpty()) driveDir else File(driveDir, decodedPath)
    if (!targetFile.canonicalPath.startsWith(driveDir.canonicalPath)) {
        call.respond(HttpStatusCode.Forbidden)
        return
    }

    val httpDateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("GMT")
    }

    when (method) {
        // ==========================================
        // 1. OPTIONS (Capabilities check)
        // ==========================================
        "OPTIONS" -> {
            call.response.header("DAV", "1, 2")
            call.response.header("MS-Author-Via", "DAV")
            call.response.header(
                "Allow",
                "OPTIONS, GET, HEAD, POST, PUT, DELETE, PROPFIND, PROPPATCH, MKCOL, COPY, MOVE, LOCK, UNLOCK"
            )
            call.response.header("Accept-Ranges", "bytes")
            call.respond(HttpStatusCode.OK)
        }

        // ==========================================
        // 2. PROPFIND (File & Folder Directory Index)
        // ==========================================
        "PROPFIND" -> {
            if (!targetFile.exists()) {
                call.respond(HttpStatusCode.NotFound)
                return
            }

            val depth = call.request.header("Depth") ?: "1"
            val requestUri = call.request.header("X-Original-URI")
                ?: ("/drive/" + decodedPath.replace(File.separatorChar, '/').trimStart('/'))
            val baseHref = if (targetFile.isDirectory && !requestUri.endsWith("/")) "$requestUri/" else requestUri

            val xml = generatePropfindXml(baseHref, targetFile, depth, httpDateFormat)
            call.respondText(
                xml,
                ContentType.parse("application/xml; charset=utf-8"),
                HttpStatusCode(207, "Multi-Status")
            )
        }

        // ==========================================
        // 3. PROPPATCH (Windows metadata probe)
        // ==========================================
        "PROPPATCH" -> {
            val xml = """<?xml version="1.0" encoding="utf-8"?>
<D:multistatus xmlns:D="DAV:">
  <D:response>
    <D:href>${escapeXml("/drive/" + decodedPath.replace(File.separatorChar, '/'))}</D:href>
    <D:propstat>
      <D:status>HTTP/1.1 200 OK</D:status>
    </D:propstat>
  </D:response>
</D:multistatus>"""
            call.respondText(
                xml,
                ContentType.parse("application/xml; charset=utf-8"),
                HttpStatusCode(207, "Multi-Status")
            )
        }

        // ==========================================
        // 4. GET & HEAD (File Streaming / Web Preview)
        // ==========================================
        "GET" -> {
            if (!targetFile.exists()) {
                call.respond(HttpStatusCode.NotFound)
                return
            }

            if (targetFile.isDirectory) {
                val accept = call.request.header("Accept") ?: ""
                val format = call.request.queryParameters["format"] ?: ""
                if (accept.contains("application/json") || format == "json") {
                    val array = org.json.JSONArray()
                    val children = targetFile.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })) ?: emptyList()
                    for (child in children) {
                        array.put(org.json.JSONObject().apply {
                            put("name", child.name)
                            put("isDirectory", child.isDirectory)
                            put("size", if (child.isDirectory) 0L else child.length())
                            put("lastModified", child.lastModified())
                        })
                    }
                    call.respondText(array.toString(), ContentType.Application.Json, HttpStatusCode.OK)
                } else if (accept.contains("text/html")) {
                    val html = generateWebBrowserHtml(targetFile, decodedPath)
                    call.respondText(html, ContentType.Text.Html, HttpStatusCode.OK)
                } else {
                    call.respond(HttpStatusCode.OK)
                }
            } else {
                call.respondFile(targetFile)
            }
        }

        "HEAD" -> {
            if (targetFile.exists()) {
                call.response.header("Content-Length", targetFile.length().toString())
                call.response.header("Last-Modified", httpDateFormat.format(Date(targetFile.lastModified())))
                call.respond(HttpStatusCode.OK)
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }

        // ==========================================
        // 5. PUT (Upload / Write File)
        // ==========================================
        "PUT" -> {
            targetFile.parentFile?.mkdirs()
            val isNew = !targetFile.exists()

            try {
                call.receiveStream().use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }
                ConnectionManager.log("🗄️ [IntraDrive] Written: ${targetFile.name} (${targetFile.length()} bytes)")
                if (isNew) call.respond(HttpStatusCode.Created) else call.respond(HttpStatusCode.NoContent)
            } catch (e: Exception) {
                ConnectionManager.log("❌ [IntraDrive] Write error: ${e.message}")
                call.respond(HttpStatusCode.InternalServerError)
            }
        }

        // ==========================================
        // 6. MKCOL (Create Directory)
        // ==========================================
        "MKCOL" -> {
            if (targetFile.exists()) {
                call.respond(HttpStatusCode(405, "Method Not Allowed"))
                return
            }
            if (targetFile.mkdirs()) {
                ConnectionManager.log("🗄️ [IntraDrive] Created folder: ${targetFile.name}")
                call.respond(HttpStatusCode.Created)
            } else {
                call.respond(HttpStatusCode.Conflict)
            }
        }

        // ==========================================
        // 7. DELETE (Delete file or folder)
        // ==========================================
        "DELETE" -> {
            if (!targetFile.exists()) {
                call.respond(HttpStatusCode.NotFound)
                return
            }
            if (targetFile.deleteRecursively()) {
                ConnectionManager.log("🗄️ [IntraDrive] Deleted: ${targetFile.name}")
                call.respond(HttpStatusCode.NoContent)
            } else {
                call.respond(HttpStatusCode.InternalServerError)
            }
        }

        // ==========================================
        // 8. MOVE (Rename / Move file)
        // ==========================================
        "MOVE" -> {
            if (!targetFile.exists()) {
                call.respond(HttpStatusCode.NotFound)
                return
            }

            val destHeader = call.request.header("Destination") ?: run {
                call.respond(HttpStatusCode.BadRequest)
                return
            }

            val destRel = parseDestinationPath(destHeader)
            val destFile = File(driveDir, destRel)
            destFile.parentFile?.mkdirs()

            val overwrite = (call.request.header("Overwrite") ?: "T").uppercase() == "T"
            if (destFile.exists()) {
                if (!overwrite) {
                    call.respond(HttpStatusCode(412, "Precondition Failed"))
                    return
                }
                destFile.deleteRecursively()
            }

            val success = targetFile.renameTo(destFile) || (targetFile.copyRecursively(destFile, overwrite = true) && targetFile.deleteRecursively())
            if (success) {
                ConnectionManager.log("🗄️ [IntraDrive] Moved: ${targetFile.name} -> ${destFile.name}")
                call.respond(HttpStatusCode.Created)
            } else {
                call.respond(HttpStatusCode.InternalServerError)
            }
        }

        // ==========================================
        // 9. COPY (Duplicate file)
        // ==========================================
        "COPY" -> {
            if (!targetFile.exists()) {
                call.respond(HttpStatusCode.NotFound)
                return
            }

            val destHeader = call.request.header("Destination") ?: run {
                call.respond(HttpStatusCode.BadRequest)
                return
            }

            val destRel = parseDestinationPath(destHeader)
            val destFile = File(driveDir, destRel)
            destFile.parentFile?.mkdirs()

            val success = targetFile.copyRecursively(destFile, overwrite = true)
            if (success) {
                ConnectionManager.log("🗄️ [IntraDrive] Copied: ${targetFile.name} -> ${destFile.name}")
                call.respond(HttpStatusCode.Created)
            } else {
                call.respond(HttpStatusCode.InternalServerError)
            }
        }

        // ==========================================
        // 10. LOCK & UNLOCK (MS Office / Windows Lock Handshake)
        // ==========================================
        "LOCK" -> {
            val token = "urn:uuid:" + UUID.randomUUID().toString()
            call.response.header("Lock-Token", "<$token>")
            val lockXml = """<?xml version="1.0" encoding="utf-8"?>
<D:prop xmlns:D="DAV:">
  <D:lockdiscovery>
    <D:activelock>
      <D:locktype><D:write/></D:locktype>
      <D:lockscope><D:exclusive/></D:lockscope>
      <D:depth>0</D:depth>
      <D:owner><D:href>IntraClient</D:href></D:owner>
      <D:timeout>Second-3600</D:timeout>
      <D:locktoken><D:href>$token</D:href></D:locktoken>
      <D:lockroot><D:href>${escapeXml("/drive/" + decodedPath.replace(File.separatorChar, '/'))}</D:href></D:lockroot>
    </D:activelock>
  </D:lockdiscovery>
</D:prop>"""
            call.respondText(
                lockXml,
                ContentType.parse("application/xml; charset=utf-8"),
                HttpStatusCode.OK
            )
        }

        "UNLOCK" -> {
            call.respond(HttpStatusCode.NoContent)
        }

        else -> {
            call.respond(HttpStatusCode.MethodNotAllowed)
        }
    }
}

/**
 * Extracts relative destination path from full or partial WebDAV Destination header
 */
private fun parseDestinationPath(destination: String): String {
    val clean = try {
        URLDecoder.decode(destination, "UTF-8")
    } catch (_: Exception) {
        destination
    }
    val relative = if (clean.contains("/drive/")) {
        clean.substringAfter("/drive/").substringBefore("?")
    } else if (clean.contains("/drive")) {
        clean.substringAfter("/drive").substringBefore("?")
    } else {
        clean.substringAfterLast("/")
    }
    return relative.replace('/', File.separatorChar).trimStart(File.separatorChar)
}

/**
 * Generates RFC 4918-compliant MultiStatus XML for PROPFIND directory queries
 */
private fun generatePropfindXml(
    baseHref: String,
    targetFile: File,
    depth: String,
    httpDateFormat: SimpleDateFormat
): String {
    val sb = StringBuilder()
    sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
    sb.append("<D:multistatus xmlns:D=\"DAV:\">\n")

    fun appendEntry(file: File, href: String) {
        val isDir = file.isDirectory
        val name = file.name.ifEmpty { "drive" }
        val lastMod = httpDateFormat.format(Date(file.lastModified()))
        val length = if (isDir) 0L else file.length()
        val mime = if (isDir) "httpd/unix-directory" else getMimeType(file.name)

        sb.append("  <D:response>\n")
        sb.append("    <D:href>").append(escapeXml(href)).append("</D:href>\n")
        sb.append("    <D:propstat>\n")
        sb.append("      <D:prop>\n")
        sb.append("        <D:displayname>").append(escapeXml(name)).append("</D:displayname>\n")
        if (isDir) {
            sb.append("        <D:resourcetype><D:collection/></D:resourcetype>\n")
            val usable = file.usableSpace
            val total = file.totalSpace
            val used = (total - usable).coerceAtLeast(0L)
            sb.append("        <D:quota-available-bytes>").append(usable).append("</D:quota-available-bytes>\n")
            sb.append("        <D:quota-used-bytes>").append(used).append("</D:quota-used-bytes>\n")
        } else {
            sb.append("        <D:resourcetype/>\n")
            sb.append("        <D:getcontentlength>").append(length).append("</D:getcontentlength>\n")
            sb.append("        <D:getcontenttype>").append(mime).append("</D:getcontenttype>\n")
        }
        sb.append("        <D:getlastmodified>").append(lastMod).append("</D:getlastmodified>\n")
        sb.append("      </D:prop>\n")
        sb.append("      <D:status>HTTP/1.1 200 OK</D:status>\n")
        sb.append("    </D:propstat>\n")
        sb.append("  </D:response>\n")
    }

    val normalizedBase = if (targetFile.isDirectory && !baseHref.endsWith("/")) "$baseHref/" else baseHref
    appendEntry(targetFile, normalizedBase)

    if (depth != "0" && targetFile.isDirectory) {
        val children = targetFile.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })) ?: emptyList()
        for (child in children) {
            val childHref = normalizedBase + child.name + (if (child.isDirectory) "/" else "")
            appendEntry(child, childHref)
        }
    }

    sb.append("</D:multistatus>")
    return sb.toString()
}

/**
 * Clean Web View for users who open http://<ip>:8000/drive in browser
 */
private fun generateWebBrowserHtml(folder: File, relativePath: String): String {
    val items = folder.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })) ?: emptyList()
    val sb = StringBuilder()
    sb.append("""<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <title>IntraDrive — Network Storage</title>
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <style>
    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #0F172A; color: #E2E8F0; padding: 24px; margin: 0; }
    .container { max-width: 800px; margin: auto; }
    h1 { font-size: 20px; display: flex; align-items: center; gap: 8px; color: #38BDF8; }
    .hint { background: rgba(56, 189, 248, 0.1); border: 1px solid rgba(56, 189, 248, 0.3); border-radius: 8px; padding: 12px; font-size: 13px; margin-bottom: 20px; line-height: 1.5; }
    .item { display: flex; align-items: center; justify-content: space-between; padding: 10px 14px; background: #1E293B; border-radius: 8px; margin-bottom: 6px; text-decoration: none; color: inherit; }
    .item:hover { background: #334155; }
    .name { font-weight: 500; font-size: 14px; display: flex; align-items: center; gap: 8px; }
    .meta { font-size: 12px; color: #94A3B8; }
  </style>
</head>
<body>
  <div class="container">
    <h1>IntraDrive — Network Storage</h1>
    <div class="hint">
      <b>Windows File Explorer Tip:</b> Right-click <b>This PC</b> ➔ <b>Map network drive</b> ➔ Paste: <code>${escapeXml("http://" + (if (relativePath.isEmpty()) "/drive" else "/drive/$relativePath"))}</code> to mount as <b>Z: Drive</b>
    </div>
    <div>
""")
    if (relativePath.isNotEmpty()) {
        sb.append("""      <a class="item" href="../"><span class="name">[Parent Directory] ..</span></a>""")
    }
    for (item in items) {
        val prefix = if (item.isDirectory) "[Folder]" else "[File]"
        val href = item.name + (if (item.isDirectory) "/" else "")
        val sizeStr = if (item.isDirectory) "Directory" else "${item.length() / 1024} KB"
        sb.append("""
      <a class="item" href="${escapeXml(href)}">
        <span class="name">$prefix ${escapeXml(item.name)}</span>
        <span class="meta">$sizeStr</span>
      </a>
""")
    }
    sb.append("""
    </div>
  </div>
</body>
</html>
""")
    return sb.toString()
}

private fun escapeXml(str: String): String {
    return str.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}

private fun getMimeType(filename: String): String {
    val ext = filename.substringAfterLast(".", "").lowercase()
    return when (ext) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "mp4" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "mp3" -> "audio/mpeg"
        "wav" -> "audio/wav"
        "pdf" -> "application/pdf"
        "txt" -> "text/plain"
        "zip" -> "application/zip"
        else -> "application/octet-stream"
    }
}
