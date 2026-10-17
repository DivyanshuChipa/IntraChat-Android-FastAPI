package com.example.intraserver.lumir

import com.example.intraserver.db.ServerDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * 💡 LumirLiteEngine.kt - Ultra-Lightweight AI & Utilities Engine for Android
 * Dispatches image/document tools and connects to LAN Ollama without slowing down the phone.
 */
object LumirLiteEngine {

    data class LumirResponse(
        val text: String,
        val mediaUrl: String? = null,
        val fileName: String? = null,
        val options: List<String>? = null
    )

    // Memory: Sender -> List of recent uploaded file paths/URLs
    private val userContext = ConcurrentHashMap<String, MutableList<String>>()

    suspend fun process(
        rawText: String,
        fileUrl: String?,
        sender: String,
        uploadsDir: File,
        db: ServerDatabase
    ): LumirResponse {
        val originalText = rawText.trim()
        val text = originalText.lowercase()

        // 1. Remember incoming file in user's session context
        if (!fileUrl.isNullOrBlank()) {
            val list = userContext.getOrPut(sender) { mutableListOf() }
            list.add(fileUrl)
            if (list.size > 10) list.removeAt(0)

            // Agar sirf file bheji hai aur user ne specific command nahi likha, toh options prompt karo
            val isAutoFileText = originalText.isBlank() || originalText.startsWith("Shared File:", ignoreCase = true)
            if (isAutoFileText) {
                val ext = fileUrl.substringAfterLast(".", "").lowercase()
                return when (ext) {
                    "pdf" -> {
                        LumirResponse(
                            text = "📄 PDF received! Choose a document tool or send more PDFs to merge:",
                            options = listOf("🔗 Merge PDFs")
                        )
                    }
                    "mp4", "mkv", "avi", "mov", "3gp" -> {
                        LumirResponse(
                            text = "🎬 Video received! Note: Heavy video tools (transcoding, audio extraction) require the PC server.\n\nType /help to see all phone tools."
                        )
                    }
                    else -> {
                        // Image by default
                        LumirResponse(
                            text = "📸 Photo received! Choose an image tool below:",
                            options = listOf(
                                "🛂 Passport A6 (6 Photos)",
                                "🛂 Passport A6 (9 Photos)",
                                "🗜️ Compress Image"
                            )
                        )
                    }
                }
            }
        }

        // 2. Identify the target file for operations (from parameter or recent context)
        val activeFileUrl = fileUrl ?: userContext[sender]?.lastOrNull()
        val activeFile = if (!activeFileUrl.isNullOrBlank()) {
            val fname = activeFileUrl.substringAfterLast("/")
            File(uploadsDir, fname)
        } else null

        // 3. Command: PASSPORT PHOTO
        if (text.contains("passport") || text.startsWith("/passport")) {
            if (activeFile == null || !activeFile.exists()) {
                return LumirResponse(text = "⚠️ Pehle ek photo bhejo, phir passport command use karo! (e.g. 6 photo sheet)")
            }

            val gridSize = if (text.contains("9")) 9 else 6

            // Optional: parse name or date (e.g., name: Rahul date: 15/10/2026)
            var name: String? = null
            var date: String? = null
            val nameMatch = Regex("""name:\s*([^,\n]+)""", RegexOption.IGNORE_CASE).find(originalText)
            if (nameMatch != null) name = nameMatch.groupValues[1].trim()

            val dateMatch = Regex("""date:\s*([^,\n]+)""", RegexOption.IGNORE_CASE).find(originalText)
            if (dateMatch != null) date = dateMatch.groupValues[1].trim()

            val res = ImageUtils.generatePassportLayout(
                inputFile = activeFile,
                uploadsDir = uploadsDir,
                gridSize = gridSize,
                nameText = name,
                dateText = date,
                pageSize = if (text.contains("a4")) "A4" else "A6"
            )

            return if (res.success) {
                LumirResponse(text = res.message, mediaUrl = res.fileUrl, fileName = res.fileName)
            } else {
                LumirResponse(text = "❌ " + res.message)
            }
        }

        // 4. Command: IMAGE COMPRESS
        if (text.contains("compress") || text.startsWith("/compress")) {
            if (activeFile == null || !activeFile.exists()) {
                return LumirResponse(text = "⚠️ Pehle ek image bhejo jisko compress karna hai!")
            }

            val sizeMatch = Regex("""(\d+)\s*(kb|mb)?""").find(text)
            var targetKb = sizeMatch?.groupValues?.get(1)?.toIntOrNull() ?: 50
            if (text.contains("mb")) targetKb *= 1024

            val res = ImageUtils.compressImageToTarget(activeFile, uploadsDir, targetKb)
            return if (res.success) {
                LumirResponse(text = res.message, mediaUrl = res.fileUrl, fileName = res.fileName)
            } else {
                LumirResponse(text = "❌ " + res.message)
            }
        }

        // 5. Command: MERGE PDF
        if (text.contains("merge") || text.startsWith("/merge") || text == "🔗 merge pdfs") {
            val userFiles = userContext[sender]?.map { File(uploadsDir, it.substringAfterLast("/")) }
                ?.filter { it.exists() && it.extension.equals("pdf", ignoreCase = true) }
                ?: emptyList()

            if (userFiles.size < 2) {
                return LumirResponse(text = "⚠️ Merge karne ke liye kam se kam 2 PDF files bhejo!")
            }

            val res = PdfUtils.mergePdfs(userFiles, uploadsDir)
            return if (res.success) {
                LumirResponse(text = res.message, mediaUrl = res.fileUrl, fileName = res.fileName)
            } else {
                LumirResponse(text = "❌ " + res.message)
            }
        }

        // 6. Command: HEAVY TASKS NOTICE (YouTube, Video, OCR)
        if (text.startsWith("/yt") || text.contains("youtube.com") || text.contains("youtu.be") || text.contains("transcode") || text.contains("extractaudio")) {
            return LumirResponse(
                text = "⚠️ **Heavy Feature Notice:**\n\nYouTube Downloader aur Heavy Video tools phone ki battery aur performance bachane ke liye sirf **PC Server (FastAPI)** par available hain.\n\n💡 Phone server par aap **Passport Photo**, **Image Compression**, aur **PDF Merge** use kar sakte hain!"
            )
        }

        // 7. Command: HELP
        if (text == "/help" || text == "help") {
            return LumirResponse(
                text = """
                    🌟 **Lumir-Lite Utilities on Phone Server**:
                    
                    📸 **Passport Photos**:
                    • Bhejo koi bhi photo aur bolo: `Passport 6` ya `Passport 9`
                    • Name aur date add karein: `Passport 6 name: Aman date: 2026`
                    
                    🗜️ **Image Compression**:
                    • Bhejo image aur bolo: `Compress 50kb` ya `Compress 100kb`
                    
                    📄 **PDF Tools**:
                    • 2 ya zyada PDFs bhejo aur bolo: `Merge PDFs`
                    
                    🤖 **AI Chat**:
                    • Admin panel me Ollama LAN IP daal kar seedha kisi bhi LLM se baat karein!
                """.trimIndent()
            )
        }

        // 8. General AI Chat via LAN Ollama
        val aiConfig = db.getAiConfig()
        if (aiConfig.aiEnabled && aiConfig.ollamaUrl.isNotBlank()) {
            val reply = OllamaClient.askOllama(
                ollamaBaseUrl = aiConfig.ollamaUrl,
                model = aiConfig.aiModel,
                prompt = originalText,
                sender = sender
            )
            return LumirResponse(text = reply)
        }

        // 9. Default friendly greeting
        return LumirResponse(
            text = "👋 Hello $sender! Main Lumir-Lite hu. Mujhe koi photo bhejo Passport sheet ya Compression ke liye, ya /help type karo. AI baatein karne ke liye server Admin se Ollama enable karwayein!"
        )
    }
}
