package com.example.intraserver.lumir

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

enum class ToolIntent {
    PASSPORT,
    COMPRESS,
    TOPDF,
    MERGE_PDF,
    SERVER_STATUS,
    HELP,
    CHAT // Regular AI Conversation
}

/**
 * 🧠 LumirToolRouter.kt - Autonomous Multi-Tier Intent Router (Corpus Callosum)
 *
 * Tier 1: 0ms Local Regex & Typo-tolerant Stem Matchers.
 * Tier 2: Qwen Micro-Classifier (15 tokens via llama.cpp/Ollama) for subtle, nuanced phrasing.
 * Tier 3: Seamless fallback to regular AI Chat.
 */
object LumirToolRouter {

    // Tier 1 Regex Patterns (0ms)
    private val STATUS_REGEX = Pattern.compile(
        """\b(status|server|battery|charging|ram|storage|space|uptime|health|system|phone)\b""",
        Pattern.CASE_INSENSITIVE
    )

    private val COMPRESS_REGEX = Pattern.compile(
        """\b(compress|size|chota|chhoti|kam kar|ghata|halka|halke|\d+\s*kb|\d+\s*mb|under \d+)\b""",
        Pattern.CASE_INSENSITIVE
    )

    private val PASSPORT_REGEX = Pattern.compile(
        """\b(passport|6 photo|9 photo|chhe photo|che photo|six photo|nine photo|grid|photo sheet|a6 sheet|a4 sheet)\b""",
        Pattern.CASE_INSENSITIVE
    )

    private val TOPDF_REGEX = Pattern.compile(
        """\b(to pdf|topdf|convert.*pdf|photos?.*pdf|images?.*pdf|pdf bana|pdf nikal|pdf create)\b""",
        Pattern.CASE_INSENSITIVE
    )

    private val MERGE_REGEX = Pattern.compile(
        """\b(merge|combine|jod|jodo|dono pdf|saari pdf|ek sath jod|combine pdf)\b""",
        Pattern.CASE_INSENSITIVE
    )

    /**
     * Resolves user intent autonomously without requiring slash commands:
     */
    suspend fun resolveIntent(
        rawText: String,
        hasImageInContext: Boolean,
        hasPdfInContext: Boolean,
        aiBaseUrl: String? = null
    ): ToolIntent {
        val text = rawText.lowercase().trim()

        // 1. Help check
        if (text == "/help" || text == "help" || text == "kya kar sakte ho" || text == "features" || text == "tools") {
            return ToolIntent.HELP
        }

        // 2. Status / Server Diagnostics
        if (text == "/status" || text == "/server" || (STATUS_REGEX.matcher(text).find() && (
            text.contains("server") || text.contains("battery") || text.contains("ram") ||
            text.contains("storage") || text.contains("status") || text.contains("health") ||
            text.contains("kaisa") || text.contains("check") || text.contains("kitni") || text.contains("kitna")
        ))) {
            return ToolIntent.SERVER_STATUS
        }

        // 3. Image in Context: Passport, Compress, Convert to PDF
        if (hasImageInContext) {
            if (PASSPORT_REGEX.matcher(text).find() || text.contains("passport")) {
                return ToolIntent.PASSPORT
            }
            if (COMPRESS_REGEX.matcher(text).find() || text.contains("compress")) {
                return ToolIntent.COMPRESS
            }
            if (TOPDF_REGEX.matcher(text).find() || text.contains("topdf") || text.contains("to pdf")) {
                return ToolIntent.TOPDF
            }
        }

        // 4. PDF in Context: Merge PDFs
        if (hasPdfInContext && (MERGE_REGEX.matcher(text).find() || text.contains("merge"))) {
            return ToolIntent.MERGE_PDF
        }

        // 5. Explicit keywords even if no file in immediate context
        if (text.startsWith("/passport") || text == "🛂 passport a6 (6 photos)" || text == "🛂 passport a6 (9 photos)") return ToolIntent.PASSPORT
        if (text.startsWith("/compress") || text == "🗜️ compress image") return ToolIntent.COMPRESS
        if (text == "###topdf###" || text == "📄 convert to pdf" || text.startsWith("/topdf")) return ToolIntent.TOPDF
        if (text.startsWith("/merge") || text == "🔗 merge pdfs") return ToolIntent.MERGE_PDF

        // 6. Tier 2: Qwen Corpus Callosum Classifier (if LLM is configured and user wrote a sentence)
        if (!aiBaseUrl.isNullOrBlank() && (hasImageInContext || hasPdfInContext) && text.length in 4..80) {
            val qwenResult = queryQwenClassifier(text, aiBaseUrl, hasImageInContext, hasPdfInContext)
            if (qwenResult != ToolIntent.CHAT) {
                return qwenResult
            }
        }

        return ToolIntent.CHAT
    }

    private suspend fun queryQwenClassifier(
        prompt: String,
        baseUrl: String,
        hasImage: Boolean,
        hasPdf: Boolean
    ): ToolIntent = withContext(Dispatchers.IO) {
        val cleanUrl = baseUrl.trim().removeSuffix("/")
        val endpoint = if (cleanUrl.endsWith("/v1")) "$cleanUrl/chat/completions" else "$cleanUrl/v1/chat/completions"

        val systemPrompt = "You are a fast intent classifier for an assistant. Options: 'passport', 'compress', 'topdf', 'merge', 'status', 'chat'. Reply strictly in JSON: {\"action\": \"...\"}"

        val body = JSONObject().apply {
            put("model", "qwen")
            put("temperature", 0.0)
            put("max_tokens", 15)
            val msgs = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            }
            put("messages", msgs)
        }

        try {
            val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                connectTimeout = 1200
                readTimeout = 1500
                doOutput = true
            }
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body.toString()) }
            if (conn.responseCode == 200) {
                val resp = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
                val json = JSONObject(resp)
                val choices = json.optJSONArray("choices")
                val content = choices?.optJSONObject(0)?.optJSONObject("message")?.optString("content") ?: ""
                val clean = content.lowercase()
                return@withContext when {
                    clean.contains("passport") && hasImage -> ToolIntent.PASSPORT
                    clean.contains("compress") && hasImage -> ToolIntent.COMPRESS
                    clean.contains("topdf") && hasImage -> ToolIntent.TOPDF
                    clean.contains("merge") && hasPdf -> ToolIntent.MERGE_PDF
                    clean.contains("status") -> ToolIntent.SERVER_STATUS
                    else -> ToolIntent.CHAT
                }
            }
        } catch (_: Exception) {}

        ToolIntent.CHAT
    }
}
